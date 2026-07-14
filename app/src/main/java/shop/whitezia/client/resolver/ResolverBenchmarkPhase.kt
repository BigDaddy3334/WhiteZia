package shop.whitezia.client.resolver

internal enum class ResolverBenchmarkPhase {
    Idle,
    PostCheckYandex,
    TestingYandex,
    ApplyingYandexWinner,
    ApplyingLocalWinner,
    Done,
}
