MERGE INTO event_types (id, title, description, duration_minutes) KEY (id)
  VALUES ('intro-call', 'Знакомство', 'Короткий звонок, чтобы познакомиться и обсудить задачу.', 30);
MERGE INTO event_types (id, title, description, duration_minutes) KEY (id)
  VALUES ('consultation', 'Консультация', 'Подробный разбор вашего вопроса.', 60);
