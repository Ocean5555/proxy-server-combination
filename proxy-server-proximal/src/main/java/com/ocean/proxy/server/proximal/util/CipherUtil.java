package com.ocean.proxy.server.proximal.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2024/1/30 10:40
 */
public class CipherUtil {

    private Cipher encryptCipher;

    private Cipher decryptCipher;

    private byte a;

    public CipherUtil(byte[] token) throws Exception {
        byte a = token[0];
        for (int i = 1; i < token.length; i++) {
            a ^= token[i];
        }
        this.a = a;
        // encryptCipher = initEncryptCipher(token);
        // decryptCipher = initDecryptCipher(token);
    }

    public byte[] encryptData(byte[] data) throws Exception {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte)(a ^ data[i]);
        }
        return result;
        // return encryptCipher.doFinal(data);
    }

    public byte[] decryptData(byte[] data) throws Exception {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte)(a ^ data[i]);
        }
        return result;
        // return decryptCipher.doFinal(data);
    }

    private Cipher initEncryptCipher(byte[] token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec secretKeySpec = new SecretKeySpec(token, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        return cipher;
    }

    private Cipher initDecryptCipher(byte[] token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec secretKeySpec = new SecretKeySpec(token, "AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        return cipher;
    }
}

