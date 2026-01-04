package com.mycompany.membershipcardgui;

import javax.swing.table.DefaultTableModel;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    // Cấu hình kết nối MySQL (XAMPP)
    private static final String URL = "jdbc:mysql://localhost:3306/card_system?useSSL=false&characterEncoding=UTF-8";
    private static final String USER = "root";
    private static final String PASS = "";

    public static Connection connect() {
        Connection conn = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(URL, USER, PASS);
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Lỗi kết nối MySQL: " + e.getMessage());
        }
        return conn;
    }

    public static void createNewTable() {
        // [CẬP NHẬT] Thêm 2 cột lưu Public Key
        String sql = "CREATE TABLE IF NOT EXISTS members ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "full_name VARCHAR(100) NOT NULL, "
                + "dob VARCHAR(20), "
                + "gender VARCHAR(10), "
              //  + "phone VARCHAR(20), "
                + "phone VARCHAR(64), " // Mở rộng độ dài để lưu số đã mã hóa SHA-256
                + "card_status VARCHAR(20) DEFAULT 'Active', "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "public_key_modulus TEXT, "   // Cột mới: Lưu RSA Modulus
                + "public_key_exponent TEXT"    // Cột mới: Lưu RSA Exponent
                + ")";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            if (conn != null) {
                stmt.execute(sql);
                System.out.println("Đã kiểm tra/tạo bảng members (bao gồm cột Public Key).");
            }
        } catch (SQLException e) {
            System.out.println("Lỗi tạo bảng: " + e.getMessage());
        }
    }

    /**
     * [MỚI] Lưu thành viên trong Transaction (Không tự đóng kết nối)
     * Hàm này được gọi từ quy trình Init Card để đảm bảo Atomicity
     */
    public static int saveMemberTransactional(Connection conn, String name, String dob, String gender, String phone) throws SQLException {
        String sql = "INSERT INTO members(full_name, dob, gender, phone, card_status) VALUES(?,?,?,?,'Active')";

        // Lưu ý: Không dùng try-with-resources cho 'conn' vì nó được truyền từ ngoài vào
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, name);
            pstmt.setString(2, dob);
            pstmt.setString(3, gender);
           // pstmt.setString(4, phone);
            // LƯU MÃ BĂM
            pstmt.setString(4, CryptoUtil.hashPhoneNumber(phone));
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1); // Trả về ID vừa sinh
                    }
                }
            }
        }
        return -1; // Lỗi
    }
    /**
     * [MỚI] Kiểm tra số điện thoại có khớp với ID thành viên không
     */
    public static boolean checkPhoneMatch(int id, String inputPhone) {
        String sql = "SELECT phone FROM members WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            //bỏ so sánh phone bản rõ
//            if (rs.next()) {
//                String storedPhone = rs.getString("phone");
//                // So sánh số điện thoại (bỏ khoảng trắng nếu có)
//                if (storedPhone != null && inputPhone != null) {
//                    return storedPhone.trim().equals(inputPhone.trim());
//                }
//            }

            if (rs.next()) {
                String storedHash = rs.getString("phone");
                // BĂM INPUT RỒI SO SÁNH
                String inputHash = CryptoUtil.hashPhoneNumber(inputPhone);
                return storedHash != null && storedHash.equals(inputHash);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false; // Không tìm thấy hoặc không khớp
    }
    /**
     * [CŨ] Lưu thành viên (Tự tạo kết nối - Dùng cho test hoặc nhập liệu thủ công)
     */
    public static int saveMember(String name, String dob, String gender, String phone) {
        // Chỉ là wrapper gọi hàm transactional với kết nối mới
        try (Connection conn = connect()) {
            return saveMemberTransactional(conn, name, dob, gender, phone);
        } catch (SQLException e) {
            System.out.println("Lỗi lưu thành viên: " + e.getMessage());
            return -1;
        }
    }

    /**
     * [MỚI] Cập nhật Public Key cho thành viên (Sau khi đọc từ thẻ thành công)
     */
    public static boolean updatePublicKey(int id, String modulus, String exponent) {
        String sql = "UPDATE members SET public_key_modulus = ?, public_key_exponent = ? WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, modulus);
            pstmt.setString(2, exponent);
            pstmt.setInt(3, id);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Lỗi lưu Public Key: " + e.getMessage());
            return false;
        }
    }

    /**
     * [MỚI] Lấy Public Key để phục vụ Reset PIN hoặc Verify Chữ ký
     * Trả về mảng String[2]: {Modulus, Exponent}
     */
    public static String[] getPublicKey(int id) {
        String sql = "SELECT public_key_modulus, public_key_exponent FROM members WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String mod = rs.getString("public_key_modulus");
                String exp = rs.getString("public_key_exponent");
                if (mod != null && exp != null) {
                    return new String[] { mod, exp };
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean updateStatus(int id, String newStatus) {
        String sql = "UPDATE members SET card_status = ? WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String[] getMemberInfo(int id) {
        String sql = "SELECT full_name, dob, gender, phone, card_status, created_at FROM members WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new String[] {
                        rs.getString("full_name"),
                        rs.getString("dob"),
                        rs.getString("gender"),
                        rs.getString("phone"),
                        rs.getString("card_status"),
                        rs.getString("created_at")
                };
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean updateMemberInfoByCardCode(
            String cardCode,
            String fullName,
            String dob,
            String gender,
            String phone
    ) {
        // CT00005 -> 5
        int id;
        try {
            if (cardCode.startsWith("CT")) {
                id = Integer.parseInt(cardCode.substring(2));
            } else {
                id = Integer.parseInt(cardCode);
            }
        } catch (Exception e) {
            System.out.println("Không parse được cardCode: " + cardCode);
            return false;
        }

        String sql = """
        UPDATE members
        SET full_name = ?, dob = ?, gender = ?, phone = ?
        WHERE id = ?
    """;

        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, fullName);
            ps.setString(2, dob);
            ps.setString(3, gender);
            //ps.setString(4, phone);

            // CẬP NHẬT MÃ BĂM MỚI
            ps.setString(4, CryptoUtil.hashPhoneNumber(phone));
            ps.setInt(5, id);

            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    // Thêm bảng lưu chi tiết giao dịch tương xứng với biến động số dư trên thẻ
    public static void createTransactionTable() {
        String sql = """
        CREATE TABLE IF NOT EXISTS transactions (
                         id INT AUTO_INCREMENT PRIMARY KEY,
                         member_id INT NOT NULL,
                         log_index INT NOT NULL,
                         balance_before BIGINT NOT NULL,
                         balance_after  BIGINT NOT NULL,
                         delta BIGINT NOT NULL,
                
                         raw_total BIGINT NOT NULL DEFAULT 0,
                         tier_discount BIGINT NOT NULL DEFAULT 0,
                         voucher_discount BIGINT NOT NULL DEFAULT 0,
                
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (member_id) REFERENCES members(id)
                     )
    """;

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);
            System.out.println("Đã kiểm tra/tạo bảng transactions");

        } catch (SQLException e) {
            System.out.println("Lỗi tạo bảng transactions: " + e.getMessage());
        }
    }

    //bảng chi tiết đơn hàng
    public static void createTransactionItemTable() {
        String sql = """
        CREATE TABLE IF NOT EXISTS transaction_items (
            id INT AUTO_INCREMENT PRIMARY KEY,
            transaction_id INT NOT NULL,
            product_name VARCHAR(255) NOT NULL,
            quantity INT NOT NULL,
            unit_price BIGINT NOT NULL,
            total_price BIGINT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (transaction_id)
                REFERENCES transactions(id)
                ON DELETE CASCADE
        )
    """;

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);
            System.out.println("Đã kiểm tra/tạo bảng transaction_items");

        } catch (SQLException e) {
            System.out.println("Lỗi tạo bảng transaction_items: " + e.getMessage());
        }
    }

    // Hàm lưu chi tiết giao dịch
    public static void insertTransactionItem(
            int transactionId,
            String productName,
            int quantity,
            int unitPrice
    ) throws SQLException {

        String sql = """
        INSERT INTO transaction_items
        (transaction_id, product_name, quantity, unit_price, total_price)
        VALUES (?, ?, ?, ?, ?)
    """;

        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, transactionId);
            ps.setString(2, productName);
            ps.setInt(3, quantity);
            ps.setInt(4, unitPrice);
            ps.setInt(5, quantity * unitPrice);

            ps.executeUpdate();
        }
    }

    public static int insertTransaction(
            int memberId,
            int logIndex,
            long balanceBefore,
            long balanceAfter,
            long delta,
            long rawTotal,
            long tierDiscount,
            long voucherDiscount
    ) throws SQLException {
        String sql = """
        INSERT INTO transactions
                    (member_id, log_index, balance_before, balance_after, delta, raw_total, tier_discount, voucher_discount)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """;

        try (Connection conn = connect();
             PreparedStatement ps =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, memberId);
            ps.setInt(2, logIndex);
            ps.setLong(3, balanceBefore);
            ps.setLong(4, balanceAfter);
            ps.setLong(5, delta);
            ps.setLong(6, rawTotal);
            ps.setLong(7, tierDiscount);
            ps.setLong(8, voucherDiscount);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    //tìm transaction gần nhất theo thời gian
    public static Integer findTransactionByTime(int memberId, long unixTime) throws SQLException {
        String sql = """
        SELECT id
        FROM transactions
        WHERE member_id = ?
        ORDER BY ABS(TIMESTAMPDIFF(SECOND, created_at, FROM_UNIXTIME(?)))
        LIMIT 1
    """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, memberId);
            ps.setLong(2, unixTime);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }
        return null;
    }

    public static Integer findTransactionByLogIndex(
            int memberId,
            int logIndex
    ) throws SQLException {

        String sql = """
        SELECT id
        FROM transactions
        WHERE member_id = ? AND log_index = ?
    """;

        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, memberId);
            ps.setInt(2, logIndex);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }
        return null;
    }

    public static void shiftLogIndexes(Connection conn, int memberId) throws SQLException {
        // Xóa các log sẽ rơi khỏi top 5
        try (PreparedStatement psDel = conn.prepareStatement(
                "DELETE FROM transactions WHERE member_id = ? AND log_index >= 4")) {
            psDel.setInt(1, memberId);
            psDel.executeUpdate();
        }

        // Đẩy log_index: 0->1, 1->2, 2->3, 3->4
        try (PreparedStatement psUp = conn.prepareStatement(
                "UPDATE transactions SET log_index = log_index + 1 WHERE member_id = ?")) {
            psUp.setInt(1, memberId);
            psUp.executeUpdate();
        }
    }


    // Thêm vào class Database

