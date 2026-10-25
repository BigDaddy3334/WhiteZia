package shop.whitezia.client.vpn;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/** Runs as shell so its traffic enters the VPN rather than the excluded app UID. */
public final class VirtualDnsShellProbe {
    public static void main(String[] args) throws Exception {
        // Allocate the fixture in one process: spawning 17 shell processes can
        // outlast the intentionally unreachable upstream's startup health budget.
        if (args.length > 1 && args[1].equals("seed")) {
            for (int i = 0; i < 16; i++) resolve("seed-" + i + "." + args[0]);
        }
        System.out.println(resolve(args[0]));
    }

    private static String resolve(String host) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream query = new DataOutputStream(bytes);
        query.writeShort(5173);
        query.writeShort(256);
        query.writeShort(1);
        for (int i = 0; i < 3; i++) query.writeShort(0);
        for (String label : host.split("\\.")) {
            byte[] data = label.getBytes(StandardCharsets.US_ASCII);
            query.writeByte(data.length);
            query.write(data);
        }
        query.writeByte(0);
        query.writeShort(1);
        query.writeShort(1);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(2000);
            byte[] data = bytes.toByteArray();
            socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("172.19.0.2"), 53));
            DatagramPacket response = new DatagramPacket(new byte[4096], 4096);
            socket.receive(response);
            data = java.util.Arrays.copyOf(response.getData(), response.getLength());
            if (u16(data, 0) != 5173 || (data[3] & 15) != 0) throw new IllegalStateException("DNS error");
            int offset = 12;
            for (int i = 0; i < u16(data, 4); i++) offset = skipName(data, offset) + 4;
            for (int i = 0; i < u16(data, 6); i++) {
                offset = skipName(data, offset);
                int type = u16(data, offset), length = u16(data, offset + 8);
                offset += 10;
                if (type == 1 && length == 4) {
                    return InetAddress.getByAddress(java.util.Arrays.copyOfRange(data, offset, offset + 4)).getHostAddress();
                }
                offset += length;
            }
            throw new IllegalStateException("No IPv4 DNS answer");
        }
    }

    private static int u16(byte[] data, int offset) {
        return ((data[offset] & 255) << 8) | (data[offset + 1] & 255);
    }

    private static int skipName(byte[] data, int offset) {
        while ((data[offset] & 255) != 0) {
            int length = data[offset] & 255;
            if ((length & 192) == 192) return offset + 2;
            offset += length + 1;
        }
        return offset + 1;
    }
}
