/**
 * 🦇 RECIBOS DE SUELDO — Viñas y Frutales (Mendoza)
 * Backend API: registro de patrones/contratistas, recibos y firmas
 * 
 * Puerto: 8400
 * 
 * Endpoints:
 *   POST /api/patron/registrar      → { nombre, numero_viniedo }
 *   POST /api/patron/contratista    → { cuil, nombre } (registra contratista)
 *   POST /api/patron/recibo         → { contratista_cuil, periodo, archivo }
 *   GET  /api/contratista/recibos   → ?cuil&nombre (lista recibos del contratista)
 *   POST /api/contratista/firma     → { cuil, firma_base64 } (sube su firma)
 *   POST /api/contratista/firmar    → { recibo_id, cuil } (firma el recibo)
 */
const express = require('express');
const sqlite3 = require('sqlite3');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const app = express();
app.use(express.json({ limit: '50mb' }));

const DB_PATH = '/opt/recibos-vina/recibos.db';
const FIRMAS_DIR = '/opt/recibos-vina/firmas';
const RECIBOS_DIR = '/opt/recibos-vina/recibos';

fs.mkdirSync('/opt/recibos-vina', { recursive: true });
fs.mkdirSync(FIRMAS_DIR, { recursive: true });
fs.mkdirSync(RECIBOS_DIR, { recursive: true });

const db = new sqlite3.Database(DB_PATH);
db.exec(fs.readFileSync(__dirname + '/schema.sql', 'utf-8'));

// ─── Patrón: registrarse ───
app.post('/api/patron/registrar', (req, res) => {
    const { nombre, numero_viniedo, cuit, telefono } = req.body || {};
    if (!nombre || !numero_viniedo) {
        return res.status(400).json({ ok: false, error: 'Faltan nombre o número de viñedo' });
    }
    db.run('INSERT OR IGNORE INTO patrones (nombre, numero_viniedo, cuit, telefono) VALUES (?,?,?,?)',
        [nombre, numero_viniedo, cuit || null, telefono || null],
        function (err) {
            if (err) return res.status(500).json({ ok: false, error: err.message });
            if (this.changes === 0) {
                // ya existe → devolver el id
                db.get('SELECT id FROM patrones WHERE numero_viniedo=?', [numero_viniedo], (e, row) => {
                    res.json({ ok: true, patron_id: row.id, mensaje: 'Ya estaba registrado' });
                });
            } else {
                res.json({ ok: true, patron_id: this.lastID });
            }
        });
});

// ─── Patrón: registrar contratista ───
app.post('/api/patron/contratista', (req, res) => {
    const { patron_id, cuil, nombre } = req.body || {};
    if (!patron_id || !cuil || !nombre) {
        return res.status(400).json({ ok: false, error: 'Faltan datos (patron_id, cuil, nombre)' });
    }
    db.run('INSERT OR IGNORE INTO contratistas (cuil, nombre, patron_id) VALUES (?,?,?)',
        [cuil, nombre, patron_id],
        function (err) {
            if (err) return res.status(500).json({ ok: false, error: err.message });
            res.json({ ok: true, contratista_id: this.lastID || 'ya existía' });
        });
});

// ─── Patrón: subir recibo ───
app.post('/api/patron/recibo', (req, res) => {
    const { patron_id, contratista_cuil, periodo, archivo_base64, nombre_archivo } = req.body || {};
    if (!patron_id || !contratista_cuil || !periodo || !archivo_base64) {
        return res.status(400).json({ ok: false, error: 'Faltan datos' });
    }
    db.get('SELECT id FROM contratistas WHERE cuil=? AND patron_id=?', [contratista_cuil, patron_id], (e, c) => {
        if (!c) return res.status(404).json({ ok: false, error: 'Contratista no encontrado para este patrón' });
        const fname = `${contratista_cuil}_${periodo.replace(/\s+/g, '_')}_${Date.now()}.pdf`;
        const fpath = path.join(RECIBOS_DIR, fname);
        fs.writeFileSync(fpath, Buffer.from(archivo_base64, 'base64'));
        db.run('INSERT INTO recibos (patron_id, contratista_id, periodo, archivo_path) VALUES (?,?,?,?)',
            [patron_id, c.id, periodo, fpath],
            function (err) {
                if (err) return res.status(500).json({ ok: false, error: err.message });
                res.json({ ok: true, recibo_id: this.lastID });
            });
    });
});