//    public static javax.swing.table.DefaultTableModel getTransactionDetails(int transactionId) {
//        // Tên cột cho bảng
//        String[] columnNames = {"Sản phẩm", "Số lượng", "Đơn giá", "Giảm hạng", "Giảm voucher", "Thành tiền"};
//        DefaultTableModel model = new DefaultTableModel(null, columnNames);
//        long rawTotal = 0;
//        long tierDiscount = 0;
//        long voucherDiscount = 0;
//        String txSql = "SELECT raw_total, tier_discount, voucher_discount FROM transactions WHERE id = ?";
//        try (Connection conn = connect();
//             PreparedStatement ps = conn.prepareStatement(txSql)) {
//
//            ps.setInt(1, transactionId);
//            ResultSet rsTx = ps.executeQuery();
//            if (rsTx.next()) {
//                rawTotal = rsTx.getLong("raw_total");
//                tierDiscount = rsTx.getLong("tier_discount");
//                voucherDiscount = rsTx.getLong("voucher_discount");
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//
//        String sql = "SELECT product_name, quantity, unit_price, total_price FROM transaction_items WHERE transaction_id = ?";
//
//        java.util.List<Object[]> rows = new java.util.ArrayList<>();
//
//        try (Connection conn = connect();
//             PreparedStatement pstmt = conn.prepareStatement(sql)) {
//
//            pstmt.setInt(1, transactionId);
//            ResultSet rs = pstmt.executeQuery();
//
//            while (rs.next()) {
//                String name = rs.getString("product_name");
//                int qty = rs.getInt("quantity");
//                long price = rs.getLong("unit_price");
//                long total = rs.getLong("total_price");
//
//                rows.add(new Object[]{name, qty, price, total});
//            }
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//
//// ===== PHÂN BỔ GIẢM GIÁ THEO TỶ LỆ =====
//        long usedTier = 0;
//        long usedVoucher = 0;
//
//        for (int i = 0; i < rows.size(); i++) {
//
//            String name = (String) rows.get(i)[0];
//            int qty = (int) rows.get(i)[1];
//            long price = (long) rows.get(i)[2];
//            long total = (long) rows.get(i)[3];
//
//            long tierPart = 0;
//            long voucherPart = 0;
//
//            if (rawTotal > 0) {
//                if (i == rows.size() - 1) {
//                    // DÒNG CUỐI: bù sai số làm tròn để tổng đúng tuyệt đối
//                    tierPart = tierDiscount - usedTier;
//                    voucherPart = voucherDiscount - usedVoucher;
//                } else {
//                    double ratio = (double) total / (double) rawTotal;
//                    tierPart = Math.round(tierDiscount * ratio);
//                    voucherPart = Math.round(voucherDiscount * ratio);
//                }
//            }
//
//            usedTier += tierPart;
//            usedVoucher += voucherPart;
//
//            String priceStr = String.format("%,d VNĐ", price);
//            String totalStr = String.format("%,d VNĐ", total);
//            String tierStr = String.format("%,d VNĐ", tierPart);
//            String voucherStr = String.format("%,d VNĐ", voucherPart);
//
//            // ✅ PHẢI ADD ĐỦ 6 CỘT
//            model.addRow(new Object[]{name, qty, priceStr, totalStr, tierStr, voucherStr});
//        }
//
//        return model;
//    }

