CREATE TABLE IF NOT EXISTS patients (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  birth_year  INT  NOT NULL,
  allergies   TEXT[] NOT NULL DEFAULT '{}',
  conditions  TEXT[] NOT NULL DEFAULT '{}',
  medications TEXT[] NOT NULL DEFAULT '{}'
);
CREATE TABLE IF NOT EXISTS doctors (
  id        TEXT PRIMARY KEY,
  name      TEXT NOT NULL,
  specialty TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS slots (
  id        TEXT PRIMARY KEY,
  doctor_id TEXT NOT NULL REFERENCES doctors(id),
  start_at  TIMESTAMP NOT NULL,
  is_booked BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE IF NOT EXISTS appointments (
  id           TEXT PRIMARY KEY,
  patient_id   TEXT NOT NULL REFERENCES patients(id),
  slot_id      TEXT NOT NULL REFERENCES slots(id),
  reason       TEXT NOT NULL,
  status       TEXT NOT NULL,
  summary_json TEXT
);
CREATE TABLE IF NOT EXISTS chat_memory (
  memory_id     TEXT PRIMARY KEY,
  messages_json TEXT NOT NULL,
  updated_at    TIMESTAMP NOT NULL DEFAULT now()
);
