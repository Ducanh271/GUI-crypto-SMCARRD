package com.mycompany.membershipcardgui;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.smartcardio.*;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.sql.Connection; // Cho transaction
import java.sql.SQLException;
import com.formdev.flatlaf.FlatLightLaf;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;
import com.github.lgooddatepicker.components.CalendarPanel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


public class MembershipCardGUI extends JFrame {

    // ================== MÀU CHỦ ĐẠO – MODERN PURPLE ==================
    private static final Color PRIMARY_PURPLE = new Color(106, 76, 147);   // Deep Purple
    private static final Color ACCENT_PURPLE  = new Color(142, 104, 190); // Light Purple
    private static final Color DARK_PURPLE    = new Color(75, 46, 131);   // Very Dark Purple
    private static final Color LIGHT_BG       = new Color(248, 246, 252); // Light background
    private static final Color CARD_BG        = Color.WHITE;
    private static final Color SUCCESS_COLOR  = new Color(88, 166, 124);  // Green
    private static final Color WARNING_COLOR  = new Color(230, 126, 34);  // Orange
    private static final Color DANGER_COLOR   = new Color(231, 76, 60);   // Red
    private static final Color TEXT_DARK      = new Color(44, 44, 44);
    private static final Color TEXT_LIGHT     = new Color(128, 128, 128);

    private static final int LOG_ENTRY_SIZE = 16;

    // ================== BIẾN LOGIC GỐC ==================
    private byte[] fileData;
    private boolean isConnected = false;

    private Card card = null;
    private CardChannel channel = null;
    private static int counter = 1;

    private JLabel imageLabel;
    private JTextField getBalanceField;

    private JFrame frame;
    private JPanel apduPanel, infoPanel, memberPanel;
    private JTextField responseField, getMaKH, getName, getDob, getGender, getPoints;
    private JTextField getPhone;
    private JPasswordField pinField;
    private JTextField makhField, nameField, dobField;
    private JComboBox<String> genderComboBox;
    private JButton browseButton;
    private JLabel imageInfoLabel;
    private JLabel statusIndicator;

    // ==== BUTTONS CHÍNH (giữ logic, nhưng text có icon) ====
    private JButton initCardButton      = createModernButton("Khởi tạo thẻ", "🆕");
    private JButton readCardButton      = createModernButton("Đọc dữ liệu thẻ", "📄");
    private JButton changePinButton     = createModernButton("Thay đổi mã PIN", "🔐");
    private JButton editButton          = createModernButton("Sửa Thông Tin", "✏️");
    private JButton topUpButton         = createModernButton("Nạp tiền", "💳");
    private JButton storeButton         = createModernButton("Cửa hàng", "🛒");
    private JButton upgradeTierButton   = createModernButton("Nâng hạng", "⭐");
    private JButton exchangePointsButton= createModernButton("Đổi điểm", "🎁");
   // private JButton unblockCartButton   = createModernButton("Mở khoá thẻ", "🔓");
    private JButton verifybtn           = createModernButton("Kiểm tra PIN", "✓");
    private JButton viewLogButton       = createModernButton("Xem lịch sử", "📄");
    private JButton forgotPinButton     = createModernButton("RESET PIN", "❓");
    // ================== DATA GỐC ==================
    private static class Product {
        String name;
        long price;
        Product(String n, long p) { name = n; price = p; }
    }

    private static class CartItem {
        Product product;
        int quantity;

        CartItem(Product p, int q) {
            this.product = p;
            this.quantity = q;
        }
    }

    private static class TierPack {
        String name;
        int tier;
        long price;
        TierPack(String n, int t, long p) { name = n; tier = t; price = p; }
    }

    private Product[] products = new Product[]{
            new Product("Áo thun", 100_000L),
            new Product("Quần jean", 500_000L),
            new Product("Thắt lưng", 300_000L),
            new Product("Mũ", 400_000L),
            new Product("Găng tay", 200_000L),
            new Product("Giày sneaker", 1_500_000L),

            // ===== SẢN PHẨM MỚI =====
            new Product("Áo khoác", 800_000L),
            new Product("Ba lô", 650_000L),
            new Product("Ví da", 450_000L),
            new Product("Kính mát", 350_000L)
    };

    private TierPack[] tierPacks = new TierPack[]{
            new TierPack("Bạc (-5%)", 1, 300_000),
            new TierPack("Vàng (-10%)", 2, 700_000),
            new TierPack("Bạch Kim (-15%)", 3, 1_200_000),
            new TierPack("Kim Cương (-20%)", 4, 2_000_000)
    };

