package com.cgutman.adblib;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdbStream implements Closeable {
    private AdbConnection adbConn;
    private int localId;
    private int remoteId;
    private boolean closed;
    private LinkedBlockingQueue<byte[]> readQueue = new LinkedBlockingQueue<>();
    private final Object openLock = new Object();
    private final Object writeLock = new Object();
    private boolean isReady = false;
    private AtomicBoolean canWrite = new AtomicBoolean(false);

    public AdbStream(AdbConnection adbConn, int localId) {
        this.adbConn = adbConn;
        this.localId = localId;
    }

    public void addPayload(byte[] payload) {
        readQueue.add(payload);
    }

    public void sendReady() throws IOException {
        adbConn.sendReady(localId, remoteId);
    }

    public void updateRemoteId(int remoteId) {
        this.remoteId = remoteId;
        synchronized (openLock) {
            isReady = true;
            openLock.notifyAll();
        }
        notifyWriteReady();
    }

    public void notifyWriteReady() {
        synchronized (writeLock) {
            canWrite.set(true);
            writeLock.notifyAll();
        }
    }

    public void waitReady() throws IOException, InterruptedException {
        synchronized (openLock) {
            long start = System.currentTimeMillis();
            while (!isReady && !closed && System.currentTimeMillis() - start < 5000) {
                openLock.wait(1000);
            }
            if (!isReady && !closed) throw new IOException("Stream open timeout");
        }
    }

    public void notifyClose() {
        closed = true;
        synchronized (openLock) { openLock.notifyAll(); }
        synchronized (writeLock) { writeLock.notifyAll(); }
        readQueue.add(new byte[0]);
    }

    public byte[] read() throws InterruptedException {
        if (closed && readQueue.isEmpty()) return null;
        byte[] data = readQueue.poll(2000, TimeUnit.MILLISECONDS);
        if (data == null || data.length == 0) return null;
        return data;
    }

    public void write(String data) throws IOException {
        write(data.getBytes("UTF-8"));
    }

    public void write(byte[] data) throws IOException {
        int offset = 0;
        int maxData = 16384;
        
        while (offset < data.length) {
            synchronized (writeLock) {
                while (!canWrite.get() && !closed) {
                    try { writeLock.wait(1000); } catch (InterruptedException e) { break; }
                }
                if (closed) throw new IOException("Stream closed during write");
                canWrite.set(false); // Espera o próximo OKAY
            }

            int len = Math.min(maxData, data.length - offset);
            byte[] chunk = Arrays.copyOfRange(data, offset, offset + len);
            adbConn.sendWrite(localId, remoteId, chunk);
            offset += len;
        }
    }

    public boolean isClosed() { return closed; }

    @Override
    public void close() throws IOException {
        if (!closed) {
            adbConn.sendClose(localId, remoteId);
            closed = true;
        }
    }
}
