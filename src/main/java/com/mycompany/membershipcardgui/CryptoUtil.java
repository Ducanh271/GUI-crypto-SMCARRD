package com.mycompany.membershipcardgui;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

public class CryptoUtil {

    /**
     * Sinh khóa AES cho thẻ: SHA256(MasterKey + CardIDString)
     * Lấy 16 byte đầu tiên làm khóa AES-128
     */
    public static byte[] deriveCardKey(String masterKey, String cardCode) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        String input = masterKey + cardCode;
        byte[] hash = sha256.digest(input.getBytes(StandardCharsets.UTF_8));
        return hash; // Cắt lấy 16 byte đầu
    }

    /**
     * Mã hóa dữ liệu bằng RSA Public Key (dùng để gửi AES Key xuống thẻ)
     */
    public static byte[] encryptRSA(byte[] data, byte[] modulusBytes, byte[] exponentBytes) throws Exception {
        // Tái tạo Public Key từ Modulus và Exponent thô
        BigInteger modulus = new BigInteger(1, modulusBytes); // 1 để đảm bảo số dương
        BigInteger exponent = new BigInteger(1, exponentBytes);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey pubKey = factory.generatePublic(spec);

        // Mã hóa (Java Card thường dùng PKCS1Padding)
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pubKey);
        return cipher.doFinal(data);
    }

    /**
     * Mã hóa dữ liệu bằng AES Key (dùng để gửi Info xuống thẻ)
     * Lưu ý: Applet dùng NoPadding, nên ta phải tự Padding ở đây
     */
    public static byte[] encryptAES(String plainText, byte[] aesKey) throws Exception {
        byte[] input = plainText.getBytes(StandardCharsets.UTF_8);

        // Padding thủ công (PKCS7 style) để độ dài chia hết cho 16
        int blockSize = 16;
        int paddingLength = blockSize - (input.length % blockSize);
        byte[] paddedInput = new byte[input.length + paddingLength];
        System.arraycopy(input, 0, paddedInput, 0, input.length);
        for (int i = input.length; i < paddedInput.length; i++) {
            paddedInput[i] = (byte) paddingLength; // Hoặc dùng 0x00 nếu Applet bạn handle 0x00
        }

        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(paddedInput);
    }
}