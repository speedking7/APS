-- APS 主数据种子数据（由项目根目录 Excel 文件生成）
-- 生成时间：2026-05-15

SET FOREIGN_KEY_CHECKS=0;

-- t_demand
DELETE FROM t_demand;
INSERT INTO t_demand (customer,item_code,`year_month`,demand_qty,ending_inventory,min_safety_stock,net_demand,version) VALUES ('AAA','11201A012',202604,100,10,0,90,'20260331-1');
INSERT INTO t_demand (customer,item_code,`year_month`,demand_qty,ending_inventory,min_safety_stock,net_demand,version) VALUES ('AAA','11201A012',202605,101,0,0,101,'20260331-1');
INSERT INTO t_demand (customer,item_code,`year_month`,demand_qty,ending_inventory,min_safety_stock,net_demand,version) VALUES ('AAA','11201A012',202606,102,0,0,102,'20260331-1');
INSERT INTO t_demand (customer,item_code,`year_month`,demand_qty,ending_inventory,min_safety_stock,net_demand,version) VALUES ('BBB','11201A022',202604,103,13,1,91,'20260331-1');
INSERT INTO t_demand (customer,item_code,`year_month`,demand_qty,ending_inventory,min_safety_stock,net_demand,version) VALUES ('BBB','11201A022',202605,104,1,1,104,'20260331-1');
INSERT INTO t_demand (customer,item_code,`year_month`,demand_qty,ending_inventory,min_safety_stock,net_demand,version) VALUES ('BBB','11201A022',202606,105,1,1,105,'20260331-1');

-- t_bom
DELETE FROM t_bom;
INSERT INTO t_bom (parent_code,child_code,usage_qty,process,equipment,mold_cavity,cycle_time,staff_count,takt_time,scrap_rate,version) VALUES ('11201A012','11201A012-1',1,'aa','aa001',1,30,1,30,0.01,'20260331-1');
INSERT INTO t_bom (parent_code,child_code,usage_qty,process,equipment,mold_cavity,cycle_time,staff_count,takt_time,scrap_rate,version) VALUES ('11201A012-1','21201A012',1,'bb','bb001',1,31,1,31,0.02,'20260331-1');
INSERT INTO t_bom (parent_code,child_code,usage_qty,process,equipment,mold_cavity,cycle_time,staff_count,takt_time,scrap_rate,version) VALUES ('11201A012-1','11201A012-1-1',1,'bb','bb001',1,31,1,31,0.02,'20260331-1');
INSERT INTO t_bom (parent_code,child_code,usage_qty,process,equipment,mold_cavity,cycle_time,staff_count,takt_time,scrap_rate,version) VALUES ('11201A012-1-1','31201A012',1,'cc','cc001',1,33,1,33,0.04,'20260331-1');
INSERT INTO t_bom (parent_code,child_code,usage_qty,process,equipment,mold_cavity,cycle_time,staff_count,takt_time,scrap_rate,version) VALUES ('11201A012-1-1','31201A013',1,'cc','cc001',1,33,1,33,0.04,'20260331-1');
INSERT INTO t_bom (parent_code,child_code,usage_qty,process,equipment,mold_cavity,cycle_time,staff_count,takt_time,scrap_rate,version) VALUES ('21201A012','31201A014',1,'dd','dd001',1,35,1,35,0.06,'20260331-1');
INSERT INTO t_bom (parent_code,child_code,usage_qty,process,equipment,mold_cavity,cycle_time,staff_count,takt_time,scrap_rate,version) VALUES ('31201A013','41201A012',1,'ee','ee001',1,36,1,36,0.07,'20260331-1');
INSERT INTO t_bom (parent_code,child_code,usage_qty,process,equipment,mold_cavity,cycle_time,staff_count,takt_time,scrap_rate,version) VALUES ('41201A012',NULL,NULL,'ff','ff001',1,37,1,37,0.08,'20260331-1');
INSERT INTO t_bom (parent_code,child_code,usage_qty,process,equipment,mold_cavity,cycle_time,staff_count,takt_time,scrap_rate,version) VALUES ('11201A022','21201A022',1,'aa','aa001',1,38,1,38,0.09,'20260331-1');
INSERT INTO t_bom (parent_code,child_code,usage_qty,process,equipment,mold_cavity,cycle_time,staff_count,takt_time,scrap_rate,version) VALUES ('21201A022','31201A022',1,'bb','bb002',1,39,1,39,0.1,'20260331-1');
INSERT INTO t_bom (parent_code,child_code,usage_qty,process,equipment,mold_cavity,cycle_time,staff_count,takt_time,scrap_rate,version) VALUES ('31201A022',NULL,NULL,'cc','cc002',1,40,1,40,0.11,'20260331-1');

-- t_safety_stock
DELETE FROM t_safety_stock;
INSERT INTO t_safety_stock (item_code,`year_month`,daily_equivalent,safety_days,max_days,version) VALUES ('21201A012',202604,10,3,15,'20260331-1');
INSERT INTO t_safety_stock (item_code,`year_month`,daily_equivalent,safety_days,max_days,version) VALUES ('31201A012',202605,11,3,15,'20260331-1');
INSERT INTO t_safety_stock (item_code,`year_month`,daily_equivalent,safety_days,max_days,version) VALUES ('21201A022',202604,12,3,15,'20260331-1');
INSERT INTO t_safety_stock (item_code,`year_month`,daily_equivalent,safety_days,max_days,version) VALUES ('31201A022',202605,13,3,15,'20260331-1');

-- t_operating_days
DELETE FROM t_operating_days;
INSERT INTO t_operating_days (`year_month`,total_days,work_days,weekend_days,holiday_days) VALUES (202604,26,21,5,0);
INSERT INTO t_operating_days (`year_month`,total_days,work_days,weekend_days,holiday_days) VALUES (202605,27,22,5,0);
INSERT INTO t_operating_days (`year_month`,total_days,work_days,weekend_days,holiday_days) VALUES (202606,26,21,5,0);

-- t_inventory_count
DELETE FROM t_inventory_count;
INSERT INTO t_inventory_count (item_code,`year_month`,available_qty,version) VALUES ('21201A012',202603,11,'20260331-1');
INSERT INTO t_inventory_count (item_code,`year_month`,available_qty,version) VALUES ('31201A012',202603,12,'20260331-1');
INSERT INTO t_inventory_count (item_code,`year_month`,available_qty,version) VALUES ('21201A022',202603,14,'20260331-1');
INSERT INTO t_inventory_count (item_code,`year_month`,available_qty,version) VALUES ('31201A022',202603,15,'20260331-1');

SET FOREIGN_KEY_CHECKS=1;