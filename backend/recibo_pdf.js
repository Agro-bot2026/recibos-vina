/**
 * 🦇 Generador de recibo PDF — Formato Anexo III (Decreto 407/2026)
 * Renderiza el HTML exacto (aprobado por el usuario) a PDF con Chromium.
 * 
 * Uso:
 *   generarReciboPDF(datos) → { path, neto }
 *   con firma: datos.firma_path = 'ruta/imagen.png' (se estampa)
 */
const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

/**
 * Construye el HTML del recibo (idéntico al modelo aprobado).
 * @param {object} d - datos del recibo
 * @param {number} neto - sueldo neto calculado
 */
function buildHTML(d, neto) {
    const contribuciones = d.contribuciones || [];
    const subtotalContrib = contribuciones.reduce((s, c) => s + (c.monto || 0), 0);
    const totalDed = (d.deducciones || []).reduce((s, x) => s + x.monto, 0);
    const costo = d.costo_laboral || [];
    const costoTotalEmp = costo.reduce((s, c) => s + (c.emp || 0), 0);
    const costoTotalTrab = costo.reduce((s, c) => s + (c.trab || 0), 0);

    const rowsContrib = contribuciones.map(c => `
        <tr><td>${c.concepto}</td><td>${c.unidad}</td><td>${fmt(d.remunerativo)}</td><td class="derecha">${fmt(c.monto)}</td></tr>`).join('\n');

    const rowsDed = (d.deducciones || []).map((dd, i) => `
        <tr><td>${dd.cod || ''}</td><td>${dd.concepto}</td><td>1,00</td><td>—</td><td>—</td><td class="derecha">${fmt(dd.monto)}</td></tr>`).join('\n');

    const rowsCosto = costo.map(c => `
        <tr><td>${c.rubro}</td><td class="derecha">${fmt(c.emp)}</td><td class="derecha">${fmt(c.trab)}</td></tr>`).join('\n');

    // Firmas: la del contratista (imagen) si existe
    const firmaImg = d.firma_path && fs.existsSync(d.firma_path)
        ? `<img src="file://${d.firma_path}" style="height:42px;position:absolute;left:60px;top:-20px;opacity:0.9"/>` : '';

    return `<!DOCTYPE html>
<html lang="es"><head><meta charset="UTF-8"><style>
  body { font-family: 'Segoe UI', Arial, sans-serif; background: #fff; color: #111; margin: 0; padding: 24px; font-size: 11px; }
  h1 { font-size: 14px; text-align: center; margin: 0 0 2px; color: #1a4d8f; }
  .sub { text-align: center; font-size: 9px; color: #555; margin-bottom: 10px; }
  table { width: 100%; border-collapse: collapse; font-size: 10px; margin-bottom: 8px; }
  th, td { border: 1px solid #999; padding: 3px 5px; text-align: left; }
  th { background: #1a4d8f; color: #fff; font-weight: 600; }
  .total-row td { background: #eef3fb; font-weight: 700; }
  .neto-row td { background: #d4edda; font-weight: 800; font-size: 12px; }
  .seccion { background: #1a4d8f; color: #fff; font-weight: 700; padding: 4px 8px; font-size: 11px; margin: 10px 0 5px; border-radius: 3px; }
  .datos { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; font-size: 10px; margin-bottom: 5px; }
  .datos div { border: 1px solid #999; padding: 5px; }
  .datos b { color: #1a4d8f; }
  .costo-grid { display: grid; grid-template-columns: 1fr 240px; gap: 10px; align-items: start; }
  .firma { display: grid; grid-template-columns: 1fr 1fr; gap: 30px; margin-top: 18px; text-align: center; font-size: 10px; }
  .firma .caja { position: relative; border-top: 1px solid #333; padding-top: 4px; margin-top: 34px; }
  .firma b { font-size: 11px; }
  .nota { font-size: 8.5px; color: #777; margin-top: 10px; border-top: 1px dashed #bbb; padding-top: 5px; }
  .derecha { text-align: right; }
  @page { size: A4; margin: 12mm; }
</style></head><body>
  <h1>RECIBO DE REMUNERACIONES — CONTRATISTA DE VIÑAS Y FRUTALES</h1>
  <div class="sub">Según Anexo III · Decreto 407/2026 · Ley 27.802 · Ley 23.154 (Estatuto Contratista de Viñas)</div>

  <div class="seccion">1 · DATOS DEL EMPLEADOR Y DEL TRABAJADOR</div>
  <div class="datos">
    <div><b>Empleador:</b> ${d.empleador_nombre}<br><b>CUIT:</b> ${d.cuit_empleador}<br><b>Domicilio:</b> ${d.domicilio_empleador || '—'}</div>
    <div><b>Contratista:</b> ${d.nombre}<br><b>CUIL:</b> ${d.cuil}<br><b>Legajo:</b> ${d.legajo || '—'} · <b>Ingreso:</b> ${d.fecha_ingreso || '—'}<br><b>Obra Social:</b> ${d.obra_social || 'OSPRERA'}</div>
    <div><b>Período:</b> ${d.periodo}<br><b>Viñedo/Contrato:</b> N° ${d.numero_viniedo || '—'}<br><b>Modalidad:</b> ${d.modalidad || 'Promovida'}</div>
    <div><b>Categoría:</b> CONTRATISTAS<br><b>Concepto:</b> ${d.concepto}<br><b>Lugar de pago:</b> ${d.lugar_pago || '—'} · <b>Fecha pago:</b> ${d.fecha_pago || '—'}</div>
  </div>

  <div class="seccion">2 · COSTO TOTAL DEL EMPLEADOR (CONTRIBUCIONES)</div>
  <div class="costo-grid">
    <table>
      <tr><th>Concepto</th><th>Unidad</th><th>Base</th><th class="derecha">Monto</th></tr>
      ${rowsContrib}
      <tr class="total-row"><td colspan="3">SUBTOTAL CONTRIBUCIONES EMPLEADOR</td><td class="derecha">${fmt(subtotalContrib)}</td></tr>
    </table>
    <div style="text-align:center">
      <svg viewBox="0 0 200 200" width="150" height="150">
        <circle cx="100" cy="100" r="85" fill="#d4edda"/>
        <path d="M100 100 L100 15 A85 85 0 0 1 183 62 Z" fill="#1a4d8f"/>
        <path d="M100 100 L183 62 A85 85 0 0 1 185 115 Z" fill="#f39c12"/>
        <path d="M100 100 L185 115 A85 85 0 0 1 128 181 Z" fill="#27ae60"/>
        <path d="M100 100 L128 181 A85 85 0 0 1 62 172 Z" fill="#8e44ad"/>
        <path d="M100 100 L62 172 A85 85 0 0 1 15 100 Z" fill="#e74c3c"/>
        <circle cx="100" cy="100" r="40" fill="#fff"/>
      </svg>
      <div style="font-size:8.5px;margin-top:2px"><b>Gráfico N° 1 — Costo total empleador</b></div>
      <div style="font-size:7.5px;text-align:left;margin-top:3px">
        🟢 Neto 73% · 🔵 Jubilación 10% · 🟠 Obra social 6% · 🟣 INSSJP 5% · 🔴 ART 4%
      </div>
    </div>
  </div>

  <div class="seccion">3 · SUELDO BRUTO Y DEDUCCIONES</div>
  <table>
    <tr><th>Cód.</th><th>Concepto</th><th>Unidad</th><th>Rem. C/D</th><th>Rem. S/D</th><th class="derecha">Deducciones</th></tr>
    <tr><td>100</td><td>${d.concepto}</td><td>5,00</td><td>${fmt(d.remunerativo)}</td><td>${fmt(d.no_remunerativo)}</td><td class="derecha">—</td></tr>
    ${rowsDed}
    <tr class="total-row"><td colspan="3">TOTALES</td><td>${fmt(d.remunerativo)}</td><td>${fmt(d.no_remunerativo)}</td><td class="derecha">${fmt(totalDed)}</td></tr>
    <tr class="neto-row"><td colspan="5" style="text-align:right">SUELDO NETO</td><td class="derecha">${fmt(neto)}</td></tr>
  </table>

  <div class="seccion">4 · DETALLE DE LA COMPOSICIÓN DEL COSTO LABORAL</div>
  <table>
    <tr><th>Rubro</th><th class="derecha">Empleador</th><th class="derecha">Trabajador</th></tr>
    ${rowsCosto}
    <tr class="total-row"><td>TOTAL COSTO LABORAL</td><td class="derecha">${fmt(costoTotalEmp)}</td><td class="derecha">${fmt(costoTotalTrab)}</td></tr>
  </table>

  <div class="firma">
    <div class="caja">${firmaImg}<b>FIRMA CONTRATISTA</b><br><span style="font-size:8.5px;color:#666">${d.nombre || ''} · ${d.firma_fecha || ''}</span></div>
    <div class="caja"><b>FIRMA EMPLEADOR</b><br><span style="font-size:8.5px;color:#666">${d.empleador_nombre || ''}</span></div>
  </div>

  <div class="nota">
    RECIBÍ CONFORME LA SUMA DE: ${numeroALetras(neto)}, en concepto de remuneraciones correspondientes al período arriba indicado (${d.periodo}).
    <br><b>Nota:</b> Porcentajes de contribuciones empleador (ART/SIPA/OS) según valores vigentes — verificables contra ARCA/ANSES/paritaria. Firma digital incorporada con fecha/hora.
  </div>
</body></html>`;
}

