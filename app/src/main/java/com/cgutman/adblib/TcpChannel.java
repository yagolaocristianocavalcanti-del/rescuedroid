package com.cgutman.adblib;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class TcpChannel implements AdbChannel {
    private Socket socket;
    private InputStream is;
    private OutputStream os;

    public TcpChannel(Socket socket) throws IOException {
        this.socket = socket;
        this.is = socket.getInputStream();
        this.os = socket.getOutputStream();
    }

    @Override
    public void read(byte[] buffer) throws IOException {
        int totalRead = 0;
        while (totalRead < buffer.length) {
            int res = is.read(buffer, totalRead, buffer.length - totalRead);
            if (res < 0) throw new IOException("Conexão fechada");
            totalRead += res;
        }
    }

    @Override
    public void write(byte[] data) throws IOException {
        os.write(data);
        os.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