public static DefaultTableModel getTransactionDetails(int transactionId) {

    String[] columnNames = {"Sản phẩm", "Số lượng", "Đơn giá", "Giảm hạng", "Giảm voucher", "Thành tiền"};
    DefaultTableModel model = new DefaultTableModel(null, columnNames);

    // 1) Lấy tổng đơn + giảm giá từ transactions
    String sqlTx = "SELECT raw_total, tier_discount, voucher_discount FROM transactions WHERE id = ?";
    long rawTotal = 0, tierDiscount = 0, voucherDiscount = 0;

    // 2) Lấy danh sách item
    String sqlItems = "SELECT product_name, quantity, unit_price, total_price FROM transaction_items WHERE transaction_id = ?";

    try (Connection conn = connect()) {

        // --- Query transactions ---
        try (PreparedStatement ps = conn.prepareStatement(sqlTx)) {
            ps.setInt(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rawTotal = rs.getLong("raw_total");
                    tierDiscount = rs.getLong("tier_discount");
                    voucherDiscount = rs.getLong("voucher_discount");
                }
            }
        }

        // Nếu rawTotal = 0 thì fallback tránh chia 0
        if (rawTotal <= 0) rawTotal = 1;

        long afterTierTotal = rawTotal - tierDiscount;
        if (afterTierTotal <= 0) afterTierTotal = 1;

        // Để xử lý làm tròn cho khớp tổng giảm giá
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        long tierAllocatedSum = 0;
        long voucherAllocatedSum = 0;

        // --- Query items ---
        try (PreparedStatement ps = conn.prepareStatement(sqlItems)) {
            ps.setInt(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("product_name");
                    int qty = rs.getInt("quantity");
                    long unit = rs.getLong("unit_price");
                    long itemTotal = rs.getLong("total_price"); // gốc

                    // A) phân bổ giảm hạng theo tỉ lệ trên rawTotal
                    long itemTier = Math.round((double)itemTotal * tierDiscount / rawTotal);
                    tierAllocatedSum += itemTier;

                    // B) voucher giảm sau khi đã giảm hạng
                    long itemAfterTier = itemTotal - itemTier;
                    long itemVoucher = Math.round((double)itemAfterTier * voucherDiscount / afterTierTotal);
                    voucherAllocatedSum += itemVoucher;

                    long finalLine = itemTotal - itemTier - itemVoucher;

                    rows.add(new Object[]{
                            name,
                            qty,
                            String.format("%,d VNĐ", unit),
                            String.format("%,d VNĐ", itemTier),
                            String.format("%,d VNĐ", itemVoucher),
                            String.format("%,d VNĐ", finalLine)
                    });
                }
            }
        }

        // --- Fix sai số do làm tròn: dồn phần chênh vào dòng cuối ---
        if (!rows.isEmpty()) {
            long tierDiff = tierDiscount - tierAllocatedSum;
            long voucherDiff = voucherDiscount - voucherAllocatedSum;

            Object[] last = rows.get(rows.size() - 1);

            // last[3] = giảm hạng, last[4] = giảm voucher, last[5] = thành tiền
            long lastTier = parseVnd(last[3].toString());
            long lastVoucher = parseVnd(last[4].toString());
            long lastFinal = parseVnd(last[5].toString());

            lastTier += tierDiff;
            lastVoucher += voucherDiff;
            lastFinal = lastFinal - tierDiff - voucherDiff;

            last[3] = String.format("%,d VNĐ", lastTier);
            last[4] = String.format("%,d VNĐ", lastVoucher);
            last[5] = String.format("%,d VNĐ", lastFinal);
        }

        // add vào model
        for (Object[] r : rows) model.addRow(r);

    } catch (SQLException e) {
        e.printStackTrace();
    }

    return model;
}

    // helper: đổi "20,000 VNĐ" -> 20000
    private static long parseVnd(String s) {
        return Long.parseLong(s.replace("VNĐ", "").replace(",", "").trim());
    }

    // ================== AUTO MIGRATION ==================
    public static void migrateDatabase() {
        try (Connection conn = connect()) {
            if (conn == null) {
                System.out.println("Không kết nối được DB để migrate!");
                return;
            }

            // 1) Đảm bảo bảng tồn tại trước
            createNewTable();
            createTransactionTable();
            createTransactionItemTable();

            // 2) Migrate bảng transactions (nếu bảng cũ)
            migrateTransactionsTable(conn);

            System.out.println("✅ Migrate database xong!");
        } catch (Exception e) {
            System.out.println("❌ Lỗi migrateDatabase: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void migrateTransactionsTable(Connection conn) throws SQLException {
        // Nếu bảng chưa tồn tại thì createTransactionTable() đã tạo rồi -> khỏi migrate
        if (!tableExists(conn, "transactions")) return;

        // A) Thêm cột nếu thiếu
        addColumnIfNotExists(conn, "transactions", "raw_total",
                "ALTER TABLE transactions ADD COLUMN raw_total BIGINT NOT NULL DEFAULT 0");

        addColumnIfNotExists(conn, "transactions", "tier_discount",
                "ALTER TABLE transactions ADD COLUMN tier_discount BIGINT NOT NULL DEFAULT 0");

        addColumnIfNotExists(conn, "transactions", "voucher_discount",
                "ALTER TABLE transactions ADD COLUMN voucher_discount BIGINT NOT NULL DEFAULT 0");

        // B) Ép kiểu BIGINT NOT NULL cho các cột quan trọng (an toàn chạy lại)
        runSQL(conn, "ALTER TABLE transactions MODIFY balance_before BIGINT NOT NULL");
        runSQL(conn, "ALTER TABLE transactions MODIFY balance_after  BIGINT NOT NULL");
        runSQL(conn, "ALTER TABLE transactions MODIFY delta          BIGINT NOT NULL");
    }

    // ===== Helper functions =====
    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        java.sql.DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        java.sql.DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    private static void addColumnIfNotExists(Connection conn, String tableName, String columnName, String alterSql)
            throws SQLException {
        if (!columnExists(conn, tableName, columnName)) {
            runSQL(conn, alterSql);
            System.out.println("✅ Added column " + tableName + "." + columnName);
        } else {
            System.out.println("ℹ Column exists " + tableName + "." + columnName);
        }
    }

    private static void runSQL(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

}