function fmt(n) {
    if (n === null || n === undefined) return '—';
    return n.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function numeroALetras(n) {
    return `PESOS ${Math.round(n).toLocaleString('es-AR')} CON 00/100`;
}

/**
 * Genera el PDF renderizando el HTML aprobado.
 * @param {object} d - datos del recibo
 * @returns {Promise<{path:string, neto:number}>}
 */
async function generarReciboPDF(d) {
    const totalDed = (d.deducciones || []).reduce((s, x) => s + x.monto, 0);
    const neto = (d.remunerativo || 0) + (d.no_remunerativo || 0) - totalDed;
    d.firma_fecha = d.firma_fecha || new Date().toLocaleString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' });

    const html = buildHTML(d, neto);
    const outDir = '/opt/recibos-vina/generados';
    fs.mkdirSync(outDir, { recursive: true });
    const outPath = path.join(outDir, `recibo_${(d.periodo || 'x').replace(/\s+/g, '_')}_${d.cuil || 'x'}.pdf`);

    const browser = await puppeteer.launch({ args: ['--no-sandbox', '--disable-setuid-sandbox'] });
    try {
        const page = await browser.newPage();
        await page.setContent(html, { waitUntil: 'networkidle0' });
        await page.pdf({ path: outPath, format: 'A4', printBackground: true, margin: { top: '8mm', bottom: '8mm', left: '8mm', right: '8mm' } });
    } finally {
        await browser.close();
    }
    return { path: outPath, neto };
}

module.exports = { generarReciboPDF };
