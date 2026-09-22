-- Seed data: use INSERT IGNORE so restarts are idempotent
INSERT IGNORE INTO products (id, name, sku, quantity, version, created_at, updated_at) VALUES
(1, 'Gaming Laptop Pro',    'LAPTOP-001', 10, 0, NOW(), NOW()),
(2, 'Wireless Headphones',  'HEAD-002',    5, 0, NOW(), NOW()),
(3, 'Mechanical Keyboard',  'KEY-003',     3, 0, NOW(), NOW()),
(4, 'USB-C Hub',            'HUB-004',    20, 0, NOW(), NOW()),
(5, 'Monitor 4K',           'MON-005',     7, 0, NOW(), NOW());
