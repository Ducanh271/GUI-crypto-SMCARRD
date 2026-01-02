package com.mycompany.membershipcardgui;

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
                + "phone VARCHAR(20), "
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
            pstmt.setString(4, phone);

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

            if (rs.next()) {
                String storedPhone = rs.getString("phone");
                // So sánh số điện thoại (bỏ khoảng trắng nếu có)
                if (storedPhone != null && inputPhone != null) {
                    return storedPhone.trim().equals(inputPhone.trim());
                }
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
            ps.setString(4, phone);
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
            log_index INT NOT NULL, -- moi them chi so de danh dau thu tu giao dich
            balance_before INT NOT NULL,
            balance_after INT NOT NULL,
            delta INT NOT NULL,
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
            long delta
    ) throws SQLException {

        String sql = """
        INSERT INTO transactions
        (member_id, log_index, balance_before, balance_after, delta)
        VALUES (?, ?, ?, ?, ?)
    """;

        try (Connection conn = connect();
             PreparedStatement ps =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, memberId);
            ps.setInt(2, logIndex);
            ps.setLong(3, balanceBefore);
            ps.setLong(4, balanceAfter);
            ps.setLong(5, delta);

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

    public static javax.swing.table.DefaultTableModel getTransactionDetails(int transactionId) {
        // Tên cột cho bảng
        String[] columnNames = {"Sản phẩm", "Số lượng", "Đơn giá", "Thành tiền"};
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(null, columnNames);

        String sql = "SELECT product_name, quantity, unit_price, total_price FROM transaction_items WHERE transaction_id = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, transactionId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String name = rs.getString("product_name");
                int qty = rs.getInt("quantity");
                long price = rs.getLong("unit_price");
                long total = rs.getLong("total_price");

                // Format tiền tệ cho đẹp
                String priceStr = String.format("%,d VNĐ", price);
                String totalStr = String.format("%,d VNĐ", total);

                model.addRow(new Object[]{name, qty, priceStr, totalStr});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return model;
    }

}