-- V6: datos de demo (categorías + productos) para poblar el catálogo en desarrollo.
-- Ids explícitos para que los productos referencien categorías de forma determinística.

INSERT INTO categories (id, name, description) VALUES
  (1, 'Laptops',     'Notebooks y ultrabooks'),
  (2, 'Audio',       'Audífonos, parlantes y micrófonos'),
  (3, 'Componentes', 'GPU, CPU, RAM y almacenamiento'),
  (4, 'Periféricos', 'Teclados, mouse y accesorios'),
  (5, 'Monitores',   'Monitores y pantallas');

INSERT INTO products (sku, name, description, price, stock, image_url, active, category_id) VALUES
  ('KR-LAP-001', 'Laptop Krypton Vortex 15',        'Intel Core i7, 16GB RAM, RTX 4060, 15.6" 144Hz', 4299.00, 12, NULL, TRUE, 1),
  ('KR-LAP-002', 'Laptop Krypton Air 14',           'Intel Core i5, 8GB RAM, SSD 512GB, 14" liviana',  2799.00, 20, NULL, TRUE, 1),
  ('KR-AUD-001', 'Audífonos Krypton Pulse X',       'Inalámbricos, cancelación de ruido, 30h batería',  349.90, 35, NULL, TRUE, 2),
  ('KR-AUD-002', 'Parlante Krypton Boom',           'Bluetooth, resistente al agua IPX7, 20W',          199.90, 28, NULL, TRUE, 2),
  ('KR-CMP-001', 'Tarjeta de video Krypton RTX 4070','GDDR6X 12GB, ray tracing, DLSS 3',                2599.00,  8, NULL, TRUE, 3),
  ('KR-CMP-002', 'Memoria RAM Krypton Fury 32GB',   'DDR5 6000MHz, kit 2x16GB, RGB',                     559.00, 40, NULL, TRUE, 3),
  ('KR-CMP-003', 'SSD Krypton NVMe 1TB',            'Gen4, 7000MB/s de lectura',                        389.00, 50, NULL, TRUE, 3),
  ('KR-PER-001', 'Teclado Mecánico Krypton TKL',    'Switches rojos, hot-swap, retroiluminado',         259.00, 30, NULL, TRUE, 4),
  ('KR-PER-002', 'Mouse Krypton Glide Pro',         'Inalámbrico, 26000 DPI, 60g',                      179.00, 45, NULL, TRUE, 4),
  ('KR-MON-001', 'Monitor Krypton View 27',         'QHD 165Hz, IPS, 1ms',                             1099.00, 15, NULL, TRUE, 5);