    // ================== MAIN ==================
    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(MembershipCardGUI::new);
    }

    // ================== HELPER UI ==================
    private JButton createModernButton(String text, String icon) {

        // HTML giúp Swing render emoji FULL glyph
        String htmlText = "<html>"
                + "<span style='font-family: Segoe UI Emoji; font-size:18px;'>"
                + icon
                + "</span>"
                + "&nbsp;&nbsp;"
                + "<span style='font-family: Segoe UI; font-size:15px;'>"
                + text
                + "</span>"
                + "</html>";

        JButton btn = new JButton(htmlText);

        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(220, 80));
        btn.setForeground(Color.WHITE);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(btn.getBackground().brighter());
            }
            public void mouseExited(MouseEvent e) {
                btn.setBackground(btn.getBackground().darker());
            }
        });

        return btn;
    }


    private void styleConnectionButton(JButton btn, Color bgColor) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bgColor);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        btn.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(bgColor.brighter()); }
            public void mouseExited(MouseEvent e) { btn.setBackground(bgColor); }
        });
    }

    private void styleFunctionButton(JButton btn, Color bgColor) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bgColor);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        btn.setPreferredSize(new Dimension(0, 70));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(bgColor.brighter()); }
            public void mouseExited(MouseEvent e) { btn.setBackground(bgColor); }
        });
    }

    private void styleSmallActionButton(JButton btn, Color bgColor) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bgColor);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(bgColor.brighter()); }
            public void mouseExited(MouseEvent e) { btn.setBackground(bgColor); }
        });
    }

    private JLabel createLabel(String text) {
        JLabel lb = new JLabel(text);
        lb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lb.setForeground(TEXT_DARK);
        return lb;
    }

    // ================== DATE PICKER (MATERIAL STYLE) ==================
    private DatePicker createDatePicker(String initialDate) {

        // ===== SETTINGS (AN TOÀN BẢN CŨ) =====
        DatePickerSettings settings = new DatePickerSettings();

        settings.setFormatForDatesCommonEra("dd/MM/yyyy");
        settings.setAllowEmptyDates(false);

        settings.setFontValidDate(new Font("Segoe UI", Font.PLAIN, 13));
        settings.setFontInvalidDate(new Font("Segoe UI", Font.PLAIN, 13));
        settings.setFontCalendarDateLabels(new Font("Segoe UI", Font.PLAIN, 12));
        settings.setFontCalendarWeekdayLabels(new Font("Segoe UI", Font.PLAIN, 12));

        // ===== DATE PICKER =====
        DatePicker datePicker = new DatePicker(settings);
        datePicker.setPreferredSize(new Dimension(200, 28));

        // ===== LÀM ĐẸP TEXT FIELD =====
        JTextField tf = datePicker.getComponentDateTextField();

        tf.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tf.setBackground(Color.WHITE);
        tf.setForeground(new Color(44, 44, 44));
        tf.setCaretColor(new Color(106, 76, 147)); // tím chủ đạo
        tf.setSelectionColor(new Color(210, 195, 230));
        tf.setSelectedTextColor(Color.BLACK);

        // Border hiện đại
        Border normalBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210), 1),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        );

        Border focusBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(106, 76, 147), 2),
                BorderFactory.createEmptyBorder(4, 7, 4, 7)
        );

        tf.setBorder(normalBorder);

        // ===== HIỆU ỨNG FOCUS (RẤT QUAN TRỌNG CHO UI MODERN) =====
        tf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                tf.setBorder(focusBorder);
            }

            @Override
            public void focusLost(FocusEvent e) {
                tf.setBorder(normalBorder);
            }
        });

        // ===== SET NGÀY BAN ĐẦU =====
        if (initialDate != null && !initialDate.trim().isEmpty()) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                datePicker.setDate(LocalDate.parse(initialDate.trim(), formatter));
            } catch (Exception ignored) {
            }
        }

        return datePicker;
    }

    // ================== CONSTRUCTOR – GIAO DIỆN NGOÀI ==================
    public MembershipCardGUI() {
        Database.createNewTable();
        frame = new JFrame("Hệ Thống Quản Lý Thẻ Thành Viên");
        frame.setSize(1200, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(0, 0));
        frame.getContentPane().setBackground(LIGHT_BG);

        // Header
        JPanel header = createHeaderPanel();
        frame.add(header, BorderLayout.NORTH);

        // Left – kết nối thẻ
        apduPanel = createConnectionPanel();
        frame.add(apduPanel, BorderLayout.WEST);

        // Center – chức năng
        memberPanel = createFunctionsPanel();
        frame.add(memberPanel, BorderLayout.CENTER);

        // Status bar
        JPanel statusBar = createStatusBar();
        frame.add(statusBar, BorderLayout.SOUTH);

        // Gán sự kiện cho các button chức năng
        attachEventListeners();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PRIMARY_PURPLE);
        header.setPreferredSize(new Dimension(0, 80));
        header.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));

        JLabel titleLabel = new JLabel("HỆ THỐNG QUẢN LÝ THẺ THÀNH VIÊN");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel("Quản lý thông tin và giao dịch thẻ thông minh");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitleLabel.setForeground(new Color(230, 230, 230));

        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titlePanel.setOpaque(false);
        titlePanel.add(titleLabel);
        titlePanel.add(subtitleLabel);

        statusIndicator = new JLabel("● Chưa kết nối");
        statusIndicator.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusIndicator.setForeground(new Color(255, 200, 200));

        header.add(titlePanel, BorderLayout.WEST);
        header.add(statusIndicator, BorderLayout.EAST);
        return header;
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(320, 0));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(230, 230, 230)));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(CARD_BG);
        content.setBorder(BorderFactory.createEmptyBorder(25, 20, 25, 20));

        JLabel connectionTitle = new JLabel("KẾT NỐI THẺ");
        connectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        connectionTitle.setForeground(PRIMARY_PURPLE);
        connectionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(connectionTitle);
        content.add(Box.createVerticalStrut(20));

        JButton connectButton = new JButton("Kết nối thẻ");
        JButton disconnectButton = new JButton("Ngắt kết nối");

        styleConnectionButton(connectButton, SUCCESS_COLOR);
        styleConnectionButton(disconnectButton, DANGER_COLOR);

        connectButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        disconnectButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(connectButton);
        content.add(Box.createVerticalStrut(12));
        content.add(disconnectButton);
        content.add(Box.createVerticalStrut(25));

        JLabel statusLabel = new JLabel("TRẠNG THÁI");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(TEXT_DARK);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(statusLabel);
        content.add(Box.createVerticalStrut(10));

        responseField = new JTextField();
        responseField.setEditable(false);
        responseField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        responseField.setBackground(LIGHT_BG);
        responseField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        responseField.setAlignmentX(Component.LEFT_ALIGNMENT);
        responseField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        content.add(responseField);
        content.add(Box.createVerticalGlue());

        panel.add(content, BorderLayout.CENTER);

        connectButton.addActionListener(e -> connectToCard());
        disconnectButton.addActionListener(e -> disconnectFromCard());

        return panel;
    }

    private JPanel createFunctionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        JLabel title = new JLabel("CHỨC NĂNG");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(PRIMARY_PURPLE);

        JPanel grid = new JPanel(new GridLayout(4, 3, 15, 15));
        grid.setBackground(LIGHT_BG);

        styleFunctionButton(initCardButton, ACCENT_PURPLE);
        styleFunctionButton(readCardButton, PRIMARY_PURPLE);
        styleFunctionButton(topUpButton, SUCCESS_COLOR);
        styleFunctionButton(storeButton, new Color(52, 152, 219));
        styleFunctionButton(upgradeTierButton, new Color(241, 196, 15));
        styleFunctionButton(exchangePointsButton, new Color(155, 89, 182));
  //     styleFunctionButton(unblockCartButton, WARNING_COLOR);
        styleFunctionButton(viewLogButton, new Color(41, 128, 185));
        styleFunctionButton(forgotPinButton, new Color(52, 152, 219));
        forgotPinButton.addActionListener(e -> forgotPin());

        grid.add(createFunctionCard(initCardButton));
        grid.add(createFunctionCard(readCardButton));
        grid.add(createFunctionCard(topUpButton));
        grid.add(createFunctionCard(storeButton));
        grid.add(createFunctionCard(upgradeTierButton));
        grid.add(createFunctionCard(exchangePointsButton));
  //      grid.add(createFunctionCard(unblockCartButton));
        grid.add(createFunctionCard(viewLogButton));
        grid.add(createFunctionCard(forgotPinButton));

        panel.add(title, BorderLayout.NORTH);
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createFunctionCard(JButton button) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 2, 2, new Color(230, 230, 230)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        card.add(button, BorderLayout.CENTER);
        return card;
    }

    // ==== CARD CHO SHOP / NÂNG HẠNG (MỖI Ô 1 MÀU) ====

    // bo viền khi chọn / bỏ chọn
    private void setCardSelected(JPanel card, boolean selected) {
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(selected ? Color.WHITE : new Color(230, 230, 230),
                        selected ? 3 : 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
    }

    // tạo 1 ô vuông màu cho sản phẩm / gói hạng
    private JPanel createSelectCard(String title, String subtitle, Color bgColor) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(bgColor);
        setCardSelected(card, false); // ban đầu chưa chọn

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setHorizontalAlignment(SwingConstants.LEFT);

        JLabel lblSub = new JLabel(subtitle);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(245, 245, 245));
        lblSub.setHorizontalAlignment(SwingConstants.LEFT);

        card.add(lblTitle, BorderLayout.CENTER);
        card.add(lblSub, BorderLayout.SOUTH);

        return card;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(new Color(250, 250, 250));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(8, 20, 8, 20)
        ));

        JLabel versionLabel = new JLabel("v1.0.0 | Membership Card Management System");
        versionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        versionLabel.setForeground(TEXT_LIGHT);

        JLabel copyLabel = new JLabel("© 2025 All Rights Reserved");
        copyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        copyLabel.setForeground(TEXT_LIGHT);

        statusBar.add(versionLabel, BorderLayout.WEST);
        statusBar.add(copyLabel, BorderLayout.EAST);
        return statusBar;
    }

    private void attachEventListeners() {
        initCardButton.addActionListener(e -> {
            try {
                initializeCard();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        readCardButton.addActionListener(e -> readCard());

        editButton.addActionListener(e -> {
            try {
                // Nếu chưa đọc thẻ thì đọc trước
                if (getName == null || getDob == null || getPhone == null || getGender == null) {
                    readCard();   // verify PIN + đọc dữ liệu
                    return;
                }

                Window window = SwingUtilities.getWindowAncestor(infoPanel);
                if (window != null) window.dispose();

                changeInfo();
            } catch (Exception ex) {
                responseField.setText("Lỗi: " + ex.getMessage());
                JOptionPane.showMessageDialog(null, "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        changePinButton.addActionListener(e -> {
            Window window = SwingUtilities.getWindowAncestor(infoPanel);
            if (window != null) window.dispose();
            changePin();
        });

        exchangePointsButton.addActionListener(e -> exchangePoints());
//        unblockCartButton.addActionListener(e -> unblockCard());
        verifybtn.addActionListener(e -> verifyPin());

        viewLogButton.addActionListener(e -> viewTransactionLogs());
        topUpButton.addActionListener(e -> topUpMoney());
        storeButton.addActionListener(e -> openStore());
        upgradeTierButton.addActionListener(e -> openUpgradeShop());
    }

    // ================== LOGIC GỐC – KẾT NỐI THẺ ==================
    private void connectToCard() {
        if (!isConnected) {
            try {
                TerminalFactory factory = TerminalFactory.getDefault();
                List<CardTerminal> terminals = factory.terminals().list();
                if (terminals.isEmpty()) {
                    responseField.setText("Không tìm thấy đầu đọc thẻ!");
                    return;
                }

                CardTerminal terminal = terminals.get(0);
                responseField.setText("Đang kết nối...");
                if (terminal.waitForCardPresent(10000)) {
                    card = terminal.connect("*");
                    channel = card.getBasicChannel();
                    isConnected = true;
                    responseField.setText("Kết nối thành công!");
                    selectApplet(); // auto select AID
                    checkCardStatus();

                    statusIndicator.setText("● Đã kết nối");
                    statusIndicator.setForeground(new Color(150, 255, 150));
                } else {
                    responseField.setText("Không có thẻ trong đầu đọc!");
                }
            } catch (Exception ex) {
                responseField.setText("Lỗi: " + ex.getMessage());
            }
        } else {
            responseField.setText("Đã kết nối trước đó!");
        }
    }

    private void disconnectFromCard() {
        if (isConnected && card != null) {
            try {
                card.disconnect(false);
                isConnected = false;
                responseField.setText("Ngắt kết nối thành công!");
                statusIndicator.setText("● Chưa kết nối");
                initCardButton.setEnabled(true);
                readCardButton.setEnabled(true);
                statusIndicator.setForeground(new Color(255, 200, 200));
            } catch (Exception ex) {
                responseField.setText("Lỗi khi ngắt kết nối: " + ex.getMessage());
            }
        } else {
            responseField.setText("Chưa có kết nối để ngắt!");
        }
    }

    private void selectApplet() {
        if (!isConnected || channel == null) {
            responseField.setText("Bạn phải kết nối với thẻ trước!");
            return;
        }

        try {
            String aid = "112233445500";
            byte[] aidBytes = hexStringToByteArray(aid);

            CommandAPDU selectCommand = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, aidBytes);
            ResponseAPDU response = channel.transmit(selectCommand);

            int sw1 = response.getSW1();
            int sw2 = response.getSW2();
            if (sw1 == 0x90 && sw2 == 0x00) {
                responseField.setText("Chọn Applet thành công!");
            } else {
                responseField.setText(String.format("Lỗi khi chọn applet! SW: %02X %02X", sw1, sw2));
            }
        } catch (Exception ex) {
            responseField.setText("Lỗi: " + ex.getMessage());
        }
    }

    // ================== VERIFY PIN – UI MODERN ==================
    private boolean verifyPin() {
        try {
            while (true) {
                JPanel pinPanel = new JPanel(new GridBagLayout());
                pinPanel.setBackground(LIGHT_BG);
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.insets = new Insets(5,5,5,5);
                gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
                pinPanel.add(createLabel("Nhập mã PIN (6 số):"), gbc);

                JPasswordField passwordField = new JPasswordField();
                passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                passwordField.setPreferredSize(new Dimension(150, 25)); // Set size
                gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
                pinPanel.add(passwordField, gbc);

                int option = JOptionPane.showConfirmDialog(null, pinPanel, "Xác thực mã PIN",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

                if (option != JOptionPane.OK_OPTION) {
                    responseField.setText("Bạn đã hủy nhập mã PIN.");
                    return false;
                }

                String pin = new String(passwordField.getPassword()).trim();
                if (!pin.matches("\\d{6}")) {
                    JOptionPane.showMessageDialog(null, "Mã PIN phải gồm đúng 6 chữ số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    continue;
                }

                // Gửi lệnh Verify PIN
                byte[] pinBytes = pin.getBytes(StandardCharsets.UTF_8);
                CommandAPDU verifyPinCommand = new CommandAPDU(0x00, 0x02, 0x00, 0x00, pinBytes);
                ResponseAPDU verifyResponse = channel.transmit(verifyPinCommand);

                int sw = verifyResponse.getSW();

                // Xử lý các mã lỗi đặc biệt
                if (sw == 0x6983) {
                    JOptionPane.showMessageDialog(null, "Thẻ đã bị khóa (Blocked) do nhập sai quá nhiều lần!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                if (sw != 0x9000) {
                    JOptionPane.showMessageDialog(null, "Lỗi từ thẻ! SW=" + Integer.toHexString(sw), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return false;
                }

                byte[] data = verifyResponse.getData();
                if (data.length < 1) {
                    JOptionPane.showMessageDialog(null, "Thẻ không trả về dữ liệu!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return false;
                }

                // [FIX QUAN TRỌNG]: Thẻ trả về số lần thử còn lại.
                // Nếu đúng PIN -> Trả về MAX (3).
                byte remainingTries = data[0];

                if (remainingTries == 0x03) {
                    responseField.setText("PIN đúng. Đang xác thực thẻ...");

                    if (authenticateCard()) {
                        responseField.setText("Xác thực thẻ thành công! (Thẻ thật)");
                        //JOptionPane.showMessageDialog(null, "Đăng nhập thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                        return true;
                    } else {
                        responseField.setText("CẢNH BÁO: Thẻ giả mạo!");
                        JOptionPane.showMessageDialog(null, "PIN đúng nhưng xác thực thẻ thất bại!\nCó thể đây là thẻ giả (Clone).", "Cảnh báo bảo mật", JOptionPane.ERROR_MESSAGE);
                        return false; // Chặn đăng nhập dù biết PIN
                    }
                } else {
                    String msg = "Mã PIN không đúng!";
                    msg += "\nBạn còn " + remainingTries + " lần thử trước khi thẻ bị khóa.";
                    responseField.setText(msg);
                    JOptionPane.showMessageDialog(null, msg, "Cảnh báo", JOptionPane.WARNING_MESSAGE);

                    if (remainingTries == 0) return false;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Lỗi hệ thống: " + ex.getMessage());
            return false;
        }
    }

    // [CẬP NHẬT] Hàm xác thực thẻ sử dụng Key từ Database (Chống thẻ giả)
    private boolean authenticateCard() {
        try {
            // 1. Lấy Card ID (Mã KH) từ thẻ để biết là ai
            // (Lệnh này không cần PIN nên gọi thoải mái)
            CommandAPDU getIDCmd = new CommandAPDU(0x00, 0x26, 0x00, 0x00);
            ResponseAPDU respID = channel.transmit(getIDCmd);

            if (respID.getSW() != 0x9000) return false;

            byte[] idBytes = respID.getData();
            int realLen = idBytes.length;
            while(realLen > 0 && idBytes[realLen-1] == 0) realLen--;
            String cardCode = new String(idBytes, 0, realLen, StandardCharsets.UTF_8); // Ví dụ: "CT00005"

            // 2. Chuyển đổi CardCode sang ID Database (CT00005 -> 5)
            int dbId;
            try {
                // Cắt bỏ chữ "CT" và parse số
                dbId = Integer.parseInt(cardCode.substring(2));
            } catch (Exception e) {
                System.out.println("Lỗi parse ID: " + cardCode);
                return false;
            }

            // 3. Lấy Public Key CỦA THẺ từ DATABASE
            String[] dbKeys = Database.getPublicKey(dbId);

            if (dbKeys == null) {
                JOptionPane.showMessageDialog(this, "Thẻ này chưa được đăng ký trên hệ thống (Không tìm thấy Key)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            // Chuyển Hex String từ DB thành BigInteger
            java.math.BigInteger modulus = new java.math.BigInteger(dbKeys[0], 16);
            java.math.BigInteger exponent = new java.math.BigInteger(dbKeys[1], 16);

            // Tạo đối tượng Public Key từ dữ liệu DB
            java.security.spec.RSAPublicKeySpec spec = new java.security.spec.RSAPublicKeySpec(modulus, exponent);
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("RSA");
            java.security.PublicKey cardPubKeyFromDB = factory.generatePublic(spec);

            // 4. Gửi Challenge xuống thẻ
            byte[] challenge = new byte[32];
            new java.security.SecureRandom().nextBytes(challenge);

            CommandAPDU signCmd = new CommandAPDU(0x00, 0x11, 0x00, 0x00, challenge);
            ResponseAPDU signResp = channel.transmit(signCmd);

            if (signResp.getSW() != 0x9000) return false;
            byte[] signature = signResp.getData();

            // 5. Verify Chữ ký bằng Key của DB
            java.security.Signature verifier = java.security.Signature.getInstance("SHA1withRSA");
            verifier.initVerify(cardPubKeyFromDB);
            verifier.update(challenge);

            return verifier.verify(signature);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ================== KHỞI TẠO THẺ – FORM ĐẸP ==================
    private void initializeCard() throws IOException {
        if (!isConnected || channel == null) {
            responseField.setText("Bạn phải kết nối với thẻ trước!");
            JOptionPane.showMessageDialog(null, "Bạn phải kết nối với thẻ trước!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Vòng lặp nhập liệu (UI giữ nguyên)
        while (true) {
            JPanel addMemberPanel = new JPanel(new BorderLayout(15, 15));
            addMemberPanel.setBackground(LIGHT_BG);
            addMemberPanel.setBorder(BorderFactory.createTitledBorder("Khởi tạo thẻ"));

            // ----- PANEL ẢNH -----
            JPanel imagePanel = new JPanel();
            imagePanel.setBackground(Color.WHITE);
            imagePanel.setPreferredSize(new Dimension(140, 180));
            imagePanel.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
            imagePanel.setLayout(new BorderLayout());

            imageLabel = new JLabel();
            imageLabel.setHorizontalAlignment(JLabel.CENTER);
            imagePanel.add(imageLabel, BorderLayout.CENTER);

            browseButton = new JButton("Tải ảnh lên");
            browseButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            browseButton.setBackground(ACCENT_PURPLE);
            browseButton.setForeground(Color.WHITE);
            browseButton.addActionListener(e -> fileData = chooseAndReadFile());

            JPanel leftPanel = new JPanel();
            leftPanel.setBackground(LIGHT_BG);
            leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
            leftPanel.add(Box.createVerticalStrut(10));
            leftPanel.add(imagePanel);
            leftPanel.add(Box.createVerticalStrut(10));
            leftPanel.add(browseButton);
            leftPanel.add(Box.createVerticalGlue());

            // ----- PANEL THÔNG TIN -----
            JPanel rightPanel = new JPanel(new GridBagLayout());
            rightPanel.setBackground(LIGHT_BG);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;
            int row = 0;

            // PIN
            gbc.gridx = 0;
            gbc.gridy = row;
            rightPanel.add(createLabel("Pin:"), gbc);
            pinField = new JPasswordField();
            pinField.setPreferredSize(new Dimension(200, 25));
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            rightPanel.add(pinField, gbc);
            row++;

            // Mã KH (Hiển thị tượng trưng, ID thật sẽ lấy từ DB)
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.fill = 0;
            gbc.weightx = 0;
            rightPanel.add(createLabel("Mã KH:"), gbc);
            makhField = new JTextField("(Tự động sinh)");
            makhField.setEditable(false);
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            rightPanel.add(makhField, gbc);
            row++;

            // Họ tên
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0;
            gbc.fill = 0;
            rightPanel.add(createLabel("Họ và Tên:"), gbc);
            nameField = new JTextField();
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            rightPanel.add(nameField, gbc);
            row++;

            // Ngày sinh (DatePicker)
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0;
            gbc.fill = 0;
            rightPanel.add(createLabel("Ngày Sinh:"), gbc);

            DatePicker dobPicker = createDatePicker(null);
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            rightPanel.add(dobPicker, gbc);
            row++;

            // Giới tính
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0;
            gbc.fill = 0;
            rightPanel.add(createLabel("Giới Tính:"), gbc);
            genderComboBox = new JComboBox<>(new String[]{"Nam", "Nữ"});
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            rightPanel.add(genderComboBox, gbc);
            row++;

            // Số điện thoại
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0;
            gbc.fill = 0;
            rightPanel.add(createLabel("Số điện thoại:"), gbc);
            JTextField phoneField = new JTextField();
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            rightPanel.add(phoneField, gbc);
            row++;

            addMemberPanel.add(leftPanel, BorderLayout.LINE_START);
            addMemberPanel.add(rightPanel, BorderLayout.CENTER);

            // HIỂN THỊ DIALOG
            int option = JOptionPane.showConfirmDialog(null, addMemberPanel, "Thêm thành viên", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (option != JOptionPane.OK_OPTION) {
                responseField.setText("Đã hủy thao tác.");
                return;
            }
            // LẤY DỮ LIỆU TỪ FORM
            String name = nameField.getText().trim();
            if (dobPicker.getDate() == null) {
                JOptionPane.showMessageDialog(null, "Vui lòng chọn ngày sinh!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                continue;
            }

            String dob = dobPicker.getDate()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String gender = (String) genderComboBox.getSelectedItem();
            String pin = new String(pinField.getPassword()).trim();
            String phone = phoneField.getText().trim();

            // VALIDATE DỮ LIỆU
            if (!isValidName(name)) {
                JOptionPane.showMessageDialog(null, "Tên mới không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                continue; // Quay lại vòng lặp nhập
            }
            if (!isValidDateOfBirth(dob)) {
                JOptionPane.showMessageDialog(null, "Ngày sinh mới không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            if (!pin.matches("\\d{6}")) {
                JOptionPane.showMessageDialog(null, "Mã PIN phải là 6 chữ số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return; // Dùng return thay vì continue trong hàm này
            }
            if (name.isEmpty() || dob.isEmpty() || phone.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Vui lòng điền đầy đủ thông tin!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Connection conn = null;
            try {
                conn = Database.connect();
                if (conn == null) throw new Exception("Không kết nối được Database!");
                conn.setAutoCommit(false);

                // --- 1. GỬI SERVER PUBLIC KEY XUỐNG THẺ (INS 0x24) ---
                // (Bước này giữ nguyên để phục vụ xác thực chữ ký lúc Reset PIN)
                KeyPair serverPair = EnvUtil.getOrGenerateServerKeyPair();
                RSAPublicKey serverPub = (RSAPublicKey) serverPair.getPublic();

                byte[] mod = serverPub.getModulus().toByteArray();
                byte[] exp = serverPub.getPublicExponent().toByteArray();
                if (mod[0] == 0 && mod.length > 128) { // Fix sign bit
                    byte[] tmp = new byte[128];
                    System.arraycopy(mod, 1, tmp, 0, 128);
                    mod = tmp;
                }

                ByteArrayOutputStream bosKey = new ByteArrayOutputStream();
                bosKey.write(mod.length);
                bosKey.write(mod);
                bosKey.write(exp.length);
                bosKey.write(exp);

                CommandAPDU cmdSetServerKey = new CommandAPDU(0x00, 0x24, 0x00, 0x00, bosKey.toByteArray());
                ResponseAPDU respSetKey = channel.transmit(cmdSetServerKey);
                if (respSetKey.getSW() != 0x9000) {
                    throw new Exception("Lỗi nạp Server Public Key. SW=" + Integer.toHexString(respSetKey.getSW()));
                }

                // --- 2. LƯU DB ĐỂ LẤY ID ---
                int newId = Database.saveMemberTransactional(conn, name, dob, gender, phone);
                if (newId == -1) throw new Exception("Lỗi khi lưu vào Database.");
                String customerCode = "CT" + String.format("%05d", newId);

                // --- 3. CHUẨN BỊ SERVER KEY (KHÓA KHÔI PHỤC) ---
                String masterKey = EnvUtil.getOrGenerateMasterKey();
                // Sinh ra 32 bytes duy nhất cho thẻ này dựa trên MasterKey
                byte[] serverKey = CryptoUtil.deriveCardKey(masterKey, customerCode);

                // --- 4. GỬI THÔNG TIN + SERVER KEY (PLAINTEXT) ---
                // Format Applet yêu cầu: PIN | Info... | ServerKey (32 bytes cuối)

                // Tạo chuỗi thông tin (Không bao gồm ServerKey vì nó là byte thô)
                String textInfo = String.join("|", pin, customerCode, name, dob, gender, phone);
                byte[] infoBytes = textInfo.getBytes(StandardCharsets.UTF_8);

                // Ghép mảng byte: [InfoBytes] + [ServerKeyBytes]
                ByteArrayOutputStream bosInfo = new ByteArrayOutputStream();
                bosInfo.write(infoBytes);
                bosInfo.write(serverKey); // 32 bytes cuối cùng

                byte[] finalData = bosInfo.toByteArray();

                // Kiểm tra độ dài (APDU thường max ~255 bytes nếu ko dùng Extended)
                if (finalData.length > 250) {
                    throw new Exception("Dữ liệu quá dài (" + finalData.length + " bytes). Hãy rút ngắn tên.");
                }

                // Gửi lệnh Init (0x22) - Giờ gửi Plaintext, thẻ sẽ tự sinh AES và mã hóa
                CommandAPDU cmdInfo = new CommandAPDU(0x00, 0x22, 0x00, 0x00, finalData);
                ResponseAPDU respInfo = channel.transmit(cmdInfo);

                if (respInfo.getSW() != 0x9000) {
                    throw new Exception("Lỗi khởi tạo thông tin. SW=" + Integer.toHexString(respInfo.getSW()));
                }

                // 5. Gửi ảnh (Nếu có - Logic này giữ nguyên, Thẻ sẽ tự mã hóa khi nhận)
                if (fileData != null) {
                    sendImageData(fileData);
                }

                // 6. Hoàn tất
                conn.commit();
                // Lưu Public Key thẻ vào DB (để verify chữ ký thẻ sau này)
                // Cần gọi lại lệnh lấy PubKey thẻ vì thẻ vừa sinh cặp khóa mới trong lệnh 0x22
                saveCardPublicKeyToDB(newId);

                JOptionPane.showMessageDialog(null, "Khởi tạo thành công! Mã: " + customerCode);

            } catch (Exception e) {
                if (conn != null) try {
                    conn.rollback();
                } catch (SQLException ex) {
                }
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Lỗi: " + e.getMessage(), "Thất bại", JOptionPane.ERROR_MESSAGE);
            } finally {
                if (conn != null) try {
                    conn.close();
                } catch (SQLException ex) {
                }
            }
        }
    }
    private void saveCardPublicKeyToDB(int dbId) throws Exception {
        ResponseAPDU respMod = channel.transmit(new CommandAPDU(0x00, 0x10, 0x01, 0x00));
        ResponseAPDU respExp = channel.transmit(new CommandAPDU(0x00, 0x10, 0x02, 0x00));

        if (respMod.getSW() == 0x9000 && respExp.getSW() == 0x9000) {
            String modHex = bytesToHex(respMod.getData());
            String expHex = bytesToHex(respExp.getData());
            Database.updatePublicKey(dbId, modHex, expHex);
        }
    }
    private boolean isValidName(String name) {
        // Regex cho tiếng Việt và tên quốc tế, chỉ cho phép chữ và khoảng trắng
        // Ví dụ: "Nguyễn Văn A", "John Doe" -> OK. "User 123", "A@B" -> Sai.
        String regex = "^[\\p{L} .'-]+$";
        return name.matches(regex) && name.length() >= 2 && name.length() <= 50;
    }
    private boolean isValidDateOfBirth(String dobStr) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        sdf.setLenient(false); // Bắt buộc đúng ngày tháng (vd: ko cho phép 30/02)
        try {
            Date dob = sdf.parse(dobStr);
            Date now = new Date();

            // Kiểm tra ngày sinh không được lớn hơn ngày hiện tại
            if (dob.after(now)) {
                return false;
            }

            // Kiểm tra tuổi hợp lý (ví dụ > 5 tuổi và < 120 tuổi)
            Calendar calDob = Calendar.getInstance();
            calDob.setTime(dob);
            Calendar calNow = Calendar.getInstance();
            int age = calNow.get(Calendar.YEAR) - calDob.get(Calendar.YEAR);
            if (age < 5 || age > 120) return false;

            return true;
        } catch (Exception e) {
            return false; // Sai định dạng
        }
    }
    // ================== ĐỌC THẺ ==================
    private void readCard() {
        if (!isConnected || channel == null) {
            responseField.setText("Bạn phải kết nối với thẻ trước!");
            return;
        }

        if (!verifyPin()) {
            return;
        } else {
            readCardData();
        }
    }

    private void readCardData() {
        infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("Thông tin thẻ"));
        infoPanel.setBackground(LIGHT_BG);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);

        imageInfoLabel = new JLabel();
        imageInfoLabel.setPreferredSize(new Dimension(100, 150));
        imageInfoLabel.setBorder(BorderFactory.createLineBorder(new Color(200,200,200)));

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        infoPanel.add(imageInfoLabel, gbc);

        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;

        int row = 1;

        // Mã KH
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(createLabel("Mã KH:"), gbc);
        getMaKH = new JTextField(); getMaKH.setEditable(false);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx=1.0;
        infoPanel.add(getMaKH, gbc);
        row++;

        // Họ tên
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(createLabel("Họ và Tên:"), gbc);
        getName = new JTextField(); getName.setEditable(false);
        gbc.gridx = 1;
        infoPanel.add(getName, gbc);
        row++;

        // Ngày sinh
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(createLabel("Ngày Sinh (dd/MM/yyyy):"), gbc);
        getDob = new JTextField(); getDob.setEditable(false);
        gbc.gridx = 1;
        infoPanel.add(getDob, gbc);
        row++;

        // Giới tính
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(createLabel("Giới Tính:"), gbc);
        getGender = new JTextField(); getGender.setEditable(false);
        gbc.gridx = 1;
        infoPanel.add(getGender, gbc);
        row++;

        // Số điện thoại - NEW
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(createLabel("Số điện thoại:"), gbc);
        getPhone = new JTextField(); getPhone.setEditable(false);
        gbc.gridx = 1;
        infoPanel.add(getPhone, gbc);
        row++;

        // Số dư
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(createLabel("Số dư (VNĐ):"), gbc);
        getBalanceField = new JTextField(); getBalanceField.setEditable(false);
        gbc.gridx = 1;
        infoPanel.add(getBalanceField, gbc);
        row++;

        // Điểm
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(createLabel("Tích điểm:"), gbc);
        getPoints = new JTextField(); getPoints.setEditable(false);
        gbc.gridx = 1;
        infoPanel.add(getPoints, gbc);
        row++;

        // Hạng
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(createLabel("Hạng thành viên:"), gbc);
        JTextField tierField = new JTextField(); tierField.setEditable(false);
        gbc.gridx = 1;
        infoPanel.add(tierField, gbc);
        row++;

        // Thời hạn hạng
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(createLabel("Thời hạn hạng còn lại:"), gbc);
        JTextField expireField = new JTextField(); expireField.setEditable(false);
        gbc.gridx = 1;
        infoPanel.add(expireField, gbc);
        row++;

        // Nút
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        btnPanel.setBackground(LIGHT_BG);
        styleSmallActionButton(changePinButton, DANGER_COLOR);
        styleSmallActionButton(editButton, WARNING_COLOR);
        btnPanel.add(changePinButton);
        btnPanel.add(editButton);

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        infoPanel.add(btnPanel, gbc);

        try {
            CommandAPDU readCommand = new CommandAPDU(0x00, 0x06, 0x00, 0x00);
            ResponseAPDU response = channel.transmit(readCommand);

            if (response.getSW() == 0x9000) {

                byte[] data = response.getData();
                int realLen = data.length;
                while (realLen > 0 && data[realLen - 1] == 0x00) realLen--;

                String rawData = new String(data, 0, realLen, StandardCharsets.UTF_8);
                String[] fields = rawData.split("\\|");

                // ===== SYNC DATABASE SAU KHI ĐỌC TỪ THẺ =====
                try {
                    boolean ok = Database.updateMemberInfoByCardCode(
                            fields[0], // maKH (CT000xx)
                            fields[1], // full_name
                            fields[2], // dob
                            fields[3], // gender
                            fields[4]  // phone
                    );

                    if (!ok) {
                        System.out.println("⚠ Không cập nhật được DB cho " + fields[0]);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                // MUST HAVE 6 FIELDS
                if (fields.length >= 6) {

                    getMaKH.setText(fields[0]);   // maKH
                    getName.setText(fields[1]);   // hoten
                    getDob.setText(fields[2]);    // ngaysinh
                    getGender.setText(fields[3]); // gioitinh
                    getPhone.setText(fields[4]);  // sdt
                    getPoints.setText(fields[5]); // sodu điểm (đổi tên nhưng đúng dữ liệu applet)

                    // Số dư tiền (tách API riêng)
                    long balance = getBalanceFromCard();
                    getBalanceField.setText(formatMoneyNoSign(balance) + " VNĐ");

                    // ===== TIER =====
                    CommandAPDU getTierCmd = new CommandAPDU(0x00, 0x14, 0x00, 0x00);
                    ResponseAPDU tierResp = channel.transmit(getTierCmd);

                    byte tierValue = tierResp.getData()[0];
                    String tierName = switch (tierValue) {
                        case 0 -> "Basic";
                        case 1 -> "Silver";
                        case 2 -> "Gold";
                        case 3 -> "Platinum";
                        case 4 -> "Diamond";
                        default -> "Unknown";
                    };
                    tierField.setText(tierName);

                    // ===== EXPIRE =====
                    CommandAPDU getExpireCmd = new CommandAPDU(0x00, 0x1B, 0x00, 0x00);
                    ResponseAPDU expireResp = channel.transmit(getExpireCmd);

                    long expireTime = 0;
                    if (expireResp.getData().length == 4) {
                        byte[] exp = expireResp.getData();
                        expireTime =
                                ((exp[0] & 0xFFL) << 24) |
                                        ((exp[1] & 0xFFL) << 16) |
                                        ((exp[2] & 0xFFL) << 8)  |
                                        (exp[3] & 0xFFL);
                    }

                    long nowSec = System.currentTimeMillis() / 1000;
                    long remainSec = expireTime - nowSec;
                    String remainText;

                    if (expireTime == 0 || tierValue == 0) {
                        remainText = "Không giới hạn / Chưa mua gói";
                    } else if (remainSec <= 0) {
                        remainText = "ĐÃ HẾT HẠN";
                    } else {
                        long days = remainSec / (24 * 3600);
                        remainText = (days <= 0) ? "< 1 ngày" : days + " ngày";
                    }
                    expireField.setText(remainText);

                    // ===== HÌNH ẢNH =====
                    getImageFile(imageInfoLabel);

                    responseField.setText("Đọc dữ liệu thẻ thành công!");
                    JOptionPane.showConfirmDialog(
                            null, infoPanel,
                            "Thông tin thẻ",
                            JOptionPane.CLOSED_OPTION,
                            JOptionPane.PLAIN_MESSAGE
                    );
                } else {
                    responseField.setText("Dữ liệu không đầy đủ hoặc sai định dạng!");
                }

            } else {
                responseField.setText("Lỗi từ thẻ: SW=" + Integer.toHexString(response.getSW()));
            }

        } catch (Exception ex) {
            responseField.setText("Lỗi đọc thẻ: " + ex.getMessage());
        }
    }


    // ================== ĐỔI PIN ==================
    private void changePin() {
        if (!isConnected || channel == null) {
            responseField.setText("Bạn phải kết nối với thẻ trước!");
            JOptionPane.showMessageDialog(null, "Bạn phải kết nối với thẻ trước!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        while (true) {
            JPanel pinPanel = new JPanel(new GridBagLayout());
            pinPanel.setBackground(LIGHT_BG);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5,5,5,5);
            gbc.anchor = GridBagConstraints.WEST;

            JPasswordField oldPinField = new JPasswordField();
            JPasswordField newPinField = new JPasswordField();
            JPasswordField confirmPinField = new JPasswordField();

            int row = 0;
            gbc.gridx=0; gbc.gridy=row; pinPanel.add(createLabel("Mã PIN cũ:"), gbc);
            gbc.gridx=1; gbc.fill=GridBagConstraints.HORIZONTAL; gbc.weightx=1.0;
            pinPanel.add(oldPinField, gbc); row++;

            gbc.gridx=0; gbc.gridy=row; gbc.fill=0; gbc.weightx=0;
            pinPanel.add(createLabel("Mã PIN mới:"), gbc);
            gbc.gridx=1; gbc.fill=GridBagConstraints.HORIZONTAL; gbc.weightx=1.0;
            pinPanel.add(newPinField, gbc); row++;

            gbc.gridx=0; gbc.gridy=row; gbc.fill=0; gbc.weightx=0;
            pinPanel.add(createLabel("Xác nhận mã PIN mới:"), gbc);
            gbc.gridx=1; gbc.fill=GridBagConstraints.HORIZONTAL; gbc.weightx=1.0;
            pinPanel.add(confirmPinField, gbc);

            int option = JOptionPane.showConfirmDialog(null, pinPanel, "Thay đổi mã PIN", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (option == JOptionPane.CANCEL_OPTION || option == JOptionPane.CLOSED_OPTION) {
                responseField.setText("Hủy thao tác thay đổi mã PIN.");
                return;
            }

            String oldPin = new String(oldPinField.getPassword()).trim();
            String newPin = new String(newPinField.getPassword()).trim();
            String confirmPin = new String(confirmPinField.getPassword()).trim();

            // ❌ KHÔNG CHO PIN MỚI TRÙNG PIN CŨ
            if (newPin.equals(oldPin)) {
                JOptionPane.showMessageDialog(
                        null,
                        "Mã PIN mới không được trùng với mã PIN cũ!",
                        "Lỗi đổi mã PIN",
                        JOptionPane.ERROR_MESSAGE
                );
                continue; // quay lại form nhập PIN
            }


            if (!oldPin.matches("\\d{6}")) {
                JOptionPane.showMessageDialog(null, "Mã PIN cũ phải là 6 chữ số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            if (!newPin.matches("\\d{6}")) {
                JOptionPane.showMessageDialog(null, "Mã PIN mới phải là 6 chữ số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            if (!newPin.equals(confirmPin)) {
                JOptionPane.showMessageDialog(null, "Mã PIN mới và xác nhận không trùng khớp.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                continue;
            }

            try {
                String changePinData = String.join("|", oldPin, newPin);
                byte[] dataBytes = changePinData.getBytes(StandardCharsets.UTF_8);

                CommandAPDU changePinCommand = new CommandAPDU(0x00, 0x04, 0x00, 0x00, dataBytes);
                ResponseAPDU response = channel.transmit(changePinCommand);

                if (response.getSW1() == 0x90 && response.getSW2() == 0x00) {
                    responseField.setText("Mã PIN đã được thay đổi thành công.");
                    JOptionPane.showMessageDialog(null, "Mã PIN đã được thay đổi thành công.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    return;
                } else {
                    String errorMessage = String.format("Lỗi khi thay đổi mã PIN. SW: %04X", response.getSW());
                    JOptionPane.showMessageDialog(null, errorMessage, "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Lỗi khi thay đổi mã PIN: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ================== ĐỔI THÔNG TIN ==================
    private void changeInfo() {
        if (!isConnected || channel == null) {
            responseField.setText("Bạn phải kết nối với thẻ trước!");
            JOptionPane.showMessageDialog(null, "Bạn phải kết nối với thẻ trước!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!verifyPin()) {
            JOptionPane.showMessageDialog(null, "Xác thực mã PIN không thành công.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        while (true) {
            final byte[][] newAvatarDataHolder = new byte[1][];
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBackground(LIGHT_BG);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5,5,5,5);
            gbc.anchor = GridBagConstraints.WEST;

            int row = 0;

            // ================= ẢNH ĐẠI DIỆN (TRÊN CÙNG – 2 CỘT) =================
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 2;
            gbc.anchor = GridBagConstraints.CENTER;

            JLabel avatarPreview = new JLabel();
            avatarPreview.setPreferredSize(new Dimension(150, 180));
            avatarPreview.setHorizontalAlignment(SwingConstants.CENTER);
            avatarPreview.setBorder(BorderFactory.createLineBorder(new Color(200,200,200)));

            // ===== LOAD ẢNH CŨ TỪ THẺ =====
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                boolean first = true;

                while (true) {
                    byte p1 = first ? (byte)0x00 : (byte)0x01;
                    CommandAPDU cmd = new CommandAPDU(0x00, 0x09, p1, 0x00);
                    ResponseAPDU resp = channel.transmit(cmd);

                    if (resp.getSW() != 0x9000 || resp.getData().length == 0) break;
                    bos.write(resp.getData());
                    first = false;
                }

                byte[] raw = bos.toByteArray();
                if (raw.length > 0) {
                    int len = raw.length;
                    while (len > 0 && raw[len - 1] == 0x00) len--;
                    byte[] img = Arrays.copyOf(raw, len);

                    ImageIcon icon = new ImageIcon(img);
                    if (icon.getIconWidth() > 0) {
                        Image scaled = icon.getImage().getScaledInstance(150, 180, Image.SCALE_SMOOTH);
                        avatarPreview.setIcon(new ImageIcon(scaled));
                    }
                }
            } catch (Exception ignore) {}


            JButton chooseAvatarBtn = new JButton("Chọn ảnh mới");
            chooseAvatarBtn.setBackground(ACCENT_PURPLE);
            chooseAvatarBtn.setForeground(Color.WHITE);

            chooseAvatarBtn.addActionListener(ev -> {
                try {
                    byte[] imgBytes = chooseAndReadFile();
                    if (imgBytes != null) {
                        newAvatarDataHolder[0] = imgBytes;

                        ImageIcon icon = new ImageIcon(imgBytes);
                        Image scaled = icon.getImage().getScaledInstance(150, 180, Image.SCALE_SMOOTH);
                        avatarPreview.setIcon(new ImageIcon(scaled));
                        avatarPreview.setText("");
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi chọn ảnh: " + ex.getMessage());
                }
            });

            JPanel avatarPanel = new JPanel(new BorderLayout(5,5));
            avatarPanel.setBackground(LIGHT_BG);
            avatarPanel.add(avatarPreview, BorderLayout.CENTER);
            avatarPanel.add(chooseAvatarBtn, BorderLayout.SOUTH);

            panel.add(avatarPanel, gbc);

            row++;               // xuống dòng
            gbc.gridwidth = 1;   // RESET gridwidth
            gbc.anchor = GridBagConstraints.WEST;

            // Họ tên
            gbc.gridx = 0; gbc.gridy = row;
            panel.add(createLabel("Họ và Tên:"), gbc);
            JTextField nameFieldNew = new JTextField(getName.getText());
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            panel.add(nameFieldNew, gbc);
            row++;

            // Ngày sinh (DatePicker)
            gbc.gridx = 0; gbc.gridy = row;
            panel.add(createLabel("Ngày Sinh:"), gbc);

            DatePicker dobPickerNew = createDatePicker(getDob.getText());
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            panel.add(dobPickerNew, gbc);
            row++;

            // Số Điện Thoại
            gbc.gridx = 0; gbc.gridy = row;
            panel.add(createLabel("Số Điện Thoại:"), gbc);
            JTextField phoneFieldNew = new JTextField(getPhone.getText());
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(phoneFieldNew, gbc);
            row++;

            // Giới tính
            gbc.gridx = 0; gbc.gridy = row;
            panel.add(createLabel("Giới Tính:"), gbc);
            JComboBox<String> genderComboBoxNew = new JComboBox<>(new String[]{"Nam", "Nữ"});
            genderComboBoxNew.setSelectedItem(getGender.getText());
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(genderComboBoxNew, gbc);

            row++; // xuống dòng mới

            // Show popup
            int option = JOptionPane.showConfirmDialog(
                    null, panel, "Thay đổi thông tin",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
            );

            if (option != JOptionPane.OK_OPTION) {
                responseField.setText("Hủy thao tác thay đổi thông tin.");
                return;
            }

            // Validate
            String name = nameFieldNew.getText().trim();
            if (dobPickerNew.getDate() == null) {
                JOptionPane.showMessageDialog(null, "Vui lòng chọn ngày sinh!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                continue;
            }

            String dob = dobPickerNew.getDate()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String phone = phoneFieldNew.getText().trim();
            String gender = (String) genderComboBoxNew.getSelectedItem();
            if (!isValidName(name)) {
                JOptionPane.showMessageDialog(null, "Tên không hợp lệ! Chỉ nhập chữ cái.", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!isValidDateOfBirth(dob)) {
                JOptionPane.showMessageDialog(null, "Ngày sinh không hợp lệ (dd/MM/yyyy) hoặc ngày tương lai!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (name.isEmpty() || dob.isEmpty() || phone.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Vui lòng nhập đầy đủ thông tin.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                continue;
            }

            if (!phone.matches("\\d{8,15}")) {
                JOptionPane.showMessageDialog(null, "Số điện thoại không hợp lệ.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                continue;
            }

            try {
                // CHUẨN: gửi 4 trường: Hoten|NgaySinh|SoDienThoai|GioiTinh
                String changeInfoData = name + "|" + dob + "|" + gender + "|" + phone;
                byte[] dataBytes = changeInfoData.getBytes(StandardCharsets.UTF_8);

                CommandAPDU changeInfoCommand = new CommandAPDU(0x00, 0x05, 0x00, 0x00, dataBytes);
                ResponseAPDU response = channel.transmit(changeInfoCommand);

                if (response.getSW() == 0x9000) {

                    // ====== NẾU CÓ ẢNH MỚI -> UPLOAD LÊN THẺ ======
                    if (newAvatarDataHolder[0] != null) {
                        sendImageData(newAvatarDataHolder[0]); // INS 0x08 theo applet
                    }

                    responseField.setText("Cập nhật thành công (bao gồm ảnh nếu có).");
                    JOptionPane.showMessageDialog(null,
                            "Cập nhật thành công!",
                            "Thành công",
                            JOptionPane.INFORMATION_MESSAGE);

                    readCard(); // đọc lại để cập nhật UI
                    return;
                }
                else {
                    JOptionPane.showMessageDialog(null,
                            "Lỗi khi thay đổi thông tin. SW=" + Integer.toHexString(response.getSW()),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }

            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Lỗi khi thay đổi thông tin: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ================== BALANCE / POINT / TIER / VOUCHER ==================
    private long getBalanceFromCard() throws CardException {
        CommandAPDU cmd = new CommandAPDU(0x00, 0x17, 0x00, 0x00);
        ResponseAPDU resp = channel.transmit(cmd);
        if (resp.getSW() != 0x9000) throw new CardException("Get balance failed");
        String s = new String(resp.getData(), StandardCharsets.UTF_8).trim();
        if (s.isEmpty()) return 0;
        return Long.parseLong(s);
    }

    private void setBalanceToCard(long value) throws CardException {
        setBalanceToCard(value, 0x02);
    }

    private void setBalanceToCard(long value, int logType) throws CardException {
        String balanceStr = String.valueOf(value);
        byte[] balanceBytes = balanceStr.getBytes(StandardCharsets.UTF_8);

        long nowSec = System.currentTimeMillis() / 1000L;
        byte[] ts = new byte[]{
                (byte)((nowSec >> 24) & 0xFF),
                (byte)((nowSec >> 16) & 0xFF),
                (byte)((nowSec >> 8) & 0xFF),
                (byte)(nowSec & 0xFF)
        };

        byte[] data = new byte[balanceBytes.length + 1 + 4];
        System.arraycopy(balanceBytes, 0, data, 0, balanceBytes.length);
        data[balanceBytes.length] = (byte)0x7C;
        System.arraycopy(ts, 0, data, balanceBytes.length + 1, 4);

        CommandAPDU cmd = new CommandAPDU(0x00, 0x16, logType & 0xFF, 0x00, data);
        ResponseAPDU resp = channel.transmit(cmd);
        if (resp.getSW() != 0x9000) throw new CardException("Set balance failed");
    }

    private int getPointsFromCard() throws CardException {
        CommandAPDU cmd = new CommandAPDU(0x00, 0x13, 0x00, 0x00);
        ResponseAPDU resp = channel.transmit(cmd);
        if (resp.getSW() != 0x9000) throw new CardException("Get points failed");
        String s = new String(resp.getData(), StandardCharsets.UTF_8).trim();
        if (s.isEmpty()) return 0;
        return Integer.parseInt(s);
    }

    private void setPointsToCard(int value) throws CardException {
        byte[] data = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        CommandAPDU cmd = new CommandAPDU(0x00, 0x12, 0x00, 0x00, data);
        ResponseAPDU resp = channel.transmit(cmd);
        if (resp.getSW() != 0x9000) throw new CardException("Set points failed");
    }

    private int getTierFromCard() throws CardException {
        CommandAPDU cmd = new CommandAPDU(0x00, 0x14, 0x00, 0x00);
        ResponseAPDU resp = channel.transmit(cmd);
        if (resp.getSW() != 0x9000) throw new CardException("Get tier failed");
        return resp.getData()[0];
    }

    private void setTierOnCard(int tier) throws CardException {
        CommandAPDU cmd = new CommandAPDU(0x00, 0x1A, (byte) tier, 0x00);
        ResponseAPDU resp = channel.transmit(cmd);
        if (resp.getSW() != 0x9000) throw new CardException("Set tier failed");
    }

    private int getVoucherLevel() throws CardException {
        CommandAPDU cmd = new CommandAPDU(0x00, 0x19, 0x00, 0x00);
        ResponseAPDU resp = channel.transmit(cmd);
        if (resp.getSW() != 0x9000) throw new CardException("Get voucher failed");
        return resp.getData()[0] & 0xFF;
    }

    private void setVoucherLevel(int level) throws CardException {
        CommandAPDU cmd = new CommandAPDU(0x00, 0x18, level, 0x00);
        ResponseAPDU resp = channel.transmit(cmd);
        if (resp.getSW() != 0x9000) throw new CardException("Set voucher failed");
    }

    // ================== NẠP TIỀN – DIALOG MỚI ==================
    private void topUpMoney() {
        if (!isConnected || channel == null) {
            responseField.setText("Bạn phải kết nối với thẻ trước!");
            return;
        }

        // ===== PANEL CHÍNH =====
        JPanel panel = new JPanel(new BorderLayout(10, 15));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Chọn số tiền nạp");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(PRIMARY_PURPLE);
        panel.add(title, BorderLayout.NORTH);

        // ===== GRID CÁC BLOCK NẠP NHANH =====
        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));
        grid.setBackground(LIGHT_BG);

        long[] quickAmounts = {
                100_000L,
                200_000L,
                500_000L,
                1_000_000L
        };

        Color[] colors = {
                ACCENT_PURPLE,
                PRIMARY_PURPLE,
                SUCCESS_COLOR,
                new Color(52, 152, 219)
        };

        JTextField inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210,210,210)),
                BorderFactory.createEmptyBorder(6,8,6,8)
        ));

        for (int i = 0; i < quickAmounts.length; i++) {
            long amount = quickAmounts[i];

            JPanel card = createSelectCard(
                    formatMoneyNoSign(amount) + " VNĐ",
                    "Nạp nhanh",
                    colors[i % colors.length]
            );

            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    inputField.setText(String.valueOf(amount));
                }
            });

            grid.add(card);
        }

        panel.add(grid, BorderLayout.CENTER);

        // ===== PANEL NHẬP TAY =====
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBackground(LIGHT_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(createLabel("Nhập số tiền khác (VNĐ):"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        inputPanel.add(inputField, gbc);

        panel.add(inputPanel, BorderLayout.SOUTH);

        // ===== HIỂN THỊ POPUP =====
        int opt = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Nạp tiền",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (opt != JOptionPane.OK_OPTION) {
            responseField.setText("Đã hủy nạp tiền.");
            return;
        }

        String input = inputField.getText().trim();
        if (input.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập số tiền!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            long amount = Long.parseLong(input);
            if (amount <= 0) {
                JOptionPane.showMessageDialog(this, "Số tiền phải lớn hơn 0!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!verifyPin()) {
                return;
            }
            long current = getBalanceFromCard();
            long updated = current + amount;

            // LOG_TOPUP = 0x02 (giữ logic cũ)
            setBalanceToCard(updated, 0x02);

            responseField.setText("Nạp tiền thành công: +" +
                    formatMoneyNoSign(amount) +
                    " VNĐ | Số dư mới: " +
                    formatMoneyNoSign(updated) + " VNĐ");

            JOptionPane.showMessageDialog(
                    this,
                    "Nạp tiền thành công!\nSố dư mới: " + formatMoneyNoSign(updated) + " VNĐ",
                    "Thành công",
                    JOptionPane.INFORMATION_MESSAGE
            );

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Số tiền không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        } catch (CardException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi thẻ: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ================== CỬA HÀNG ==================
    // ==== CỬA HÀNG DẠNG Ô VUÔNG NHIỀU MÀU ====
    private void openStore() {
        if (!isConnected || channel == null) {
            responseField.setText("Bạn phải kết nối với thẻ trước!");
            return;
        }

        // Panel chính
        JPanel mainPanel = new JPanel(new BorderLayout(10, 15));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Chọn sản phẩm muốn mua");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(PRIMARY_PURPLE);
        mainPanel.add(title, BorderLayout.NORTH);

        // Grid 3x2 cho 6 sản phẩm
        JPanel grid = new JPanel(new GridLayout(3, 2, 12, 12));
        grid.setBackground(LIGHT_BG);

        // Mỗi ô 1 màu giống dãy CHỨC NĂNG
        Color[] colors = new Color[]{
                ACCENT_PURPLE,          // Áo thun
                PRIMARY_PURPLE,         // Quần jean
                SUCCESS_COLOR,          // Thắt lưng
                new Color(52,152,219),  // Mũ
                new Color(230,126,34),  // Găng tay
                new Color(41,128,185)   // Giày
        };

        Map<Product, JSpinner> spinnerMap = new LinkedHashMap<>();

        for (int i = 0; i < products.length; i++) {
            Product p = products[i];

            JPanel card = new JPanel(new BorderLayout(5,5));
            card.setBackground(colors[i % colors.length]);
            card.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));

            JLabel nameLabel = new JLabel(p.name);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            nameLabel.setForeground(Color.WHITE);

            JLabel priceLabel = new JLabel(formatPrice(p.price));
            priceLabel.setForeground(Color.WHITE);

            // Spinner số lượng
            JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
            qtySpinner.setPreferredSize(new Dimension(60, 25));

            spinnerMap.put(p, qtySpinner);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            bottom.setOpaque(false);
            bottom.add(new JLabel("SL:"));
            bottom.add(qtySpinner);

            card.add(nameLabel, BorderLayout.NORTH);
            card.add(priceLabel, BorderLayout.CENTER);
            card.add(bottom, BorderLayout.SOUTH);

            grid.add(card);
        }

        mainPanel.add(grid, BorderLayout.CENTER);

        int option = JOptionPane.showConfirmDialog(
                this,
                mainPanel,
                "Cửa hàng",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        List<CartItem> cart = new ArrayList<>();

        for (Map.Entry<Product, JSpinner> e : spinnerMap.entrySet()) {
            int qty = (int) e.getValue().getValue();
            if (qty > 0) {
                cart.add(new CartItem(e.getKey(), qty));
            }
        }

        if (cart.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Bạn chưa chọn sản phẩm nào!",
                    "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (option != JOptionPane.OK_OPTION) {
            responseField.setText("Đã đóng cửa hàng.");
            return;
        }

        try {
            if (!verifyPin()) {
                return;
            } else {
                handlePurchase(cart);
            }
        } catch (CardException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi thẻ: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

//    private void handlePurchase(List<CartItem> cart) throws CardException {
//
//        long balance = getBalanceFromCard();
//        int tier = getTierFromCard();
//        int voucherLv = getVoucherLevel();
//
//        // ===== TÍNH TỔNG GỐC =====
//        long totalRaw = 0;
//        for (CartItem item : cart) {
//            totalRaw += item.product.price * item.quantity;
//        }
//
//        // ===== GIẢM THEO TIER =====
//        double tierDiscount = Math.min(tier * 0.05, 0.20);
//
//        // ===== GIẢM THEO VOUCHER =====
//        double voucherDiscount = switch (voucherLv) {
//            case 1 -> 0.10;
//            case 2 -> 0.15;
//            case 3 -> 0.20;
//            case 4 -> 0.25;
//            case 5 -> 0.30;
//            default -> 0.0;
//        };
//
//        double totalDiscount = Math.min(tierDiscount + voucherDiscount, 0.7);
//        long finalPrice = Math.round(totalRaw * (1.0 - totalDiscount));
//
//        // ===== BILL =====
//        StringBuilder bill = new StringBuilder("Chi tiết mua hàng:\n");
//
//        for (CartItem item : cart) {
//            bill.append("- ")
//                    .append(item.product.name)
//                    .append(" x")
//                    .append(item.quantity)
//                    .append(" = ")
//                    .append(formatPrice(item.product.price * item.quantity))
//                    .append("\n");
//        }
//
//        bill.append("\nTổng gốc: ").append(formatPrice(totalRaw))
//                .append("\nGiảm giá: ").append((int)(totalDiscount * 100)).append("%")
//                .append("\nThanh toán: ").append(formatPrice(finalPrice))
//                .append("\nSố dư hiện tại: ").append(formatPrice(balance))
//                .append("\n\nXác nhận mua?");
//
//        int confirm = JOptionPane.showConfirmDialog(
//                this,
//                bill.toString(),
//                "Xác nhận mua hàng",
//                JOptionPane.OK_CANCEL_OPTION
//        );
//
//        if (confirm != JOptionPane.OK_OPTION) return;
//
//        if (balance < finalPrice) {
//            JOptionPane.showMessageDialog(this, "Không đủ tiền!", "Lỗi", JOptionPane.ERROR_MESSAGE);
//            return;
//        }
//
//        // ===== TRỪ TIỀN =====
//        setBalanceToCard(balance - finalPrice, 0x03);
//
//        // ===== +50 ĐIỂM / 1 LẦN MUA =====
//        int newPoints = getPointsFromCard() + 50;
//        setPointsToCard(newPoints);
//
//        // ===== XÓA VOUCHER =====
//        if (voucherLv > 0) setVoucherLevel(0);
//
//        JOptionPane.showMessageDialog(this, "Mua hàng thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
//    }

    private void handlePurchase(List<CartItem> cart) throws CardException {

        long balance = getBalanceFromCard();
        int tier = getTierFromCard();
        int voucherLv = getVoucherLevel();

        // ===== 1) TÍNH TỔNG GỐC =====
        long totalRaw = 0;
        for (CartItem item : cart) {
            totalRaw += item.product.price * item.quantity;
        }

        // ===== 2) GIẢM THEO TIER =====
        double tierDiscount = Math.min(tier * 0.05, 0.20); // 0..20%
        long afterTierPrice = Math.round(totalRaw * (1.0 - tierDiscount));

        // ===== 3) VOUCHER DISCOUNT THEO LEVEL =====
        double voucherDiscount = switch (voucherLv) {
            case 1 -> 0.10;
            case 2 -> 0.15;
            case 3 -> 0.20;
            case 4 -> 0.25;
            case 5 -> 0.30;
            default -> 0.0;
        };

        // ===== 4) CHECKBOX: DÙNG VOUCHER HAY KHÔNG =====
        boolean hasVoucher = voucherLv > 0;
        JCheckBox useVoucherCheckbox = new JCheckBox(
                hasVoucher ? ("Dùng voucher hiện có (" + (int)(voucherDiscount * 100) + "%)") : "Không có voucher"
        );
        useVoucherCheckbox.setSelected(true);
        useVoucherCheckbox.setEnabled(hasVoucher);

        // Nếu user không tick -> voucherDiscount = 0 và GIỮ voucher lại
        boolean willUseVoucher;

        // ===== 5) TÍNH GIÁ CUỐI ĐÚNG NGHIỆP VỤ =====
        // (Giảm hạng trước) -> (giảm voucher sau)
        long finalPriceIfUseVoucher = Math.round(afterTierPrice * (1.0 - voucherDiscount));
        long finalPriceIfNoVoucher  = afterTierPrice;

        // ===== 6) BILL CHI TIẾT =====
        StringBuilder bill = new StringBuilder("Chi tiết mua hàng:\n");
        for (CartItem item : cart) {
            bill.append("- ")
                    .append(item.product.name)
                    .append(" x")
                    .append(item.quantity)
                    .append(" = ")
                    .append(formatPrice(item.product.price * item.quantity))
                    .append("\n");
        }

        long tierSaved = totalRaw - afterTierPrice;
        long voucherSaved = finalPriceIfNoVoucher - finalPriceIfUseVoucher;

        bill.append("\nTổng gốc: ").append(formatPrice(totalRaw))
                .append("\nGiảm theo hạng: ").append((int)(tierDiscount * 100)).append("%")
                .append(" (tiết kiệm ").append(formatPrice(tierSaved)).append(")")
                .append("\nGiá sau giảm hạng: ").append(formatPrice(afterTierPrice));

        if (hasVoucher) {
            bill.append("\nVoucher hiện có: ").append((int)(voucherDiscount * 100)).append("%")
                    .append(" (nếu dùng tiết kiệm ").append(formatPrice(voucherSaved)).append(")");
        } else {
            bill.append("\nVoucher: Không có");
        }

        bill.append("\nSố dư hiện tại: ").append(formatPrice(balance));

        // Panel confirm đẹp + checkbox
        JPanel confirmPanel = new JPanel(new BorderLayout(10, 10));
        JTextArea area = new JTextArea(bill.toString());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(420, 280));

        confirmPanel.add(sp, BorderLayout.CENTER);
        confirmPanel.add(useVoucherCheckbox, BorderLayout.SOUTH);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                confirmPanel,
                "Xác nhận mua hàng",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (confirm != JOptionPane.OK_OPTION) return;

        willUseVoucher = hasVoucher && useVoucherCheckbox.isSelected();

        long finalPrice = willUseVoucher ? finalPriceIfUseVoucher : finalPriceIfNoVoucher;

        // ===== 7) KIỂM TRA TIỀN =====
        if (balance < finalPrice) {
            JOptionPane.showMessageDialog(this, "Không đủ tiền!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // ===== 8) TRỪ TIỀN (LOG MUA HÀNG = 0x03) =====
        setBalanceToCard(balance - finalPrice, 0x03);

        // ===== 9) +50 ĐIỂM / 1 LẦN MUA =====
        int newPoints = getPointsFromCard() + 50;
        setPointsToCard(newPoints);

        // ===== 10) XỬ LÝ VOUCHER SAU MUA =====
        // Nếu user CHỌN dùng voucher -> voucher bị xóa
        // Nếu user KHÔNG dùng -> giữ voucher cho lần sau
        if (willUseVoucher && voucherLv > 0) {
            setVoucherLevel(0);
        }

        JOptionPane.showMessageDialog(
                this,
                "Mua hàng thành công!\nThanh toán: " + formatPrice(finalPrice)
                        + (willUseVoucher ? "\nVoucher đã được sử dụng." : (hasVoucher ? "\nBạn đã không dùng voucher (voucher vẫn còn)." : "")),
                "Thành công",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    // ================== ĐỔI ĐIỂM LẤY VOUCHER (UI Ô VUÔNG) ==================
    private void exchangePoints() {
        if (!isConnected || channel == null) {
            responseField.setText("Bạn phải kết nối với thẻ trước!");
            return;
        }

        // Tên, điểm, level voucher
        String[] saleOptions = {
                "Voucher giảm 10%",
                "Voucher giảm 15%",
                "Voucher giảm 20%",
                "Voucher giảm 25%",
                "Voucher giảm 30%"
        };

        int[] costPoints = {100, 200, 300, 500, 1000};
        int[] voucherLevels = {1, 2, 3, 4, 5};

        // Panel chính
        JPanel mainPanel = new JPanel(new BorderLayout(10, 15));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Chọn voucher muốn đổi");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(PRIMARY_PURPLE);
        // ===== HIỂN THỊ VOUCHER + ĐIỂM HIỆN TẠI =====
        try {
            int currentVoucherLv = getVoucherLevel();
            int currentPoints = getPointsFromCard();

            String voucherText;
            if (currentVoucherLv == 0) {
                voucherText = "Voucher hiện tại: Chưa có";
            } else {
                int percent = switch (currentVoucherLv) {
                    case 1 -> 10;
                    case 2 -> 15;
                    case 3 -> 20;
                    case 4 -> 25;
                    case 5 -> 30;
                    default -> 0;
                };
                voucherText = "Voucher hiện tại: " + percent + "%";
            }

            JLabel currentInfo = new JLabel(voucherText + " | Điểm hiện có: " + currentPoints);
            currentInfo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            currentInfo.setForeground(TEXT_DARK);

            JPanel northPanel = new JPanel(new BorderLayout(0, 6));
            northPanel.setOpaque(false);
            northPanel.add(title, BorderLayout.NORTH);
            northPanel.add(currentInfo, BorderLayout.SOUTH);

            mainPanel.add(northPanel, BorderLayout.NORTH);

        } catch (Exception e) {
            // fallback nếu lỗi thẻ
            mainPanel.add(title, BorderLayout.NORTH);
        }


        // Grid 3x2 card
        JPanel grid = new JPanel(new GridLayout(3, 2, 12, 12));
        grid.setBackground(LIGHT_BG);

        // Mỗi ô 1 màu
        Color[] colors = new Color[]{
                ACCENT_PURPLE,                 // 10%
                PRIMARY_PURPLE,                // 15%
                SUCCESS_COLOR,                 // 20%
                new Color(52, 152, 219),       // 25%
                new Color(230, 126, 34)        // 30%
        };

        final JPanel[] cards = new JPanel[saleOptions.length];
        final int[] selected = {-1};

        for (int i = 0; i < saleOptions.length; i++) {
            String titleText = saleOptions[i];
            String subText = "(" + costPoints[i] + " điểm)";

            JPanel card = createSelectCard(titleText, subText, colors[i % colors.length]);
            int index = i;

            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    selected[0] = index;
                    for (int j = 0; j < cards.length; j++) {
                        setCardSelected(cards[j], j == index);
                    }
                }
            });

            cards[i] = card;
            grid.add(card);
        }

        mainPanel.add(grid, BorderLayout.CENTER);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                mainPanel,
                "Đổi điểm",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (confirm != JOptionPane.OK_OPTION) {
            responseField.setText("Đã hủy đổi điểm.");
            return;
        }

        if (selected[0] < 0) {
            JOptionPane.showMessageDialog(this, "Bạn chưa chọn voucher!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int idx = selected[0];


        int targetCost = costPoints[idx];
        int targetLevel = voucherLevels[idx];

        if (!verifyPin()) return;

        try {
            int currentVoucherLv = getVoucherLevel();
            int currentVoucherCost = 0;

            if (currentVoucherLv >= 1 && currentVoucherLv <= 5) {
                currentVoucherCost = costPoints[currentVoucherLv - 1];
            }

            // ❌ Không cho đổi cùng level
            if (currentVoucherLv == targetLevel) {
                JOptionPane.showMessageDialog(
                        this,
                        "Bạn đang có đúng voucher này rồi.",
                        "Thông báo",
                        JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            // ❌ Không cho đổi xuống thấp hơn
            if (currentVoucherLv > targetLevel) {
                JOptionPane.showMessageDialog(
                        this,
                        "Bạn đang có voucher cao hơn.\nKhông thể đổi xuống thấp hơn.",
                        "Không hợp lệ",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            // ✅ CHỈ TRỪ PHẦN CHÊNH LỆCH
            int deltaCost = targetCost - currentVoucherCost;
            if (deltaCost < 0) deltaCost = 0;

            int currentPoints = getPointsFromCard();

            if (currentPoints < deltaCost) {
                JOptionPane.showMessageDialog(
                        this,
                        "Điểm của bạn không đủ (" + currentPoints + " điểm).\n" +
                                "Cần thêm " + deltaCost + " điểm để nâng voucher.",
                        "Không đủ điểm",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            setPointsToCard(currentPoints - deltaCost);
            setVoucherLevel(targetLevel);

            responseField.setText("Nâng voucher thành công! Điểm còn lại: " + (currentPoints - deltaCost));

            JOptionPane.showMessageDialog(
                    this,
                    "Bạn đã " + (currentVoucherLv == 0 ? "đổi" : "nâng") + " lên " + saleOptions[idx] +
                            "\nĐiểm bị trừ: " + deltaCost +
                            "\nĐiểm còn lại: " + (currentPoints - deltaCost),
                    "Thành công",
                    JOptionPane.INFORMATION_MESSAGE
            );

            readCardData();

        } catch (Exception e) {
            responseField.setText("Lỗi đổi điểm: " + e.getMessage());
        }
    }

    // ================== NÂNG HẠNG ==================
    // ==== NÂNG HẠNG DẠNG Ô VUÔNG ====
    private void openUpgradeShop() {
        if (!isConnected || channel == null) {
            responseField.setText("Bạn phải kết nối với thẻ trước!");
            return;
        }

        JPanel mainPanel = new JPanel(new BorderLayout(10, 15));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Chọn gói nâng hạng");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(PRIMARY_PURPLE);
        mainPanel.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));
        grid.setBackground(LIGHT_BG);

        // 4 màu khác nhau
        Color[] colors = new Color[]{
                ACCENT_PURPLE,                 // Bạc
                new Color(241,196,15),         // Vàng
                new Color(155,89,182),         // Bạch kim
                new Color(52,152,219)          // Kim cương
        };

        final JPanel[] cards = new JPanel[tierPacks.length];
        final int[] selectedIndex = {-1};

        for (int i = 0; i < tierPacks.length; i++) {
            TierPack pack = tierPacks[i];
            String titleText = pack.name;
            String priceText = formatPrice(pack.price);

            JPanel card = createSelectCard(titleText, priceText, colors[i % colors.length]);
            int index = i;

            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    selectedIndex[0] = index;
                    for (int j = 0; j < cards.length; j++) {
                        setCardSelected(cards[j], j == index);
                    }
                }
            });

            cards[i] = card;
            grid.add(card);
        }

        mainPanel.add(grid, BorderLayout.CENTER);

        int opt = JOptionPane.showConfirmDialog(
                this,
                mainPanel,
                "Nâng hạng",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (opt != JOptionPane.OK_OPTION) {
            responseField.setText("Hủy nâng hạng.");
            return;
        }

        if (selectedIndex[0] < 0) {
            JOptionPane.showMessageDialog(this, "Bạn chưa chọn gói!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int idx = selectedIndex[0];
        if (!verifyPin()) {
            return;
        }
        try {

            TierPack pack = tierPacks[idx];
            int currentTier = getTierFromCard();
            long balance = getBalanceFromCard();

            if (currentTier >= pack.tier) {
                JOptionPane.showMessageDialog(this,
                        "Bạn đang ở hạng " + currentTier + " rồi.\nKhông được mua gói thấp hơn hoặc bằng.",
                        "Không hợp lệ",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (balance < pack.price) {
                JOptionPane.showMessageDialog(this, "Không đủ tiền để nâng hạng!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Nâng từ hạng " + currentTier + " lên " + pack.name +
                            "\nGiá: " + pack.price +
                            "\nSố dư hiện tại: " + balance +
                            "\n\nXác nhận?",
                    "Xác nhận nâng hạng",
                    JOptionPane.OK_CANCEL_OPTION
            );

            if (confirm != JOptionPane.OK_OPTION) return;

            long newBalance = balance - pack.price;
            // LOG_UPGRADE
            setBalanceToCard(newBalance, 0x05);

            // tính hết hạn 30 ngày
            long now = System.currentTimeMillis() / 1000;
            long expire = now + 30L * 24 * 60 * 60;

            byte[] expiryBytes = new byte[]{
                    (byte) ((expire >> 24) & 0xFF),
                    (byte) ((expire >> 16) & 0xFF),
                    (byte) ((expire >> 8) & 0xFF),
                    (byte) (expire & 0xFF)
            };

            CommandAPDU setTierCmd = new CommandAPDU(
                    0x00,
                    0x1A,
                    pack.tier,
                    0x00,
                    expiryBytes
            );

            ResponseAPDU respTier = channel.transmit(setTierCmd);

            if (respTier.getSW() != 0x9000) {
                JOptionPane.showMessageDialog(this,
                        "Lỗi ghi tier hoặc thời hạn! SW=" + Integer.toHexString(respTier.getSW()),
                        "Lỗi",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            responseField.setText("Nâng hạng thành công! Hạng mới: " + pack.tier +
                    ", số dư: " + newBalance);
            JOptionPane.showMessageDialog(
                    this,
                    "Nâng hạng thành công!",
                    "Thành công",
                    JOptionPane.INFORMATION_MESSAGE
            );

        } catch (CardException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi thẻ: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void forgotPin() {
        if (!isConnected || channel == null) {
            JOptionPane.showMessageDialog(null, "Chưa kết nối thẻ!");
            return;
        }

        try {
            // --- 1. LẤY ID TỪ THẺ (INS 0x26) ---
            CommandAPDU getIDCmd = new CommandAPDU(0x00, 0x26, 0x00, 0x00);
            ResponseAPDU respID = channel.transmit(getIDCmd);

            if (respID.getSW() != 0x9000) {
                JOptionPane.showMessageDialog(null, "Lỗi đọc ID thẻ (Thẻ có thể chưa khởi tạo)!");
                return;
            }

            // Parse chuỗi ID (Ví dụ: "CT00005")
            byte[] idBytes = respID.getData();
            int realLen = idBytes.length;
            while(realLen > 0 && idBytes[realLen-1] == 0) realLen--;
            String cardCode = new String(idBytes, 0, realLen, StandardCharsets.UTF_8);

            // --- 2. NHẬP SỐ ĐIỆN THOẠI XÁC THỰC ---
            String inputPhone = JOptionPane.showInputDialog("Nhập số điện thoại đăng ký cho mã thẻ " + cardCode + ":");
            if (inputPhone == null || inputPhone.trim().isEmpty()) return;

            // --- 3. KIỂM TRA DATABASE (MỚI) ---
            int dbId = -1;
            try {
                // Cắt bỏ tiền tố "CT" để lấy ID số (CT00005 -> 5)
                if (cardCode.startsWith("CT")) {
                    dbId = Integer.parseInt(cardCode.substring(2));
                } else {
                    // Fallback nếu format khác
                    dbId = Integer.parseInt(cardCode);
                }
            } catch (NumberFormatException e) {
                System.out.println("Lỗi parse ID từ chuỗi: " + cardCode);
                JOptionPane.showMessageDialog(null, "Định dạng mã thẻ không hợp lệ để tra cứu DB!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Gọi hàm check trong Database
            boolean isMatch = Database.checkPhoneMatch(dbId, inputPhone);

            if (!isMatch) {
                JOptionPane.showMessageDialog(null, "Số điện thoại không chính xác! Từ chối Reset PIN.", "Xác thực thất bại", JOptionPane.ERROR_MESSAGE);
                return; // Dừng lại ngay nếu không khớp
            }

            // --- 4. NẾU KHỚP -> TIẾN HÀNH RESET (LOGIC CŨ) ---

            // Tái tạo Server Key (Khóa khôi phục)
            String masterKey = EnvUtil.getOrGenerateMasterKey();
            // Đây chính là key 32 bytes mà thẻ đã lưu Hash lúc Init
            byte[] serverKey = CryptoUtil.deriveCardKey(masterKey, cardCode);

            // Ký số lên Server Key (Server Authentication)
            KeyPair serverPair = EnvUtil.getOrGenerateServerKeyPair();
            RSAPrivateKey serverPri = (RSAPrivateKey) serverPair.getPrivate();

            java.security.Signature sig = java.security.Signature.getInstance("SHA1withRSA");
            sig.initSign(serverPri);
            sig.update(serverKey);
            byte[] signature = sig.sign(); // 128 bytes

            // Đóng gói: [Signature] + [ServerKey]
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(signature);
            bos.write(serverKey);
            byte[] payload = bos.toByteArray();

            // Gửi lệnh Reset (0x20)
            CommandAPDU resetCmd = new CommandAPDU(0x00, 0x20, 0x00, 0x00, payload);
            ResponseAPDU respReset = channel.transmit(resetCmd);

            if (respReset.getSW() == 0x9000) {
                JOptionPane.showMessageDialog(null, "Reset PIN thành công!\nMã PIN mới là: 000000.\nVui lòng đổi PIN ngay lập tức.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                // Tự động mở form đổi PIN để người dùng đổi luôn cho an toàn
                changePin();
            } else {
                JOptionPane.showMessageDialog(null, "Lỗi Reset từ thẻ: " + Integer.toHexString(respReset.getSW()), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Lỗi hệ thống: " + e.getMessage());
        }
    }

    // ================== CHỌN & GỬI ẢNH ==================

    private byte[] chooseAndReadFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Chọn ảnh đại diện");
        fileChooser.setFileFilter(
                new javax.swing.filechooser.FileNameExtensionFilter(
                        "Image files", "jpg", "jpeg", "png", "gif", "bmp"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            try {
                // 1. Đọc file ảnh gốc vào BufferedImage
                BufferedImage originalImage = ImageIO.read(selectedFile);

                if (originalImage == null) {
                    JOptionPane.showMessageDialog(this, "Không đọc được dữ liệu ảnh.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return null;
                }

                // 2. Xử lý nén ảnh xuống dưới 10KB (10240 bytes)
                // Ta sẽ thử resize về kích thước passport chuẩn (ví dụ tối đa 150px chiều rộng) trước
                byte[] compressedData = resizeAndCompress(originalImage, 10240);

                if (compressedData == null) {
                    JOptionPane.showMessageDialog(this, "Không thể nén ảnh xuống dung lượng yêu cầu.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return null;
                }

                // --- CẬP NHẬT GIAO DIỆN ---
                // Hiển thị chính cái ảnh đã nén để người dùng biết hình trên thẻ trông thế nào
                int w = imageLabel.getWidth();
                int h = imageLabel.getHeight();
                if (w <= 0 || h <= 0) { w = 120; h = 160; } // Default size

                // Tạo ImageIcon từ byte array đã nén
                ImageIcon icon = new ImageIcon(compressedData);
                // Scale icon để vừa với Label hiển thị (chỉ để đẹp mắt trên app)
                Image scaledVisual = icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);

                imageLabel.setIcon(new ImageIcon(scaledVisual));
                imageLabel.revalidate();
                imageLabel.repaint();

                // 3. Trả về dữ liệu đã nén để gửi xuống thẻ
                System.out.println("Size ảnh gốc: " + selectedFile.length() + " bytes");
                System.out.println("Size ảnh sau nén: " + compressedData.length + " bytes");

                return compressedData;

            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Lỗi khi xử lý file: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
        return null;
    }
    /**
     * Hàm hỗ trợ: Resize và nén ảnh cho đến khi nhỏ hơn maxSize bytes
     */
    private byte[] resizeAndCompress(BufferedImage original, int maxSize) throws IOException {
        // Bắt đầu với kích thước tương đối nhỏ (ví dụ max chiều rộng 200px)
        // Kích thước này thường cho ra ảnh ~5-8KB chất lượng khá
        int targetWidth = 200;
        int targetHeight = (int) ((double) original.getHeight() / original.getWidth() * targetWidth);

        byte[] result = null;

        // Vòng lặp giảm kích thước nếu ảnh vẫn quá lớn
        while (true) {
            // 1. Tạo ảnh buffer mới với kích thước target
            // Sử dụng TYPE_INT_RGB để bỏ kênh Alpha (trong suốt) vì JPG không hỗ trợ, giúp giảm dung lượng
            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
            g.dispose();

            // 2. Ghi ảnh ra mảng byte dưới dạng JPG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", baos);
            result = baos.toByteArray();

            // 3. Kiểm tra kích thước
            if (result.length < maxSize) {
                break; // Đã đạt yêu cầu
            } else {
                // Nếu vẫn lớn hơn 10KB, giảm kích thước đi 10% và thử lại
                targetWidth = (int) (targetWidth * 0.9);
                targetHeight = (int) (targetHeight * 0.9);

                // Bảo vệ: Nếu ảnh quá nhỏ mà vẫn nặng (hiếm gặp) thì dừng để tránh lỗi
                if (targetWidth < 10) break;
            }
        }

        return result;
    }
    private void sendImageData(byte[] fileData) {
        // 1. PADDING DỮ LIỆU
        byte[] paddedData = padData(fileData);

        // 2. CHIA GÓI (Dùng 240 bytes thay vì 255 để luôn chia hết cho 16)
        int maxDataLength = 240;

        try {
            for (int offset = 0; offset < paddedData.length; offset += maxDataLength) {
                int length = Math.min(maxDataLength, paddedData.length - offset);
                byte[] chunk = new byte[length];
                System.arraycopy(paddedData, offset, chunk, 0, length);

                // 3. XÁC ĐỊNH P1 (THEO LOGIC APPLET BAICUOIKY)
                // P1 = 0x00: Gói đầu tiên hoặc gói giữa
                // P1 = 0x01: Gói cuối cùng (để kích hoạt Encryption trên thẻ)
                byte p1 = (byte) 0x00;

                // Kiểm tra xem đây có phải gói cuối cùng không
                if (offset + length >= paddedData.length) {
                    p1 = (byte) 0x02;
                }

                // [Lưu ý quan trọng]: Nếu ảnh nhỏ (chỉ 1 gói), logic Applet hiện tại
                // có thể cần gửi 2 lần (1 lần 0x00 để reset, 1 lần 0x01 để encrypt).
                // Tuy nhiên với code hiện tại, ta cứ gửi đúng protocol:

                // Gửi lệnh APDU
                CommandAPDU sendImage = new CommandAPDU(0x00, 0x08, p1, 0x00, chunk);
                ResponseAPDU response = channel.transmit(sendImage);

                if (response.getSW() != 0x9000) {
                    responseField.setText("Lỗi tại khối " + offset / maxDataLength + ": SW=" + Integer.toHexString(response.getSW()));
                    return;
                }
            }
            responseField.setText("Gửi và mã hóa ảnh thành công!");
        } catch (Exception e) {
            responseField.setText("Lỗi khi gửi ảnh: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Hàm thêm byte 0 vào cuối mảng để tổng độ dài chia hết cho 16
     * (Yêu cầu bắt buộc của AES NoPadding trên thẻ)
     */
    private byte[] padData(byte[] src) {
        int remainder = src.length % 16;
        if (remainder == 0) return src; // Đã đẹp rồi, không cần padding

        int paddingNeeded = 16 - remainder;
        byte[] padded = new byte[src.length + paddingNeeded];
        System.arraycopy(src, 0, padded, 0, src.length);
        // Các byte còn lại mặc định là 0, không cần fill thủ công
        return padded;
    }
    private void getImageFile(JLabel imageInfoLabel) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            boolean isFirst = true;

            // 1. Vòng lặp lấy dữ liệu từ thẻ (Thẻ đã giải mã sẵn)
            while (true) {
                byte p1 = isFirst ? (byte)0x00 : (byte)0x01;

                CommandAPDU cmd = new CommandAPDU(0x00, 0x09, p1, 0x00);
                ResponseAPDU response = channel.transmit(cmd);

                if (response.getSW() != 0x9000) break;

                byte[] data = response.getData();
                if (data.length == 0) break;

                bos.write(data);
                isFirst = false;
            }

            byte[] rawData = bos.toByteArray();

            if (rawData.length > 0) {
                // 2. Xử lý Padding (Loại bỏ các byte 0x00 dư thừa ở cuối)
                // Vì AES NoPadding yêu cầu input chia hết cho 16, ta đã thêm 0x00 vào cuối lúc upload.
                // Giờ nhận về ta phải cắt nó đi để file ảnh hợp lệ.
                int realLen = rawData.length;
                while (realLen > 0 && rawData[realLen - 1] == 0x00) {
                    realLen--;
                }

                // Copy ra mảng byte sạch
                byte[] finalImgData = new byte[realLen];
                System.arraycopy(rawData, 0, finalImgData, 0, realLen);

                // 3. Hiển thị ảnh
                ImageIcon icon = new ImageIcon(finalImgData);
                if (icon.getIconWidth() > 0) {
                    int w = imageInfoLabel.getWidth() > 0 ? imageInfoLabel.getWidth() : 100;
                    int h = imageInfoLabel.getHeight() > 0 ? imageInfoLabel.getHeight() : 150;
                    Image img = icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    imageInfoLabel.setIcon(new ImageIcon(img));
                    imageInfoLabel.setText("");
                } else {
                    imageInfoLabel.setIcon(null);
                    imageInfoLabel.setText("Lỗi định dạng ảnh");
                }

            } else {
                imageInfoLabel.setIcon(null);
                imageInfoLabel.setText("Không có ảnh");
            }

        } catch (Exception e) {
            e.printStackTrace();
            imageInfoLabel.setText("Lỗi kết nối");
        }
    }


    // ================== RSA PUBLIC KEY & SIGN ==================

    // ================== LỊCH SỬ GIAO DỊCH ==================
    // ================== LỊCH SỬ GIAO DỊCH (THANH TÍM NHẠT) ==================
    private void viewTransactionLogs() {
        if (!isConnected || channel == null) {
            responseField.setText("Bạn phải kết nối với thẻ trước!");
            return;
        }
        if (!verifyPin()) {
            return;
        }
        try {
            List<Integer> deltas = new ArrayList<>();
            List<String> types = new ArrayList<>();
            List<Long> times = new ArrayList<>();

            // Đọc tối đa 5 log từ thẻ (giữ logic cũ)
            for (int i = 0; i < 5; i++) {
                CommandAPDU cmd = new CommandAPDU(0x00, 0x15, i, 0x00);
                ResponseAPDU resp = channel.transmit(cmd);

                if (resp.getSW() != 0x9000) break;

                byte[] raw = resp.getData();
                if (raw.length < LOG_ENTRY_SIZE) continue;

                byte type = raw[0];
                char sign = (char) raw[1];

                if (type != 0x02 && type != 0x03 && type != 0x05)
                    continue;

                String digits = new String(raw, 2, 10).replace("\u0000", "");
                digits = digits.replaceFirst("^0+(?!$)", "");
                if (digits.equals("")) digits = "0";

                int delta = Integer.parseInt(digits);
                if (sign == '-') delta = -delta;

                long t =
                        ((raw[12] & 0xFFL) << 24) |
                                ((raw[13] & 0xFFL) << 16) |
                                ((raw[14] & 0xFFL) << 8) |
                                (raw[15] & 0xFFL);

                String typeName = switch (type) {
                    case 0x02 -> "Nạp tiền";
                    case 0x03 -> "Mua hàng";
                    case 0x05 -> "Nâng hạng";
                    default -> "Khác";
                };

                deltas.add(delta);
                types.add(typeName);
                times.add(t);
            }

            if (deltas.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Không có giao dịch.", "Thông báo", JOptionPane.PLAIN_MESSAGE);
                return;
            }

            long currentBalance = getBalanceFromCard();

            // Tính số dư sau mỗi giao dịch (giữ đúng logic cũ)
            List<Long> balances = new ArrayList<>();
            long runningBalance = currentBalance;
            for (int i = 0; i < deltas.size(); i++) runningBalance -= deltas.get(i);
            for (int i = deltas.size() - 1; i >= 0; i--) {
                runningBalance += deltas.get(i);
                balances.add(runningBalance);
            }
            Collections.reverse(balances);

            // ===== UI mới: list các thanh dài =====
            JPanel mainPanel = new JPanel(new BorderLayout(10, 15));
            mainPanel.setBackground(LIGHT_BG);
            mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

            JLabel title = new JLabel("Lịch sử giao dịch");
            title.setFont(new Font("Segoe UI", Font.BOLD, 16));
            title.setForeground(PRIMARY_PURPLE);
            mainPanel.add(title, BorderLayout.NORTH);

            JPanel listPanel = new JPanel();
            listPanel.setBackground(LIGHT_BG);
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

            // ===== HEADER CỘT =====
            JPanel header = new JPanel(new GridLayout(1, 5));
            header.setBackground(PRIMARY_PURPLE);
            header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            header.add(createHeaderLabel("STT"));
            header.add(createHeaderLabel("Thời gian"));
            header.add(createHeaderLabel("Loại giao dịch"));
            header.add(createHeaderLabel("Biến động"));
            header.add(createHeaderLabel("Số dư"));

            listPanel.add(header);
            listPanel.add(Box.createVerticalStrut(6));

            Color rowColor = new Color(235, 225, 245); // tím nhạt
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");

            for (int i = 0; i < deltas.size(); i++) {
                JPanel row = new JPanel(new GridLayout(1, 5));
                row.setBackground(rowColor);
                row.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

                JLabel sttLabel = new JLabel(String.valueOf(i + 1));
                JLabel timeLabel = new JLabel(sdf.format(new Date(times.get(i) * 1000)));
                JLabel typeLabel = new JLabel(types.get(i));
                JLabel deltaLabel = new JLabel(formatMoneyDelta(deltas.get(i)));
                JLabel balanceLabel = new JLabel(formatMoneyNoSign(balances.get(i)));

                // ===== TÔ MÀU BIẾN ĐỘNG =====
                if (deltas.get(i) >= 0) {
                    deltaLabel.setForeground(SUCCESS_COLOR); // xanh
                } else {
                    deltaLabel.setForeground(DANGER_COLOR);  // đỏ
                }

                sttLabel.setHorizontalAlignment(SwingConstants.CENTER);
                timeLabel.setHorizontalAlignment(SwingConstants.CENTER);
                typeLabel.setHorizontalAlignment(SwingConstants.CENTER);
                deltaLabel.setHorizontalAlignment(SwingConstants.CENTER);
                balanceLabel.setHorizontalAlignment(SwingConstants.CENTER);

                row.add(sttLabel);
                row.add(timeLabel);
                row.add(typeLabel);
                row.add(deltaLabel);
                row.add(balanceLabel);

                listPanel.add(row);
                listPanel.add(Box.createVerticalStrut(6));
            }

            JScrollPane scroll = new JScrollPane(listPanel);
            scroll.setBorder(null);
            mainPanel.add(scroll, BorderLayout.CENTER);

            JOptionPane.showMessageDialog(
                    this,
                    mainPanel,
                    "Lịch sử giao dịch",
                    JOptionPane.PLAIN_MESSAGE
            );

        } catch (Exception e) {
            responseField.setText("Lỗi xem log: " + e.getMessage());
        }
    }
    private JLabel createHeaderLabel(String text) {
        JLabel lb = new JLabel(text);
        lb.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lb.setForeground(Color.WHITE);
        lb.setHorizontalAlignment(SwingConstants.CENTER);
        return lb;
    }
    // ================== UTIL ==================
    private String formatMoneyDelta(long n) {
        String formatted = String.format("%,d", Math.abs(n));
        return (n >= 0 ? "+" : "-") + formatted;
    }

    private String formatMoneyNoSign(long n) {
        return String.format("%,d", n);
    }

    private String formatPrice(long n) {
        return String.format("%,d VNĐ", n);
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        if (len % 2 != 0) {
            s = "0" + s;
            len = s.length();
        }
        byte[] data = new byte[len/2];
        for (int i=0;i<len;i+=2) {
            data[i/2] = (byte)((Character.digit(s.charAt(i),16)<<4)
                    + Character.digit(s.charAt(i+1),16));
        }
        return data;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b: bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length()==1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString().toUpperCase();
    }

    private void updateGUIState(boolean isCardInitialized) {
        // Nếu thẻ ĐÃ có dữ liệu (isCardInitialized = true):
        // -> Khóa nút "Khởi tạo", Mở các nút khác

        // Nếu thẻ TRẮNG (isCardInitialized = false):
        // -> Mở nút "Khởi tạo", Khóa các nút khác (trừ Unblock phòng khi cần)

        initCardButton.setEnabled(!isCardInitialized);

        // Các nút chức năng chỉ dùng được khi thẻ đã Init
        readCardButton.setEnabled(isCardInitialized);
        changePinButton.setEnabled(isCardInitialized);
        editButton.setEnabled(isCardInitialized);
        topUpButton.setEnabled(isCardInitialized);
        storeButton.setEnabled(isCardInitialized);
        upgradeTierButton.setEnabled(isCardInitialized);
        exchangePointsButton.setEnabled(isCardInitialized);
        forgotPinButton.setEnabled(isCardInitialized);

        // unblockCartButton.setEnabled(true); // Nút mở khóa có thể luôn mở hoặc tùy bạn
        verifybtn.setEnabled(isCardInitialized);
        viewLogButton.setEnabled(isCardInitialized);

        // Đổi màu nút Khởi tạo để người dùng dễ nhận biết
        if (!isCardInitialized) {
            initCardButton.setBackground(SUCCESS_COLOR); // Xanh lá - Mời gọi bấm
            initCardButton.setText("KHỞI TẠO THẺ NGAY");
        } else {
            initCardButton.setBackground(Color.GRAY);
            initCardButton.setText("Thẻ đã khởi tạo");
        }
    }
    private void checkCardStatus() {
        try {
            // Gửi lệnh 0x25 để check
            CommandAPDU cmd = new CommandAPDU(0x00, 0x25, 0x00, 0x00);
            ResponseAPDU resp = channel.transmit(cmd);

            if (resp.getSW() == 0x9000) {
                byte[] data = resp.getData();
                boolean isInit = (data[0] == 0x01);

                updateGUIState(isInit); // Cập nhật giao diện

                if (isInit) {
                    responseField.setText("Thẻ hợp lệ. Sẵn sàng giao dịch.");
                } else {
                    responseField.setText("Thẻ trắng. Vui lòng khởi tạo!");
                    JOptionPane.showMessageDialog(this, "Đây là thẻ mới. Vui lòng khởi tạo thông tin!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
