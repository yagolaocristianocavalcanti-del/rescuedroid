package com.cgutman.adblib;

import java.io.Closeable;
import java.io.IOException;

public interface AdbChannel extends Closeable {
    public void write(byte[] data) throws IOException;
    public void read(byte[] buffer) throws IOException;
}
