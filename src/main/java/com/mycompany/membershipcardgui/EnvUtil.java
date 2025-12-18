package com.mycompany.membershipcardgui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Properties;

public class EnvUtil {
    private static final String ENV_FILE = ".env";
    private static final String MASTER_KEY_NAME = "MASTER_KEY";
    private static final String SERVER_PUB_KEY_NAME = "SERVER_PUBLIC_KEY";
    private static final String SERVER_PRI_KEY_NAME = "SERVER_PRIVATE_KEY";

    /**
     * Lấy Master Key (AES) từ file .env
     */
    public static String getOrGenerateMasterKey() {
        return getPropertyOrGenerate(MASTER_KEY_NAME, () -> generateRandomHex(32));
    }

    /**
     * Lấy hoặc sinh cặp khóa RSA cho Server.
     * Lưu vào .env dưới dạng Base64.
     */
    public static KeyPair getOrGenerateServerKeyPair() {
        Properties props = loadProperties();
        String pubStr = props.getProperty(SERVER_PUB_KEY_NAME);
        String priStr = props.getProperty(SERVER_PRI_KEY_NAME);

        if (pubStr != null && !pubStr.isEmpty() && priStr != null && !priStr.isEmpty()) {
            try {
                // Decode Base64 -> Key Object
                KeyFactory kf = KeyFactory.getInstance("RSA");

                byte[] pubBytes = Base64.getDecoder().decode(pubStr);
                PublicKey pubKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));

                byte[] priBytes = Base64.getDecoder().decode(priStr);
                PrivateKey priKey = kf.generatePrivate(new PKCS8EncodedKeySpec(priBytes));

                return new KeyPair(pubKey, priKey);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Lỗi đọc Key cũ, sẽ sinh Key mới...");
            }
        }

        // Sinh Key mới nếu chưa có
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(1024); // Khớp với thẻ JavaCard
            KeyPair kp = kpg.generateKeyPair();

            // Lưu vào properties
            String pubBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
            String priBase64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());

            props.setProperty(SERVER_PUB_KEY_NAME, pubBase64);
            props.setProperty(SERVER_PRI_KEY_NAME, priBase64);
            saveProperties(props);

            System.out.println("Đã sinh và lưu cặp khóa Server RSA mới.");
            return kp;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // --- CÁC HÀM HỖ TRỢ PRIVATE ---

    private static String getPropertyOrGenerate(String keyName, java.util.function.Supplier<String> generator) {
        Properties props = loadProperties();
        String value = props.getProperty(keyName);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        String newValue = generator.get();
        props.setProperty(keyName, newValue);
        saveProperties(props);
        return newValue;
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        File file = new File(ENV_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return props;
    }

    private static void saveProperties(Properties props) {
        try (FileOutputStream fos = new FileOutputStream(new File(ENV_FILE))) {
            props.store(fos, "Smart Card System Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String generateRandomHex(int byteLength) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[byteLength];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}