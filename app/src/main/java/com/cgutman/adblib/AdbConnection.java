package com.cgutman.adblib;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;

public class AdbConnection implements Closeable {
    private AdbChannel channel;
    private AdbCrypto crypto;
    private volatile boolean connected = false;
    private int maxData = 16384;
    private int lastLocalId = 0;
    private ConcurrentHashMap<Integer, AdbStream> streams = new ConcurrentHashMap<>();
    private volatile boolean closed = false;
    private final Object connectLock = new Object();
    private boolean sentSignature = false;

    public AdbConnection(AdbChannel channel, AdbCrypto crypto) {
        this.channel = channel;
        this.crypto = crypto;
    }

    public static AdbConnection create(AdbChannel channel, AdbCrypto crypto) {
        return new AdbConnection(channel, crypto);
    }

    public void connect() throws IOException {
        closed = false;
        connected = false;
        sentSignature = false;

        // Envia pacote inicial de conexão (CONNECT)
        writePacket(AdbProtocol.CMD_CNXN, AdbProtocol.CONNECT_VERSION, maxData, "host::\0".getBytes());

        // Inicia a Thread de escuta do dispositivo remoto
        Thread t = new Thread(() -> {
            try {
                while (!closed) {
                    AdbPacket p = readPacket();
                    if (p == null) break;
                    handlePacket(p);
                }
            } catch (Exception e) {
                notifyConnectResult(false);
            }
        });
        t.setName("AdbReceiverThread");
        t.start();

        // Aguarda a autorização RSA no outro celular (handshake)
        synchronized (connectLock) {
            try {
                if (!connected) connectLock.wait(20000); 
            } catch (InterruptedException e) {
                throw new IOException("Conexão interrompida");
            }
        }

        if (!connected) throw new IOException("Falha no Handshake ADB. Verifique o pop-up no outro celular.");
    }

    private void notifyConnectResult(boolean success) {
        synchronized (connectLock) {
            this.connected = success;
            connectLock.notifyAll();
        }
    }

    private void handlePacket(AdbPacket p) throws IOException {
        switch (p.command) {
            case AdbProtocol.CMD_CNXN:
                this.maxData = p.arg1;
                notifyConnectResult(true);
                break;

            case AdbProtocol.CMD_AUTH:
                if (p.arg0 == AdbProtocol.AUTH_TOKEN) {
                    if (sentSignature) {
                        // Se já tentou assinar e falhou, envia a chave pública para disparar o pop-up
                        writePacket(AdbProtocol.CMD_AUTH, AdbProtocol.AUTH_RSAPUBLICKEY, 0, crypto.getAdbPublicKeyPayload());
                    } else {
                        try {
                            byte[] sig = crypto.sign(p.payload);
                            writePacket(AdbProtocol.CMD_AUTH, AdbProtocol.AUTH_SIGNATURE, 0, sig);
                            sentSignature = true;
                        } catch (Exception e) {
                            writePacket(AdbProtocol.CMD_AUTH, AdbProtocol.AUTH_RSAPUBLICKEY, 0, crypto.getAdbPublicKeyPayload());
                        }
                    }
                }
                break;

            case AdbProtocol.CMD_OKAY:
            case AdbProtocol.CMD_WRTE:
            case AdbProtocol.CMD_CLSE:
                AdbStream s = streams.get(p.arg1);
                if (s != null) {
                    if (p.command == AdbProtocol.CMD_OKAY) {
                        s.updateRemoteId(p.arg0);
                    } else if (p.command == AdbProtocol.CMD_WRTE) {
                        s.addPayload(p.payload);
                        s.sendReady();
                    } else if (p.command == AdbProtocol.CMD_CLSE) {
                        s.notifyClose();
                        streams.remove(p.arg1);
                    }
                }
                break;
        }
    }

    public AdbStream open(String dest) throws IOException {
        int id = ++lastLocalId;
        AdbStream s = new AdbStream(this, id);
        streams.put(id, s);
        writePacket(AdbProtocol.CMD_OPEN, id, 0, (dest + "\0").getBytes());
        try {
            s.waitReady();
        } catch (InterruptedException e) {
            throw new IOException("Erro ao abrir canal shell");
        }
        return s;
    }

    public void sendWrite(int local, int remote, byte[] data) throws IOException {
        writePacket(AdbProtocol.CMD_WRTE, local, remote, data);
    }

    public void sendReady(int local, int remote) throws IOException {
        writePacket(AdbProtocol.CMD_OKAY, local, remote, null);
    }

    public void sendClose(int local, int remote) throws IOException {
        writePacket(AdbProtocol.CMD_CLSE, local, remote, null);
    }

    private synchronized void writePacket(int cmd, int a0, int a1, byte[] p) throws IOException {
        int len = (p == null) ? 0 : p.length;
        ByteBuffer buf = ByteBuffer.allocate(24 + len).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(cmd).putInt(a0).putInt(a1).putInt(len);
        buf.putInt(calculateChecksum(p)).putInt(cmd ^ 0xFFFFFFFF);
        if (p != null) buf.put(p);
        channel.write(buf.array());
    }

    private AdbPacket readPacket() throws IOException {
        byte[] h = new byte[24];
        channel.read(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        int cmd = b.getInt(), a0 = b.getInt(), a1 = b.getInt(), len = b.getInt();
        
        // CONSOME OS 8 BYTES RESTANTES (Checksum e Magic) para limpar o buffer!
        b.getInt(); b.getInt();

        byte[] p = null;
        if (len > 0) { 
            p = new byte[len]; 
            channel.read(p); 
        }
        return new AdbPacket(cmd, a0, a1, p);
    }

    private int calculateChecksum(byte[] p) {
        if (p == null) return 0;
        int s = 0;
        for (byte b : p) s += (b & 0xFF);
        return s;
    }

    @Override public void close() throws IOException {
        closed = true;
        channel.close();
    }

    private static class AdbPacket {
        int command, arg0, arg1; byte[] payload;
        AdbPacket(int c, int a0, int a1, byte[] p) { command = c; arg0 = a0; arg1 = a1; payload = p; }
    }
}
