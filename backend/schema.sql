-- 🦇 RECIBOS DE SUELDO - Viñas y Frutales (Mendoza)
-- Base de datos SQLite: /opt/recibos-vina/recibos.db

-- ─── PATRONES (empleadores) ───
-- Se registran con nombre + número de viñedo (la clave del contrato)
CREATE TABLE IF NOT EXISTS patrones (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL,            -- nombre y apellido del patrón
    numero_viniedo TEXT UNIQUE NOT NULL,  -- número de viñedo (identificador del contrato)
    cuit TEXT,                       -- opcional
    telefono TEXT,                   -- opcional
    password TEXT,                   -- hash para entrar
    created_at TEXT DEFAULT (datetime('now'))
);

-- ─── CONTRATISTAS (empleados) ───
-- Los registra el patrón con CUIL + nombre (así nadie entra al recibo de otro)
CREATE TABLE IF NOT EXISTS contratistas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cuil TEXT UNIQUE NOT NULL,       -- la clave de acceso
    nombre TEXT NOT NULL,            -- nombre y apellido
    patron_id INTEGER NOT NULL,      -- a qué patrón pertenece
    firma_path TEXT,                 -- ruta de la imagen de su firma (foto)
    firma_activa INTEGER DEFAULT 0,  -- 1 = ya subió su firma
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (patron_id) REFERENCES patrones(id)
);

-- ─── RECIBOS ───
-- Los sube el patrón (foto/PDF) y el contratista los firma
CREATE TABLE IF NOT EXISTS recibos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    patron_id INTEGER NOT NULL,
    contratista_id INTEGER NOT NULL,
    periodo TEXT NOT NULL,           -- ej: "ENERO 2026"
    archivo_path TEXT NOT NULL,      -- ruta del PDF/imagen del recibo
    firmado INTEGER DEFAULT 0,       -- 0 = sin firmar, 1 = firmado
    firma_fecha TEXT,                -- cuándo lo firmó el contratista
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (patron_id) REFERENCES patrones(id),
    FOREIGN KEY (contratista_id) REFERENCES contratistas(id)
);

-- Índices para búsqueda rápida
CREATE INDEX IF NOT EXISTS idx_recibos_contratista ON recibos(contratista_id, periodo);
CREATE INDEX IF NOT EXISTS idx_contratistas_cuil ON contratistas(cuil);
CREATE INDEX IF NOT EXISTS idx_patrones_viniedo ON patrones(numero_viniedo);
