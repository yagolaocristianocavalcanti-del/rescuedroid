package com.cgutman.adblib;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import javax.crypto.Cipher;

public class AdbCrypto {
    private PrivateKey key;
    private byte[] pubKeyFull;

    public AdbCrypto(PrivateKey key, byte[] pubKeyFull) {
        this.key = key;
        this.pubKeyFull = pubKeyFull;
    }

    public static AdbCrypto loadAdbCrypto(AdbBase64 base64, File privKey, File pubKey) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] privKeyData = new byte[(int) privKey.length()];
        try (FileInputStream is = new FileInputStream(privKey)) {
            is.read(privKeyData);
        }
        byte[] pubKeyData = new byte[(int) pubKey.length()];
        try (FileInputStream is = new FileInputStream(pubKey)) {
            is.read(pubKeyData);
        }
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privKeyData);
        return new AdbCrypto(KeyFactory.getInstance("RSA").generatePrivate(spec), pubKeyData);
    }

    public static AdbCrypto generateAdbKeyPair(AdbBase64 base64) throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        
        byte[] mincryptKey = convertAdbPublicKey((RSAPublicKey) kp.getPublic());
        String b64Key = base64.encodeToString(mincryptKey);
        byte[] pubKeyFull = (b64Key + " rescuedroid@android\0").getBytes();
        
        return new AdbCrypto(kp.getPrivate(), pubKeyFull);
    }

    private static byte[] convertAdbPublicKey(RSAPublicKey pubkey) {
        ByteBuffer buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        BigInteger n = pubkey.getModulus();
        BigInteger e = pubkey.getPublicExponent();
        BigInteger r32 = BigInteger.ZERO.setBit(32);
        
        buf.putInt(64); // nwords (2048/32)
        buf.putInt(n.mod(r32).modInverse(r32).negate().intValue()); // n0inv
        
        for (int i = 0; i < 64; i++) {
            buf.putInt(n.divide(r32.pow(i)).mod(r32).intValue()); // modulus words
        }
        
        BigInteger r = BigInteger.ZERO.setBit(2048);
        BigInteger rr = r.multiply(r).mod(n);
        for (int i = 0; i < 64; i++) {
            buf.putInt(rr.divide(r32.pow(i)).mod(r32).intValue()); // R^2 words
        }
        
        buf.putInt(e.intValue());
        return buf.array();
    }

    public void saveAdbKeyPair(File privKey, File pubKey) throws IOException {
        try (FileOutputStream os = new FileOutputStream(privKey)) { os.write(key.getEncoded()); }
        try (FileOutputStream os = new FileOutputStream(pubKey)) { os.write(this.pubKeyFull); }
    }

    public byte[] sign(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    public byte[] getAdbPublicKeyPayload() { return pubKeyFull; }
}
