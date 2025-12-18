package com.mycompany.membershipcardgui;

import javax.crypto.Cipher;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

public class TestImportKey {

    public static void main(String[] args) {
        try {
            // 1. MODULUS CỦA BẠN (Lấy từ kết quả lệnh 00 10 01 00)
            String modulusStr = "DF9DBDCA7CD257B458D2AA390C4D6713CF05BB7209174D06F13CD55ECC8308F89DBD2A83EBB2E0A53FB3F70194CDA2765F8C7C5DC7580CDF550F501A8F6F7BA3E170717A0CFFE95EE2233E21373C4C86DED293F129842F5143CE670DC229A74AEE5451827372C7B359E70851D7F9AB10359F55479D035DAACB3C1DBA0DE60BF1";

            // 2. EXPONENT CỦA BẠN (Lấy từ kết quả lệnh 00 10 02 00)
            String exponentStr = "010001"; // = 65537

            // 3. Tái tạo Public Key RSA
            BigInteger mod = new BigInteger(1, hexToBytes(modulusStr));
            BigInteger exp = new BigInteger(1, hexToBytes(exponentStr));
            RSAPublicKeySpec spec = new RSAPublicKeySpec(mod, exp);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PublicKey pubKey = factory.generatePublic(spec);

            // 4. Tạo AES Key giả định (32 bytes cho AES-256)
            // Ví dụ: Key toàn là AA
            byte[] aesKey = new byte[32];
            Arrays.fill(aesKey, (byte) 0xAA);

            // 5. Mã hóa RSA (PKCS1Padding - Bắt buộc khớp với thẻ)
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, pubKey);
            byte[] encryptedData = cipher.doFinal(aesKey);

            // 6. In ra lệnh APDU
            StringBuilder apdu = new StringBuilder();
            apdu.append("/send 00 21 00 00 80 "); // Header + Lc (128 bytes)

            for (byte b : encryptedData) {
                apdu.append(String.format("%02X", b)); // Xóa khoảng trắng để paste cho gọn
            }

            System.out.println("========== COPY DÒNG DƯỚI VÀO TOOL TEST ==========");
            System.out.println(apdu.toString());
            System.out.println("==================================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] hexToBytes(String s) {
        s = s.replace(" ", ""); // Xóa khoảng trắng nếu có
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}