-- Hər sınaq ayrıca pulsuz və ya ödənişli ola bilər (admin panelindən dəyişdirilir).
-- Əvvəlki qayda saxlanılır: 1-3 pulsuz, qalanları ödənişli.
ALTER TABLE categories ADD COLUMN free BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE categories SET free = TRUE WHERE number <= 3;
