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
}