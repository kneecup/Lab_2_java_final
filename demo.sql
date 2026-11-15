-- Демонстрационные данные: доноры и реципиенты
INSERT INTO donors VALUES (1, 'Иванов Иван Иванович', 'A+', 82.5);
INSERT INTO donors VALUES (2, 'Петрова Анна Сергеевна', 'O-', 60.0), (3, 'Сидоров Пётр Олегович', 'B+', 90.3);
INSERT INTO donors (donor_id, full_name, blood_type) VALUES (4, 'Кузнецова Мария Игоревна', 'AB-');
INSERT INTO recipients VALUES (1, 'Смирнов Алексей', 'A+', 1, '2026-11-01', TRUE);
INSERT INTO recipients VALUES (2, 'Орлова Ольга', 'O-', 2, '2026-11-10', FALSE);
INSERT INTO recipients (recipient_id, full_name, blood_type, request_date, urgent) VALUES (3, 'Волков Дмитрий', 'B+', '2026-11-12', TRUE);
SELECT * FROM donors;
SELECT * FROM recipients;
CREATE INDEX idx_donors_blood ON donors (blood_type);
SELECT full_name, weight_kg FROM donors WHERE blood_type = 'A+';
SELECT full_name, weight_kg FROM donors WHERE weight_kg > 70;
UPDATE donors SET weight_kg = 85 WHERE donor_id = 1;
SELECT * FROM donors WHERE donor_id = 1;
SELECT * FROM recipients WHERE urgent = TRUE;
SHOW TABLES;
