package com.mycompany.membershipcardgui;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TestInitInfo {

    public static void main(String[] args) {
        try {
            // 1. DỮ LIỆU CẦN NẠP (Theo yêu cầu của bạn)
            String rawData = "123456|CT00001|Nguyen Duc Anh|27/01/2003|Nam|0971318047";
            System.out.println("Data gốc: " + rawData);

            // 2. KHÓA AES (PHẢI TRÙNG KHỚP VỚI BƯỚC IMPORT KEY)
            // Ở bước trước ta dùng 32 bytes toàn 0xAA, giờ cũng phải y hệt.
            byte[] aesKey = new byte[32];
            Arrays.fill(aesKey, (byte) 0xAA);

            // 3. PADDING (Thêm số 0 vào cuối cho đủ bội số của 16)
            // Vì Applet dùng NOPAD, nên Client phải tự lo việc làm tròn block.
            byte[] inputBytes = rawData.getBytes(StandardCharsets.UTF_8);
            int blockSize = 16;
            int paddedLength = (inputBytes.length / blockSize + 1) * blockSize;
            // Nếu vừa khít thì vẫn nên pad thêm 1 block 0 để Applet nhận biết điểm dừng
            if (inputBytes.length % blockSize == 0) paddedLength += blockSize;

            byte[] paddedInput = new byte[paddedLength];
            System.arraycopy(inputBytes, 0, paddedInput, 0, inputBytes.length);
            // Các byte còn lại mặc định là 0x00 (Zero Padding) - Đúng chuẩn Applet mong đợi

            // 4. MÃ HÓA AES-256
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encryptedData = cipher.doFinal(paddedInput);

            // 5. IN RA LỆNH APDU
            // Header: 00 22 00 00 + Độ dài (Hex)
            StringBuilder apdu = new StringBuilder();
            apdu.append("/send 00 22 00 00 ");
            apdu.append(String.format("%02X ", encryptedData.length)); // Lc

            for (byte b : encryptedData) {
                apdu.append(String.format("%02X", b));
            }

            System.out.println("\n========== COPY DÒNG DƯỚI VÀO TOOL TEST ==========");
            System.out.println(apdu.toString());
            System.out.println("==================================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}