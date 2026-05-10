-- ============================================================
-- APS 示例数据初始化脚本（使用 INSERT IGNORE 防止重复插入）
-- ============================================================

INSERT IGNORE INTO t_forecast (item_code, `year_month`, quantity, delete_flag) VALUES
('11201A012', 202604, 100, '分类1'),
('11201A012', 202605, 101, '分类1'),
('11201A012', 202606, 102, '分类1'),
('11201A022', 202604, 103, '分类1'),
('11201A022', 202605, 104, '分类1'),
('11201A022', 202606, 105, '分类1');

INSERT IGNORE INTO t_scrap_rate (item_code, scrap_rate) VALUES
('11201A012', 0.01), ('21201A012', 0.02), ('31201A012', 0.03),
('11201A022', 0.04), ('21201A022', 0.05), ('31201A022', 0.06);

INSERT IGNORE INTO t_inventory_days (item_code, safety_days, max_days) VALUES
('11201A012', 3, 15), ('21201A012', 3, 15), ('31201A012', 3, 15),
('11201A022', 3, 15), ('21201A022', 3, 15), ('31201A022', 3, 15);

INSERT IGNORE INTO t_operating_days (`year_month`, days) VALUES
(202604, 26), (202605, 26), (202606, 26);

INSERT IGNORE INTO t_bom (parent_code, child_code, usage_qty, process, equipment, mold_cavity, cycle_time, staff_count, takt_time) VALUES
('11201A012', '21201A012', 1, 'aa', 'aa001', 1, 1, 1, 1),
('21201A012', '31201A012', 1, 'bb', 'bb001', 1, 2, 2, 2),
('31201A012', NULL, NULL, 'cc', 'cc001', 2, 3, 1, 3),
('11201A022', '21201A022', 1, 'aa', 'aa001', 1, 4, 1, 4),
('21201A022', '31201A022', 1, 'bb', 'bb002', 1, 5, 2, 5),
('31201A022', NULL, NULL, 'cc', 'cc002', 2, 6, 1, 6);

INSERT IGNORE INTO t_inventory_count (item_code, `year_month`, available_qty, delete_flag) VALUES
('11201A012', 202603, 10, '分类1'), ('21201A012', 202603, 11, '分类1'), ('31201A012', 202603, 12, '分类1'),
('11201A022', 202603, 13, '分类1'), ('21201A022', 202603, 14, '分类1'), ('31201A022', 202603, 15, '分类1');
