/**
 * Biblioteca de textos de la app: copia fiel de docs/v2/textos.md, con las mismas claves.
 * Los componentes no escriben textos sueltos: los toman de acá.
 * Ejemplo: `textos['menu.inventario']` → "Inventario"
 */
export const textos = {
  // Menú lateral (HU-001)
  'menu.inventario': 'Inventario',
  'menu.reservas': 'Reservas',
  'menu.caja': 'Caja',
  'menu.movimientos': 'Movimientos',
  'menu.configuracion': 'Configuración',

  // Marco de la app (HU-002). Pendientes de sumar a docs/v2/textos.md:
  // la etiqueta accesible del menú (la del prototipo) y el nombre por defecto de la v1,
  // que se usa hasta que Configuración (HU-003) entregue el nombre real de la tienda.
  'menu.etiqueta.menu-principal': 'Menú principal',
  'general.texto.nombre-tienda-por-defecto': 'Inventory Manager',
} as const;

/** Clave válida de la biblioteca de textos. */
export type ClaveTexto = keyof typeof textos;

/**
 * Devuelve el texto de una clave y reemplaza los `{datos}` que tenga.
 * Ejemplo: `texto('configuracion.sheet.aviso.creada', { pestana: 'Stock' })`
 */
export function texto(clave: ClaveTexto, datos: Record<string, string | number> = {}): string {
  return Object.entries(datos).reduce<string>(
    (resultado, [nombre, valor]) => resultado.replaceAll(`{${nombre}}`, String(valor)),
    textos[clave],
  );
}