// ─── Patrón: GENERAR recibo (formato 407/2026) ───
app.post('/api/patron/generar_recibo', (req, res) => {
    const { patron_id, contratista_cuil, periodo, concepto, remunerativo, no_remunerativo, deducciones } = req.body || {};
    if (!patron_id || !contratista_cuil || !periodo || !remunerativo) {
        return res.status(400).json({ ok: false, error: 'Faltan datos (patron_id, contratista_cuil, periodo, remunerativo)' });
    }
    db.get('SELECT id, nombre, cuil FROM contratistas WHERE cuil=? AND patron_id=?', [contratista_cuil, patron_id], async (e, c) => {
        if (!c) return res.status(404).json({ ok: false, error: 'Contratista no encontrado para este patrón' });
        try {
            const { generarReciboPDF } = require('./recibo_pdf.js');
            const d = {
                empleador_nombre: '—',
                cuit_empleador: '—',
                nombre: c.nombre,
                cuil: c.cuil,
                obra_social: 'OSPRERA',
                periodo,
                concepto: concepto || 'HAS EN PRODUCCIÓN',
                remunerativo: Number(remunerativo),
                no_remunerativo: Number(no_remunerativo || 0),
                deducciones: deducciones || [
                    { cod: '301', concepto: 'JUBILACIÓN 11%', monto: Math.round(Number(remunerativo) * 0.11 * 100) / 100 },
                    { cod: '302', concepto: 'LEY 19.032 (PAMI) 3%', monto: Math.round(Number(remunerativo) * 0.03 * 100) / 100 },
                    { cod: '303', concepto: 'OBRA SOCIAL', monto: Math.round(Number(remunerativo) * 0.06 * 100) / 100 },
                ],
                contribuciones: [
                    { concepto: 'ART (Riesgos del Trabajo)', unidad: '≈ 3,50%', monto: Math.round(Number(remunerativo) * 0.035 * 100) / 100 },
                    { concepto: 'Contribución Jubilación / SIPA', unidad: '18,00%', monto: Math.round(Number(remunerativo) * 0.18 * 100) / 100 },
                    { concepto: 'Contribución Obra Social', unidad: '6,00%', monto: Math.round(Number(remunerativo) * 0.06 * 100) / 100 },
                    { concepto: 'Seguro de Vida Obligatorio', unidad: 'fijo', monto: 1024.21 },
                    { concepto: 'Fondo de Desempleo (empleador)', unidad: '1,50%', monto: Math.round(Number(remunerativo) * 0.015 * 100) / 100 },
                    { concepto: 'Costos CCT / Paritaria', unidad: '—', monto: 0 },
                ],
            };
            const r = await generarReciboPDF(d);
            // guardar en DB
            db.run('INSERT INTO recibos (patron_id, contratista_id, periodo, archivo_path) VALUES (?,?,?,?)',
                [patron_id, c.id, periodo, r.path],
                function (err) {
                    if (err) return res.status(500).json({ ok: false, error: err.message });
                    res.json({ ok: true, recibo_id: this.lastID, pdf: r.path, neto: r.neto });
                });
        } catch (err) {
            res.status(500).json({ ok: false, error: 'Error generando PDF: ' + err.message });
        }
    });
});

// ─── Contratista: validar acceso y listar recibos ───
app.get('/api/contratista/recibos', (req, res) => {
    const { cuil, nombre } = req.query;
    if (!cuil || !nombre) return res.status(400).json({ ok: false, error: 'Faltan cuil y nombre' });
    db.get('SELECT id, nombre, patron_id, firma_activa FROM contratistas WHERE cuil=?', [cuil], (e, c) => {
        if (!c) return res.status(404).json({ ok: false, error: 'No estás registrado. Pedile a tu patrón que te registre.' });
        if (!nombresCoinciden(c.nombre, nombre)) {
            return res.status(403).json({ ok: false, error: 'El nombre no coincide con el CUIL. Verificá.' });
        }
        db.all('SELECT id, periodo, firmado, firma_fecha, created_at FROM recibos WHERE contratista_id=? ORDER BY created_at DESC', [c.id], (e2, recibos) => {
            res.json({ ok: true, contratista: { id: c.id, nombre: c.nombre, firma_activa: c.firma_activa }, recibos });
        });
    });
});

// Normaliza un nombre: minúsculas, sin tildes, sin símbolos, palabras ordenadas
function normalizarNombre(n) {
    return (n || '')
        .toLowerCase()
        .normalize('NFD').replace(/[\u0300-\u036f]/g, '')  // quitar tildes
        .replace(/[^a-z0-9\s]/g, ' ')                       // solo letras/números
        .split(/\s+/).filter(Boolean).sort().join(' ');     // ordenar palabras
}

function nombresCoinciden(registrado, ingresado) {
    return normalizarNombre(registrado) === normalizarNombre(ingresado);
}

// ─── Contratista: subir su firma (foto del papel) ───
app.post('/api/contratista/firma', (req, res) => {
    const { cuil, firma_base64 } = req.body || {};
    if (!cuil || !firma_base64) return res.status(400).json({ ok: false, error: 'Faltan datos' });
    db.get('SELECT id FROM contratistas WHERE cuil=?', [cuil], (e, c) => {
        if (!c) return res.status(404).json({ ok: false, error: 'No estás registrado' });
        const fname = `firma_${cuil}.png`;
        const fpath = path.join(FIRMAS_DIR, fname);
        fs.writeFileSync(fpath, Buffer.from(firma_base64, 'base64'));
        db.run('UPDATE contratistas SET firma_path=?, firma_activa=1 WHERE id=?', [fpath, c.id], (e2) => {
            res.json({ ok: true, mensaje: 'Firma guardada!' });
        });
    });
});

// ─── Contratista: firmar un recibo (usa su firma guardada) ───
app.post('/api/contratista/firmar', (req, res) => {
    const { recibo_id, cuil } = req.body || {};
    if (!recibo_id || !cuil) return res.status(400).json({ ok: false, error: 'Faltan datos' });
    db.get('SELECT id, firma_path FROM contratistas WHERE cuil=?', [cuil], (e, c) => {
        if (!c) return res.status(404).json({ ok: false, error: 'No estás registrado' });
        if (!c.firma_path) return res.status(400).json({ ok: false, error: 'Primero subí tu firma (sacale foto a tu firma en papel)' });
        db.run("UPDATE recibos SET firmado=1, firma_fecha=datetime('now') WHERE id=? AND contratista_id=?",
            [recibo_id, c.id],
            function (err) {
                if (err) return res.status(500).json({ ok: false, error: err.message });
                if (this.changes === 0) return res.status(404).json({ ok: false, error: 'Recibo no encontrado o no te pertenece' });
                res.json({ ok: true, mensaje: 'Recibo firmado!', firma_path: c.firma_path });
            });
    });
});

// ─── Health ───
app.get('/api/health', (req, res) => res.json({ ok: true, app: 'Recibos Viña', version: '0.1.0' }));

const PORT = process.env.PORT || 8400;
app.listen(PORT, () => console.log(`🦇 Recibos Viña API en :${PORT}`));
