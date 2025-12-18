package com.mycompany.membershipcardgui;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.smartcardio.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class ImageClientTest extends JFrame {

    // --- CẤU HÌNH ---
    private static final byte[] APPLET_AID = {
            (byte)0x11, (byte)0x22, (byte)0x33, (byte)0x44, (byte)0x55, (byte)0x11
    };
    private static final int INS_UPLOAD   = 0x01;
    private static final int INS_DOWNLOAD = 0x02;

    // GIỚI HẠN DUNG LƯỢNG ẢNH TRÊN THẺ (10KB)
    private static final int MAX_CARD_IMAGE_SIZE = 10240;

    // --- UI COMPONENTS ---
    private JLabel lblOriginal;
    private JLabel lblRetrieved;
    private JTextArea logArea;
    private JButton btnUpload;
    private JButton btnDownload;
    private JButton btnConnect;
    private JButton btnDisconnect;
    private JLabel lblStatus;

    // --- VARS ---
    private Card card;
    private CardChannel channel;
    private boolean isConnected = false;
    private byte[] currentImageData;

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new ImageClientTest().setVisible(true));
    }

    public ImageClientTest() {
        setTitle("Client Ảnh Thẻ Thông Minh (Auto Resize < 10KB)");
        setSize(950, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // 1. PANEL KẾT NỐI
        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnConnect = new JButton("Kết nối thẻ");
        btnDisconnect = new JButton("Ngắt kết nối");
        lblStatus = new JLabel("● Chưa kết nối");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblStatus.setForeground(Color.RED);
        btnConnect.addActionListener(e -> connectToCard());
        btnDisconnect.addActionListener(e -> disconnectFromCard());
        connectionPanel.add(btnConnect); connectionPanel.add(btnDisconnect);
        connectionPanel.add(Box.createHorizontalStrut(20)); connectionPanel.add(lblStatus);
        add(connectionPanel, BorderLayout.NORTH);

        // 2. PANEL ẢNH
        JPanel imagePanel = new JPanel(new GridLayout(1, 2, 20, 0));
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Ảnh Gốc (Sẽ tự nén nếu > 10KB)"));
        lblOriginal = new JLabel("Chưa chọn ảnh", SwingConstants.CENTER);
        lblOriginal.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        JButton btnBrowse = new JButton("Chọn Ảnh");
        btnUpload = new JButton("Gửi xuống thẻ");
        btnUpload.setEnabled(false);
        JPanel leftBtnPanel = new JPanel(); leftBtnPanel.add(btnBrowse); leftBtnPanel.add(btnUpload);
        leftPanel.add(lblOriginal, BorderLayout.CENTER); leftPanel.add(leftBtnPanel, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Ảnh Từ Thẻ (Download)"));
        lblRetrieved = new JLabel("Chưa tải ảnh", SwingConstants.CENTER);
        lblRetrieved.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        btnDownload = new JButton("Lấy ảnh về");
        btnDownload.setEnabled(false);
        JPanel rightBtnPanel = new JPanel(); rightBtnPanel.add(btnDownload);
        rightPanel.add(lblRetrieved, BorderLayout.CENTER); rightPanel.add(rightBtnPanel, BorderLayout.SOUTH);

        imagePanel.add(leftPanel); imagePanel.add(rightPanel);
        add(imagePanel, BorderLayout.CENTER);

        // 3. PANEL LOG
        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        // Events
        btnBrowse.addActionListener(e -> chooseImage());
        btnUpload.addActionListener(e -> uploadImage());
        btnDownload.addActionListener(e -> downloadImage());
    }

    // =============================================================
    // CHỨC NĂNG MỚI: CHỌN VÀ TỰ ĐỘNG RESIZE ẢNH
    // =============================================================
    private void chooseImage() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            new Thread(() -> { // Xử lý ảnh trong luồng riêng để không đơ UI
                try {
                    log("--- Đang xử lý ảnh: " + file.getName() + " ---");
                    BufferedImage originalImg = ImageIO.read(file);
                    if (originalImg == null) { log("File không hợp lệ!"); return; }

                    // 1. Kiểm tra kích thước ban đầu (thử chuyển sang JPG byte array)
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ImageIO.write(originalImg, "jpg", bos);
                    byte[] initialBytes = bos.toByteArray();
                    log("Kích thước gốc (JPG): " + initialBytes.length + " bytes");

                    if (initialBytes.length <= MAX_CARD_IMAGE_SIZE) {
                        log("Ảnh đã nhỏ hơn 10KB. Sử dụng ảnh gốc.");
                        currentImageData = initialBytes;
                    } else {
                        log("Ảnh > 10KB. Tiến hành nén tự động...");
                        // 2. GỌI HÀM NÉN NẾU CẦN
                        currentImageData = compressImageToTargetSize(originalImg, MAX_CARD_IMAGE_SIZE);
                        log("Đã nén xong! Kích thước mới: " + currentImageData.length + " bytes. Sẵn sàng upload.");
                    }

                    // Hiển thị ảnh gốc lên UI
                    SwingUtilities.invokeLater(() -> {
                        displayImage(lblOriginal, originalImg);
                        if (isConnected) btnUpload.setEnabled(true);
                    });

                } catch (Exception ex) {
                    log("Lỗi xử lý ảnh: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }).start();
        }
    }

    /**
     * Hàm nén ảnh lặp đi lặp lại cho đến khi đạt kích thước mục tiêu.
     * Sử dụng kết hợp giảm chất lượng JPEG và giảm kích thước vật lý.
     */
    private byte[] compressImageToTargetSize(BufferedImage originalImage, int targetSize) throws IOException {
        // Chuyển đổi sang RGB để tránh lỗi màu khi nén PNG có nền trong suốt sang JPG
        BufferedImage imageToCompress = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = imageToCompress.createGraphics();
        g2d.drawImage(originalImage, 0, 0, Color.WHITE, null);
        g2d.dispose();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        // Tìm người ghi ảnh JPEG
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IOException("Không tìm thấy JPG writer");
        ImageWriter writer = writers.next();

        // Thiết lập thông số nén
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);

        float quality = 0.9f; // Bắt đầu từ chất lượng 90%
        byte[] result = null;

        int attempts = 0;
        // Vòng lặp nén
        while (attempts < 20) { // Giới hạn số lần thử để tránh lặp vô tận
            bos.reset();
            ImageOutputStream ios = ImageIO.createImageOutputStream(bos);
            writer.setOutput(ios);

            log("..Thử nén: Quality=" + String.format("%.2f", quality) + " | Size=" + imageToCompress.getWidth() + "x" + imageToCompress.getHeight());

            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(imageToCompress, null, null), param);
            ios.close();

            result = bos.toByteArray();
            log("  -> Kết quả: " + result.length + " bytes");

            if (result.length <= targetSize) {
                break; // Thành công!
            }

            // Nếu vẫn còn to, giảm chất lượng tiếp
            quality -= 0.1f;

            // Nếu chất lượng quá thấp (ảnh sẽ rất xấu), tiến hành giảm kích thước vật lý
            if (quality < 0.1f) {
                log("-> Chất lượng quá thấp. Giảm kích thước ảnh xuống 70%...");
                int newW = (int)(imageToCompress.getWidth() * 0.7);
                int newH = (int)(imageToCompress.getHeight() * 0.7);

                // Đảm bảo không resize quá bé
                if (newW < 50 || newH < 50) throw new IOException("Không thể nén nhỏ hơn được nữa.");

                BufferedImage scaledBtnImage = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = scaledBtnImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(imageToCompress, 0, 0, newW, newH, null);
                g.dispose();

                imageToCompress = scaledBtnImage;
                quality = 0.7f; // Reset chất lượng lên mức chấp nhận được cho ảnh nhỏ hơn
            }
            attempts++;
        }

        writer.dispose();
        if (result == null || result.length > targetSize) {
            throw new IOException("Không thể nén ảnh xuống dưới 10KB sau nhiều lần thử.");
        }
        return result;
    }

    private void uploadImage() {
        if (!isConnected || channel == null) { log("Chưa kết nối thẻ!"); return; }
        if (currentImageData == null) return;

        new Thread(() -> {
            try {
                // 1. PADDING DỮ LIỆU CHO TRÒN 16 BYTES
                byte[] paddedData = padData(currentImageData);

                log("--- Bắt đầu Upload (AES Encrypt On-Card) ---");
                log("Gốc: " + currentImageData.length + " bytes -> Padded: " + paddedData.length + " bytes");

                long startTime = System.currentTimeMillis();

                // Block size phải chia hết cho 16 (240 là số đẹp nhất < 255)
                int blockSize = 240;
                int offset = 0;
                boolean isFirst = true;

                while (offset < paddedData.length) {
                    int length = Math.min(blockSize, paddedData.length - offset);
                    byte[] chunk = new byte[length];
                    System.arraycopy(paddedData, offset, chunk, 0, length);

                    int p1 = isFirst ? 0x00 : 0x01;
                    CommandAPDU cmd = new CommandAPDU(0x00, INS_UPLOAD, p1, 0x00, chunk);
                    ResponseAPDU resp = channel.transmit(cmd);

                    if (resp.getSW() != 0x9000) {
                        throw new Exception("Lỗi tại offset " + offset + ". SW: " + Integer.toHexString(resp.getSW()));
                    }

                    offset += length;
                    isFirst = false;

                    // Log ít lại cho đỡ lag
                    if (offset % (blockSize * 5) == 0) log("Đã gửi & mã hóa: " + offset + " bytes...");
                }

                long duration = System.currentTimeMillis() - startTime;
                log("Upload & Mã hóa hoàn tất trong " + duration + "ms!");
                JOptionPane.showMessageDialog(this, "Upload & Encrypt thành công!");

            } catch (Exception ex) {
                log("Lỗi Upload: " + ex.getMessage());
            }
        }).start();
    }

    /**
     * Hàm thêm padding (byte 0) vào cuối mảng để kích thước chia hết cho 16
     */
    private byte[] padData(byte[] src) {
        int remainder = src.length % 16;
        if (remainder == 0) return src; // Đã đẹp rồi

        int paddingNeeded = 16 - remainder;
        byte[] padded = new byte[src.length + paddingNeeded];
        System.arraycopy(src, 0, padded, 0, src.length);
        // Các byte còn lại mặc định là 0, không cần fill thủ công
        return padded;
    }

    // =============================================================
    // LOGIC DOWNLOAD (Chỉnh block size thành 240)
    // =============================================================
    private void downloadImage() {
        if (!isConnected || channel == null) { log("Chưa kết nối thẻ!"); return; }

        new Thread(() -> {
            try {
                log("--- Bắt đầu Download (AES Decrypt On-Card) ---");
                long startTime = System.currentTimeMillis();

                ByteArrayOutputStream receivedData = new ByteArrayOutputStream();
                boolean isFirst = true;
                int totalReceived = 0;

                while (true) {
                    int p1 = isFirst ? 0x00 : 0x01;
                    // Yêu cầu lấy 240 bytes (phải chia hết cho 16 để thẻ giải mã được)
                    CommandAPDU cmd = new CommandAPDU(0x00, INS_DOWNLOAD, p1, 0x00, 240);
                    ResponseAPDU resp = channel.transmit(cmd);

                    if (resp.getSW() != 0x9000) {
                        throw new Exception("Lỗi tải về. SW: " + Integer.toHexString(resp.getSW()));
                    }

                    byte[] data = resp.getData();
                    if (data.length == 0) break;

                    receivedData.write(data);
                    totalReceived += data.length;
                    isFirst = false;
                }

                long duration = System.currentTimeMillis() - startTime;
                log("Tải & Giải mã hoàn tất: " + totalReceived + " bytes trong " + duration + "ms");

                byte[] finalBytes = receivedData.toByteArray();
                if (finalBytes.length > 0) {
                    ImageIcon icon = new ImageIcon(finalBytes);
                    // Scale ảnh
                    Image scaled = icon.getImage().getScaledInstance(lblRetrieved.getWidth(), lblRetrieved.getHeight(), Image.SCALE_SMOOTH);
                    lblRetrieved.setIcon(new ImageIcon(scaled));
                } else {
                    log("Không có dữ liệu ảnh.");
                }

            } catch (Exception ex) {
                log("Lỗi Download: " + ex.getMessage());
            }
        }).start();
    }

    private void connectToCard() {
        if (!isConnected) {
            try {
                TerminalFactory factory = TerminalFactory.getDefault();
                List<CardTerminal> terminals = factory.terminals().list();
                if (terminals.isEmpty()) { log("Không tìm thấy đầu đọc thẻ!"); return; }
                CardTerminal terminal = terminals.get(0);
                log("Đang kết nối đến: " + terminal.getName() + "...");
                if (terminal.waitForCardPresent(5000)) {
                    card = terminal.connect("*");
                    channel = card.getBasicChannel();
                    isConnected = true;
                    log("Kết nối thành công!");
                    lblStatus.setText("● Đã kết nối"); lblStatus.setForeground(new Color(0, 150, 0));
                    selectApplet();
                    if (currentImageData != null) btnUpload.setEnabled(true);
                    btnDownload.setEnabled(true);
                } else { log("Timeout: Không thấy thẻ!"); }
            } catch (Exception ex) { log("Lỗi kết nối: " + ex.getMessage()); }
        }
    }

    private void disconnectFromCard() {
        if (isConnected && card != null) {
            try {
                card.disconnect(false); isConnected = false; channel = null;
                log("Đã ngắt kết nối.");
                lblStatus.setText("● Chưa kết nối"); lblStatus.setForeground(Color.RED);
                btnUpload.setEnabled(false); btnDownload.setEnabled(false);
            } catch (Exception ex) { log("Lỗi ngắt kết nối: " + ex.getMessage()); }
        }
    }

    private void selectApplet() {
        try {
            ResponseAPDU resp = channel.transmit(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, APPLET_AID));
            if (resp.getSW() == 0x9000) log("Chọn Applet thành công.");
            else log("Lỗi chọn Applet: " + Integer.toHexString(resp.getSW()));
        } catch (Exception e) { log("Lỗi select: " + e.getMessage()); }
    }

    private void displayImage(JLabel label, BufferedImage img) {
        Image dimg = img.getScaledInstance(label.getWidth(), label.getHeight(), Image.SCALE_SMOOTH);
        label.setIcon(new ImageIcon(dimg));
    }
    private void log(String msg) { SwingUtilities.invokeLater(() -> logArea.append(msg + "\n")); }
}