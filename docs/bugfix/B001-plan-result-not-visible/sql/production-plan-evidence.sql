SELECT COUNT(*) AS production_plan_count FROM t_production_plan;
SELECT id, finished_product_code, item_code, `year_month`, plan_qty, version FROM t_production_plan LIMIT 5;
