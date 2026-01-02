package com.mycompany.membershipcardgui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class TransactionDetailDialog extends JDialog {

    public TransactionDetailDialog(int transactionId) {
        setTitle("Chi tiết đơn hàng #" + transactionId);
        setSize(600, 400);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setModal(true); // Chặn cửa sổ chính khi đang mở dialog này

        // 1. Tiêu đề
        JLabel titleLabel = new JLabel("Danh sách sản phẩm", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(new Color(106, 76, 147)); // Màu tím chủ đạo
        titleLabel.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));
        add(titleLabel, BorderLayout.NORTH);

        // 2. Lấy dữ liệu từ Database
        // Gọi hàm vừa viết ở Bước 1
        javax.swing.table.DefaultTableModel model = Database.getTransactionDetails(transactionId);

        // 3. Tạo bảng JTable
        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.getTableHeader().setBackground(new Color(240, 240, 240));

        // Căn giữa các cột số lượng và tiền
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(1).setCellRenderer(centerRenderer); // Số lượng

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
        table.getColumnModel().getColumn(2).setCellRenderer(rightRenderer); // Đơn giá
        table.getColumnModel().getColumn(3).setCellRenderer(rightRenderer); // Thành tiền

        // 4. Đưa bảng vào ScrollPane
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(scrollPane, BorderLayout.CENTER);

        // 5. Nút đóng
        JButton closeBtn = new JButton("Đóng");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        closeBtn.setBackground(new Color(231, 76, 60)); // Màu đỏ
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> dispose());

        JPanel btnPanel = new JPanel();
        btnPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        btnPanel.add(closeBtn);
        add(btnPanel, BorderLayout.SOUTH);
    }
}