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
const https = require('https');

const app = express();
app.use(express.json({ limit: '50mb' }));
// CORS para el WebView de la app (el fetch del chat lo necesita)
app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    if (req.method === 'OPTIONS') return res.sendStatus(204);
    next();
});

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

// ─── Patrón: LOGIN (volver a entrar sin re-registrar) ───
app.post('/api/patron/login', (req, res) => {
    const { nombre, numero_viniedo } = req.body || {};
    if (!nombre || !numero_viniedo) {
        return res.status(400).json({ ok: false, error: 'Faltan nombre y número de viñedo' });
    }
    db.get('SELECT id, nombre, numero_viniedo FROM patrones WHERE numero_viniedo=?', [numero_viniedo], (e, p) => {
        if (!p) return res.status(404).json({ ok: false, error: 'No hay un patrón registrado con ese número de viñedo' });
        if (!nombresCoinciden(p.nombre, nombre)) {
            return res.status(403).json({ ok: false, error: 'El nombre no coincide con el viñedo. Verificá.' });
        }
        res.json({ ok: true, patron_id: p.id, nombre: p.nombre });
    });
});

// ─── Patrón: LISTAR sus recibos (historial) ───
app.get('/api/patron/recibos', (req, res) => {
    const { patron_id } = req.query;
    if (!patron_id) return res.status(400).json({ ok: false, error: 'Falta patron_id' });
    db.all(`SELECT r.id, r.periodo, r.firmado, r.firma_fecha, r.created_at, c.nombre AS contratista, c.cuil
            FROM recibos r JOIN contratistas c ON c.id = r.contratista_id
            WHERE r.patron_id=? ORDER BY r.created_at DESC`, [patron_id], (e, recibos) => {
        res.json({ ok: true, recibos: recibos || [] });
    });
});

