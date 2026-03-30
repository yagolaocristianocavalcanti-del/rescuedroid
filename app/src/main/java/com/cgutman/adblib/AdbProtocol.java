package com.cgutman.adblib;

public class AdbProtocol {
    public static final int CONNECT_TIMEOUT = 10000;
    public static final int CMD_AUTH = 0x48545541;
    public static final int CMD_CNXN = 0x4e584e43;
    public static final int CMD_OPEN = 0x4e45504f;
    public static final int CMD_OKAY = 0x59414b4f;
    public static final int CMD_CLSE = 0x45534c43;
    public static final int CMD_WRTE = 0x45545257;
    
    public static final int AUTH_TOKEN = 1;
    public static final int AUTH_SIGNATURE = 2;
    public static final int AUTH_RSAPUBLICKEY = 3;

    public static final int CONNECT_VERSION = 0x01000000;
    public static final int CONNECT_MAXDATA = 4096;
}
