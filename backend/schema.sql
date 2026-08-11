-- 🦇 RECIBOS DE SUELDO - Viñas y Frutales (Mendoza)
-- Base de datos SQLite: /opt/recibos-vina/recibos.db

-- ─── PATRONES (empleadores) ───
-- Se registran con nombre + número de viñedo + password
CREATE TABLE IF NOT EXISTS patrones (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL,
    numero_viniedo TEXT UNIQUE NOT NULL,
    cuit TEXT,
    telefono TEXT,
    password_hash TEXT NOT NULL,     -- hash bcrypt, NUNCA texto plano
    created_at TEXT DEFAULT (datetime('now'))
);

-- ─── CONTRATISTAS (empleados) ───
-- Los registra el patrón con CUIL + nombre + PIN (4-6 dígitos que el patrón define/entrega)
CREATE TABLE IF NOT EXISTS contratistas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cuil TEXT UNIQUE NOT NULL,
    nombre TEXT NOT NULL,
    patron_id INTEGER NOT NULL,
    pin_hash TEXT NOT NULL,          -- hash bcrypt del PIN, NUNCA texto plano
    firma_path TEXT,
    firma_activa INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (patron_id) REFERENCES patrones(id)
);

-- ─── RECIBOS ───
CREATE TABLE IF NOT EXISTS recibos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    patron_id INTEGER NOT NULL,
    contratista_id INTEGER NOT NULL,
    periodo TEXT NOT NULL,
    archivo_path TEXT NOT NULL,
    firmado INTEGER DEFAULT 0,
    firma_fecha TEXT,
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (patron_id) REFERENCES patrones(id),
    FOREIGN KEY (contratista_id) REFERENCES contratistas(id)
);

-- ─── SESIONES ───
-- Token de sesión para patrón o contratista (se manda en header Authorization: Bearer <token>)
CREATE TABLE IF NOT EXISTS sesiones (
    token TEXT PRIMARY KEY,          -- UUID random, 32+ bytes
    tipo TEXT NOT NULL,              -- 'patron' | 'contratista'
    referencia_id INTEGER NOT NULL,  -- patron_id o contratista_id según tipo
    created_at TEXT DEFAULT (datetime('now')),
    expires_at TEXT NOT NULL         -- datetime('now', '+30 days') por defecto
);

-- Índices
CREATE INDEX IF NOT EXISTS idx_recibos_contratista ON recibos(contratista_id, periodo);
CREATE INDEX IF NOT EXISTS idx_contratistas_cuil ON contratistas(cuil);
CREATE INDEX IF NOT EXISTS idx_patrones_viniedo ON patrones(numero_viniedo);
CREATE INDEX IF NOT EXISTS idx_sesiones_expires ON sesiones(expires_at);