// ─── Patrón: SUBIR recibo (foto/PDF existente) ───
app.post('/api/patron/subir_recibo', (req, res) => {
    const { patron_id, contratista_cuil, periodo, archivo_base64, nombre_archivo } = req.body || {};
    if (!patron_id || !contratista_cuil || !periodo || !archivo_base64) {
        return res.status(400).json({ ok: false, error: 'Faltan datos' });
    }
    db.get('SELECT id FROM contratistas WHERE cuil=? AND patron_id=?', [contratista_cuil, patron_id], (e, c) => {
        if (!c) return res.status(404).json({ ok: false, error: 'Contratista no encontrado para este patrón' });
        try {
            const fname = `${contratista_cuil}_${periodo.replace(/\s+/g, '_')}_${Date.now()}.pdf`;
            const fpath = path.join(RECIBOS_DIR, fname);
            fs.writeFileSync(fpath, Buffer.from(archivo_base64, 'base64'));
            db.run('INSERT INTO recibos (patron_id, contratista_id, periodo, archivo_path) VALUES (?,?,?,?)',
                [patron_id, c.id, periodo, fpath],
                function (err) {
                    if (err) return res.status(500).json({ ok: false, error: err.message });
                    res.json({ ok: true, recibo_id: this.lastID });
                });
        } catch (err) {
            res.status(500).json({ ok: false, error: 'Error guardando archivo: ' + err.message });
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

// ─── Centro de ayuda inteligente (DeepSeek) ───
// La key vive en /etc/ghost-license/deepseek.key (solo root, nunca en la app)
const DEEPSEEK_KEY_PATH = '/etc/ghost-license/deepseek.key';

const AYUDA_CONTEXTO = [
    'Sos el asistente de ayuda de ViñaRecibos, una app de recibos de sueldo para contratistas de viñas y frutales de Mendoza, Argentina (ley 23.154).',
    'Respondé en español rioplatense, corto y claro, con pasos numerados si hace falta.',
    '',
    'Datos útiles que conocés:',
    '- Pantalla principal: botones "Soy patrón" y "Soy contratista", más "Ingresar con huella".',
    '- El contratista entra con CUIL + nombre (los registró su patrón) desde "Soy contratista".',
    '- La firma se sube UNA vez tocando "Subir mi firma" en el panel del contratista (foto de la firma en papel).',
    '- Los recibos se ven tocando "Ver mis recibos" en el panel del contratista.',
    '- El recibo se firma tocando el botón "Firmar" en la lista de recibos.',
    '- El patrón registra contratistas y genera recibos desde su panel ("Registrar contratista" y "Generar recibo").',
    '- Los recibos se generan en formato nuevo (Decreto 407/2026): 4 secciones + gráfico de costos.',
    '- La app es gratuita y se financia con anuncios de AdMob (intersticiales, recompensados y de apertura).',
    '- Contacto: info@charly-tricks.dev',
    '- La huella dactilar entra directo si ya entraste una vez con CUIL+nombre.',
    '- "El nombre no coincide" = el patrón lo registró con otro nombre (el orden/mayúsculas/tildes no importan).',
    '- "Error de conexión" = revisar datos móviles/wifi, el servidor puede estar temporalmente caído.',
    '- NO inventes secciones ni pantallas que no existen (no hay "Mi Perfil" ni "Configuración" en la app).',
].join('\n');

app.post('/api/ayuda', (req, res) => {
    const { pregunta } = req.body || {};
    if (!pregunta || !pregunta.trim()) {
        return res.status(400).json({ ok: false, error: 'Falta la pregunta' });
    }
    let key;
    try { key = fs.readFileSync(DEEPSEEK_KEY_PATH, 'utf-8').trim(); } catch (e) {}
    if (!key) {
        return res.status(500).json({ ok: false, error: 'Asistente no configurado todavía' });
    }

    const body = JSON.stringify({
        model: 'deepseek-chat',
        messages: [
            { role: 'system', content: AYUDA_CONTEXTO },
            { role: 'user', content: pregunta },
        ],
        max_tokens: 500,
        temperature: 0.4,
    });

    const reqApi = https.request({
        hostname: 'api.deepseek.com',
        path: '/chat/completions',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${key}`,
            'Content-Length': Buffer.byteLength(body),
        },
    }, (r) => {
        let data = '';
        r.on('data', (c) => data += c);
        r.on('end', () => {
            try {
                const parsed = JSON.parse(data);
                if (r.statusCode === 200 && parsed.choices && parsed.choices[0]) {
                    res.json({ ok: true, respuesta: parsed.choices[0].message.content.trim() });
                } else {
                    res.status(502).json({ ok: false, error: `DeepSeek ${r.statusCode}: ${parsed.error?.message || 'error'}` });
                }
            } catch (e) {
                res.status(502).json({ ok: false, error: 'Respuesta inválida del asistente' });
            }
        });
    });
    reqApi.on('error', (e) => res.status(500).json({ ok: false, error: `Error de conexión: ${e.message}` }));
    reqApi.write(body);
    reqApi.end();
});

// ─── Patrón: LEER recibo viejo (OCR) y devolver datos para convertir ───
const { execSync } = require('child_process');
const TMP_OCR = '/tmp/recibos-ocr';

app.post('/api/patron/leer_recibo', (req, res) => {
    const { archivo_base64, nombre_archivo } = req.body || {};
    if (!archivo_base64) return res.status(400).json({ ok: false, error: 'Falta el archivo' });

    fs.mkdirSync(TMP_OCR, { recursive: true });
    const ext = (nombre_archivo || 'recibo.png').includes('.') ? nombre_archivo.split('.').pop().toLowerCase() : 'png';
    const inPath = path.join(TMP_OCR, `in_${Date.now()}.${ext}`);
    const outBase = inPath.replace(/\.[^.]+$/, '');

    try {
        fs.writeFileSync(inPath, Buffer.from(archivo_base64, 'base64'));

        // Si es PDF, convertirlo a PNG (página 1)
        let imgPath = inPath;
        if (ext === 'pdf' || ext === 'jpg' || ext === 'jpeg' || ext === 'png' || ext === 'webp') {
            if (ext === 'pdf') {
                try {
                    execSync(`pdftoppm -png -r 200 -f 1 -l 1 "${inPath}" "${outBase}"`, { timeout: 30000 });
                    const png = `${outBase}-1.png`;
                    if (fs.existsSync(png)) imgPath = png;
                } catch (e) { /* seguir con el pdf directo */ }
            }
        }

        // OCR con tesseract (español)
        const texto = execSync(`tesseract "${imgPath}" stdout -l spa --psm 6 2>/dev/null`, { timeout: 60000, encoding: 'utf-8' });

        // Extraer datos clave con regex
        const datos = extraerDatosRecibo(texto);

        res.json({ ok: true, texto, datos });
    } catch (e) {
        res.status(500).json({ ok: false, error: 'No pude leer el archivo: ' + e.message });
    } finally {
        try { fs.unlinkSync(inPath); } catch (e) {}
    }
});

// Extrae datos de un recibo de sueldo (contratista de viñas)
function extraerDatosRecibo(texto) {
    const t = texto.replace(/\r/g, '');
    const datos = {};

    // Período: ENERO 2026, FEBRERO 2026...
    const mPeriodo = t.match(/(ENERO|FEBRERO|MARZO|ABRIL|MAYO|JUNIO|JULIO|AGOSTO|SEPTIEMBRE|OCTUBRE|NOVIEMBRE|DICIEMBRE)\s+20\d{2}/i);
    if (mPeriodo) datos.periodo = mPeriodo[0].toUpperCase();

    // CUIL del CONTRATISTA: el que está cerca de "Contratista:" o después de la línea del contratista
    const mCuilContratista = t.match(/Contratista:\s*[^\n]*?\b(20[- ]?\d{8}[- ]?\d)\b/i)
        || t.match(/Contratista:.*?CUIL:\s*(20[- ]?\d{8}[- ]?\d)/is)
        || t.match(/(?:Contratista|CONTRATISTA)[\s\S]{0,120}?\b(20[- ]?\d{8}[- ]?\d)\b/i);
    if (mCuilContratista) {
        const c = mCuilContratista[1] || mCuilContratista[0];
        datos.cuil = c.replace(/\s+/g, '');
    }
    if (!datos.cuil) {
        // fallback: el último CUIL del documento (el del contratista suele ir después del empleador)
        const cuiles = t.match(/\b20[- ]?\d{8}[- ]?\d\b/g);
        if (cuiles && cuiles.length) datos.cuil = cuiles[cuiles.length - 1].replace(/\s+/g, '');
    }

    // Remunerativo: buscar valor con formato de dinero (miles con puntos y/o decimales)
    const mRem = t.match(/(?:REM\.?\s*C\/D|REMUNERATIVO|REM\.?\s*C\/D\.?|SUELDO BRUTO)[:\s]*\$?\s*([\d][\d.,]*)/i);
    if (mRem) datos.remunerativo = parseNumero(mRem[1]);

    // No remunerativo
    const mNoRem = t.match(/(?:REM\.?\s*S\/D|NO REMUNERATIVO|REM\.?\s*S\/D\.?)[:\s]*\$?\s*([\d][\d.,]*)/i);
    if (mNoRem) datos.no_remunerativo = parseNumero(mNoRem[1]);

    // Deducciones total: buscar "TOTAL DEDUCCIONES" o la fila de totales con formato dinero
    const mDed = t.match(/(?:TOTAL\s*DEDUCCIONES|DEDUCCIONES|DEDUCC\.?)[:\s]*\$?\s*([\d][\d.,]*)/i);
    if (mDed && mDed[1].includes(',')) datos.deducciones_total = parseNumero(mDed[1]);

    // Neto: buscar "SUELDO NETO" seguido de valor con formato dinero (con puntos de miles)
    const mNeto = t.match(/(?:SUELDO\s*NETO|NETO|TOTAL\s*NETO)[:\s]*\$?\s*([\d][\d.,]*)/i);
    if (mNeto && mNeto[1].includes(',')) datos.neto = parseNumero(mNeto[1]);

    // Concepto (hectáreas)
    const mHas = t.match(/(\d+(?:\.\d+)?)\s*HAS?(?:\s+EN\s+PRODUCCI[OÓ]N)?/i);
    if (mHas && !mHas[1].includes('.')) datos.concepto = `${mHas[1]} HAS EN PRODUCCIÓN`;

    return datos;
}

function parseNumero(s) {
    // "1.463.512,05" → 1463512.05 ; "1463512.05" → 1463512.05
    const limpio = s.replace(/\$/g, '').replace(/\s/g, '');
    if (limpio.includes(',')) {
        return parseFloat(limpio.replace(/\./g, '').replace(',', '.'));
    }
    return parseFloat(limpio);
}

// ─── Health ───
app.get('/api/health', (req, res) => res.json({ ok: true, app: 'Recibos Viña', version: '0.1.0' }));

const PORT = process.env.PORT || 8400;
app.listen(PORT, () => console.log(`🦇 Recibos Viña API en :${PORT}`));
