-- 可选：修正旧演示库中“草莓商品第二张图为苹果”的种子数据问题。
-- 请先备份并检查下列记录；如果商品或图库已自行修改，请按实际情况决定是否执行。
-- SELECT id, product_id, image_url, sort FROM product_image
-- WHERE product_id = 1 AND image_url = '/images/products/p18.jpg' AND sort = 1;
UPDATE product_image
SET image_url = '/images/products/p42.jpg'
WHERE product_id = 1 AND image_url = '/images/products/p18.jpg' AND sort = 1;
