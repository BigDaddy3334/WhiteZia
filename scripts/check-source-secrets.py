#!/usr/bin/env python3
"""Scan only this repository's indexed/current source and dirty Git diffs.

The pinned Gitleaks CLI runs locally. Its output is captured; console output
contains rule/file/line metadata only, never matched values or source snippets.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

SENSITIVE_BINARY_SUFFIXES = {'.jks', '.keystore', '.p12', '.pfx'}

# Public unit-test server keys only: exact path AND exact bytes, never a production key.
PUBLIC_TEST_TLS_KEYSTORES = {
    'app/src/test/resources/shop/whitezia/client/runtime/tls-fixture/localhost-server.p12':
        'fea2439eae71fd0eabb486c82750744a435a8db792b888f9a111bcafdfbb7edc',
    'app/src/test/resources/shop/whitezia/client/runtime/tls-fixture/health-marker-server.p12':
        'e1b7451cab30528e58fa6ae5a98114375d6643b54cc0e2bc32748e3a826e9719',
}


def git(root, *args):
    result = subprocess.run(['git', '-C', str(root), *args], capture_output=True, check=True)
    return result.stdout


def scan(root, executable):
    root = root.resolve()
    actual = Path(os.fsdecode(git(root, 'rev-parse', '--show-toplevel').strip())).resolve()
    if actual != root:
        raise ValueError('Scanner must run at the intended repository root')
    findings = []
    with tempfile.TemporaryDirectory(prefix='whitezia-source-scan-') as temporary:
        temporary = Path(temporary)
        source = temporary / 'source'
        source.mkdir()
        names = git(root, 'ls-files', '-z', '--cached', '--others', '--exclude-standard').split(b'\0')
        for raw in sorted(set(names)):
            if not raw:
                continue
            relative = Path(os.fsdecode(raw))
            target = root / relative
            # Never follow a link to private workspace files or descend into a
            # separate submodule repository. Git handles changed link text below.
            if target.is_symlink() or not target.is_file():
                continue
            if not target.resolve().is_relative_to(root):
                raise ValueError('Indexed path escapes the intended repository')
            if target.suffix.lower() in SENSITIVE_BINARY_SUFFIXES:
                fixture_sha256 = PUBLIC_TEST_TLS_KEYSTORES.get(relative.as_posix())
                if fixture_sha256 and hashlib.sha256(target.read_bytes()).hexdigest() == fixture_sha256:
                    continue
                findings.append({'File': str(relative), 'StartLine': 1, 'RuleID': 'private-signing-material'})
                continue
            data = target.read_bytes()
            if data.startswith((b'\xff\xfe\x00\x00', b'\x00\x00\xfe\xff')):
                data = data.decode('utf-32').encode('utf-8')
            elif data.startswith((b'\xff\xfe', b'\xfe\xff')):
                data = data.decode('utf-16').encode('utf-8')
            if b'\0' in data[:8192]:
                continue  # Native libraries/images/build artifacts are not source.
            destination = source / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
        for index, arguments in enumerate([
            ['dir', str(source)],
            ['git', '--pre-commit', str(root)],
            ['git', '--pre-commit', '--staged', str(root)],
        ]):
            report = temporary / f'report-{index}.json'
            result = subprocess.run([str(executable), *arguments, '--config', str(root / '.gitleaks.toml'),
                '--redact=100', '--no-banner', '--no-color', '--ignore-gitleaks-allow',
                '--log-level=error', '--report-format=json', '--report-path', str(report), '--timeout=120'],
                capture_output=True, timeout=150)
            if result.returncode not in (0, 1):
                raise RuntimeError(f'Gitleaks scan failed (exit {result.returncode}); output suppressed to prevent leaks')
            issues = json.loads(report.read_text(encoding='utf-8')) if report.exists() else []
            if result.returncode == 1 and not issues:
                raise RuntimeError('Gitleaks failed without a usable redacted report')
            for issue in issues:
                # Whitelist report fields before anything reaches logs.
                name = issue.get('File', 'unknown').replace('\\', '/')
                for prefix in (str(source).replace('\\', '/') + '/', str(root).replace('\\', '/') + '/'):
                    if name.startswith(prefix):
                        name = name[len(prefix):]
                findings.append({'File': name, 'StartLine': issue.get('StartLine', 0), 'RuleID': issue.get('RuleID', 'unknown')})
    unique = {(item['File'], item['StartLine'], item['RuleID']) for item in findings}
    for name, line, rule in sorted(unique):
        print(json.dumps({'file': name, 'line': line, 'rule': rule, 'secret': '[REDACTED]'}))
    print(json.dumps({'repository': root.name, 'source_and_dirty_diff_scan': 'failed' if unique else 'passed', 'findings': len(unique)}))
    return bool(unique)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gitleaks', default=shutil.which('gitleaks'))
    args = parser.parse_args()
    if not args.gitleaks:
        parser.error('Install the checksum-pinned Gitleaks CLI used by checks.yml')
    raise SystemExit(int(scan(Path(__file__).resolve().parents[1], args.gitleaks)))
