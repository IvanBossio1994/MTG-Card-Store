/**
 * Biblioteca de textos de la app: copia fiel de docs/v2/textos.md, con las mismas claves.
 * Los componentes no escriben textos sueltos: los toman de acá.
 * Ejemplo: `textos['menu.inventario']` → "Inventario"
 */
export const textos = {
  // Generales (HU-001, HU-003)
  'general.boton.reintentar': 'Reintentar',
  'general.boton.guardando': 'Guardando…',
  'general.carga.sincronizando-sheet': 'Sincronizando con tu Sheet…',
  'general.error.mayor-a-cero': 'Tiene que ser mayor a 0.',
  'general.error.sin-centavos': 'Usá un entero, sin centavos.',
  'general.error.corregir-campo': 'No se guardó. Corregí el campo marcado en rojo.',
  'general.error.inesperado': 'No pudimos guardar por un error inesperado. Probá de nuevo en un momento.',
  'general.error.conexion': 'Revisá tu conexión a internet e intentá de nuevo.',
  'general.error.sesion-vencida': 'Tu sesión de Google venció.',
  'general.boton.volver-a-iniciar-sesion': 'Volver a iniciar sesión',
  'general.error.sesion-ilegible':
    'La app no puede leer tu sesión de Google en esta PC. Abrila sin permisos de administrador y volvé a iniciar sesión.',
  'general.modal.sin-guardar-titulo': 'Tenés cambios sin guardar',
  'general.modal.sin-guardar-pregunta': '¿Salir igual?',
  'general.boton.seguir-editando': 'Seguir editando',
  'general.boton.salir-igual': 'Salir igual',

  // Menú lateral (HU-001, HU-002)
  'menu.inventario': 'Inventario',
  'menu.reservas': 'Reservas',
  'menu.caja': 'Caja',
  'menu.movimientos': 'Movimientos',
  'menu.configuracion': 'Configuración',
  'menu.etiqueta.menu-principal': 'Menú principal',
  'general.texto.nombre-tienda-por-defecto': 'Inventory Manager',

  // Configuración (HU-001)
  'configuracion.titulo': 'Configuración',
  'configuracion.bajada': 'Cada bloque se guarda por separado. Lo que escribas en los otros no se pierde.',
  'configuracion.carga': 'Cargando la configuración…',
  'configuracion.error.cargar-titulo': 'No pudimos cargar la configuración',

  // Configuración · Tienda
  'configuracion.tienda.titulo': 'Tu tienda',
  'configuracion.tienda.bajada': 'El nombre y el logo se ven en toda la app.',
  'configuracion.tienda.etiqueta.nombre': 'Nombre de la tienda',
  'configuracion.tienda.ejemplo.nombre': 'Ej.: Mazo Mágico',
  'configuracion.tienda.etiqueta.logo': 'Logo',
  'configuracion.tienda.ayuda.logo': 'PNG, JPG o WEBP, hasta 2 MB.',
  'configuracion.tienda.boton.subir-logo': 'Subir logo',
  'configuracion.tienda.boton.quitar-logo': 'Quitar',
  'configuracion.tienda.boton.guardar': 'Guardar tienda',
  'configuracion.tienda.exito': 'Tienda guardada.',
  'configuracion.tienda.error.nombre-vacio': 'Ingresá el nombre de la tienda.',
  'configuracion.tienda.error.logo-pesado': 'Usá un logo de hasta 2 MB.',
  'configuracion.tienda.error.logo-formato': 'Usá un PNG, JPG o WEBP.',

  // Configuración · Sheet
  'configuracion.sheet.titulo': 'Inventario en Google Sheets',
  'configuracion.sheet.bajada': 'Tu Google Sheet es la base de datos de la tienda.',
  'configuracion.sheet.texto.conectado': 'Conectado como {gmail}',
  'configuracion.sheet.etiqueta.link': 'ID o URL del Sheet',
  'configuracion.sheet.ayuda.link': 'Pegá la URL o solo el ID.',
  'configuracion.sheet.ejemplo.link': 'Ej.: 1vSk7YvkaEKOsomY7hnRG… o la URL',
  'configuracion.sheet.etiqueta.pestana': 'Pestaña del inventario',
  'configuracion.sheet.ejemplo.pestana': 'Ej.: Inventario',
  'configuracion.sheet.ayuda.pestana': 'Si cambiás el nombre, renombramos la pestaña actual. Si no hay ninguna, la creamos.',
  'configuracion.sheet.boton.guardar': 'Guardar inventario',
  'configuracion.sheet.exito': 'Inventario guardado.',
  'configuracion.sheet.aviso.renombrada': 'La pestaña «{anterior}» se renombró a «{pestana}».',
  'configuracion.sheet.aviso.creada': 'Se creó la pestaña «{pestana}».',
  'configuracion.sheet.error.vacio': 'Pegá el ID o la URL del Sheet.',
  'configuracion.sheet.error.invalido': 'Pegá una URL de Google Sheets o solo el ID.',
  'configuracion.sheet.error.sin-permiso': 'La app no tiene acceso a este Sheet. Compartilo con {cuenta-servicio} como editor.',
  'configuracion.sheet.error.pestana-vacia': 'Ingresá el nombre de la pestaña.',

  // Configuración · Precios
  'configuracion.precios.titulo': 'Regla de precio',
  'configuracion.precios.bajada': 'Cómo se pasa el precio de Card Kingdom (USD) a pesos.',
  'configuracion.precios.etiqueta.cotizacion': 'Cotización (ARS por USD)',
  'configuracion.precios.ejemplo.cotizacion': 'Ej.: 1350',
  'configuracion.precios.etiqueta.redondeo': 'Redondear al múltiplo de ($)',
  'configuracion.precios.ejemplo.redondeo': 'Ej.: 500',
  'configuracion.precios.ejemplo.bulk': 'Ej.: 50',
  'configuracion.precios.etiqueta.cache': 'Carpeta de cache CK (opcional)',
  'configuracion.precios.ejemplo.cache': 'Ej.: C:\\TCG-inventory\\cache',
  'configuracion.precios.titulo.bulk': 'Precio bulk por rareza ($, sin centavos)',
  'configuracion.precios.etiqueta.common': 'Common',
  'configuracion.precios.etiqueta.uncommon': 'Uncommon',
  'configuracion.precios.etiqueta.rare': 'Rare',
  'configuracion.precios.etiqueta.mythic': 'Mythic',
  'configuracion.precios.etiqueta.tierra-basica': 'Tierra básica',
  'configuracion.precios.boton.guardar': 'Guardar precios',
  'configuracion.precios.exito': 'Precios guardados. Se recalculan.',
  'configuracion.precios.error.cotizacion-vacia': 'Ingresá la cotización del dólar.',
  'configuracion.precios.error.cotizacion-menor': 'La cotización tiene que ser mayor a 0.',
  'configuracion.precios.error.redondeo-vacio': 'Ingresá el múltiplo para redondear.',
  'configuracion.precios.error.redondeo-menor': 'El múltiplo tiene que ser mayor a 0.',
  'configuracion.precios.error.bulk-vacio': 'Ingresá el precio.',
  'configuracion.precios.error.carpeta': 'Esa carpeta no existe. Elegí otra o dejala vacía.',
  'configuracion.precios.aviso.falla-ck': 'Guardado, pero no se pudo sincronizar con Card Kingdom. Probá más tarde.',

  // Configuración · Puntos
  'configuracion.puntos.titulo': 'Puntos de clientes',
  'configuracion.puntos.bajada': 'Cuánto tiene que gastar un cliente para sumar un punto.',
  'configuracion.puntos.etiqueta.pesos': 'Pesos por punto ($)',
  'configuracion.puntos.ejemplo.pesos': 'Ej.: 1000',
  'configuracion.puntos.texto.ejemplo': 'Una compra de $25.000 suma {puntos} puntos.',
  'configuracion.puntos.texto.ejemplo-vacio': 'Escribí cuántos pesos vale un punto.',
  'configuracion.puntos.boton.guardar': 'Guardar puntos',
  'configuracion.puntos.error.vacio': 'Ingresá cuántos pesos vale un punto.',
  'configuracion.puntos.exito': 'Puntos guardados.',
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
