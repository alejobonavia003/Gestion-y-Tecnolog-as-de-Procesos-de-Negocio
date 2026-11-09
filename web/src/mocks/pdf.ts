// Genera un PDF mínimo (una página de texto) para simular los PDF del backend.
// Solo se usa en modo mock: el PDF real lo genera el backend.

function escapar(texto: string) {
  // Helvetica estándar no tiene tildes en esta codificación simple: las sacamos
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[\\()]/g, (c) => `\\${c}`)
}

export function generarPdf(lineas: string[]): Blob {
  const texto = lineas
    .map((linea, i) => `BT /F1 ${i === 0 ? 16 : 11} Tf 50 ${780 - i * 18} Td (${escapar(linea)}) Tj ET`)
    .join('\n')

  const objetos = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${texto.length} >>\nstream\n${texto}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ]

  let pdf = '%PDF-1.4\n'
  const posiciones: number[] = []
  objetos.forEach((obj, i) => {
    posiciones.push(pdf.length)
    pdf += `${i + 1} 0 obj\n${obj}\nendobj\n`
  })
  const inicioXref = pdf.length
  pdf += `xref\n0 ${objetos.length + 1}\n0000000000 65535 f \n`
  pdf += posiciones.map((p) => `${String(p).padStart(10, '0')} 00000 n \n`).join('')
  pdf += `trailer\n<< /Size ${objetos.length + 1} /Root 1 0 R >>\nstartxref\n${inicioXref}\n%%EOF`

  return new Blob([pdf], { type: 'application/pdf' })
}
