CREATE TABLE IF NOT EXISTS event_types (
  id VARCHAR(100) PRIMARY KEY,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(500) NOT NULL DEFAULT '',
  duration_minutes INT NOT NULL
);

CREATE TABLE IF NOT EXISTS bookings (
  id VARCHAR(36) PRIMARY KEY,
  event_type_id VARCHAR(100) NOT NULL REFERENCES event_types (id) ON DELETE CASCADE,
  start_at TIMESTAMP WITH TIME ZONE NOT NULL,
  end_at TIMESTAMP WITH TIME ZONE NOT NULL,
  guest_name VARCHAR(100) NOT NULL,
  guest_email VARCHAR(254) NOT NULL,
  guest_topic VARCHAR(200) NOT NULL DEFAULT ''
);

-- Тема разговора (спецификация #8) для файловой БД, созданной до неё: повторный запуск ничего не
-- меняет, старые встречи получают пустую тему
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS guest_topic VARCHAR(200) NOT NULL DEFAULT '';

-- Единственная строка-замок: сериализует проверку занятости и вставку встречи (BookingService)
CREATE TABLE IF NOT EXISTS calendar_lock (
  id INT PRIMARY KEY
);
MERGE INTO calendar_lock KEY (id) VALUES (1);
