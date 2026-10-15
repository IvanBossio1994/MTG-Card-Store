/**
 * Biblioteca de textos de la app: copia fiel de docs/v2/textos.md, con las mismas claves.
 * Los componentes no escriben textos sueltos: los toman de acá.
 * Ejemplo: `textos['menu.inventario']` → "Inventario"
 */
export const textos = {
  // Generales (HU-001, HU-003; tono HU-005)
  'general.boton.reintentar': 'Reintentar',
  'general.boton.guardando': 'Guardando…',
  'general.carga.sincronizando-sheet': 'Sincronizando con el Sheet…',
  'general.error.mayor-a-cero': 'Debe ser mayor a 0.',
  'general.error.sin-centavos': 'El precio debe ser un número entero, sin centavos.',
  'general.error.corregir-campo': 'No se guardó. Hay un campo con error, marcado en rojo.',
  'general.error.inesperado': 'No se pudo guardar por un error inesperado. Se puede reintentar en un momento.',
  'general.error.conexion': 'No hay conexión a internet. Se puede reintentar cuando se restablezca.',
  'general.error.sesion-vencida': 'La sesión de Google venció.',
  'general.boton.volver-a-iniciar-sesion': 'Volver a iniciar sesión',
  'general.error.sesion-ilegible':
    'La app no puede leer la sesión de Google en esta PC. Abrirla sin permisos de administrador e iniciar sesión de nuevo.',
  'general.modal.sin-guardar-titulo': 'Hay cambios sin guardar',
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

  // Configuración (HU-001, tono y Sheet rediseñados en HU-005)
  'configuracion.titulo': 'Configuración',
  'configuracion.bajada': 'Cada bloque se guarda por separado. Lo escrito en los otros no se pierde.',
  'configuracion.carga': 'Cargando la configuración…',
  'configuracion.error.cargar-titulo': 'No se pudo cargar la configuración.',

  // Configuración · Tienda
  'configuracion.tienda.titulo': 'Tienda',
  'configuracion.tienda.bajada': 'El nombre y el logo se ven en toda la app.',
  'configuracion.tienda.etiqueta.nombre': 'Nombre de la tienda',
  'configuracion.tienda.ejemplo.nombre': 'Ej.: Mazo Mágico',
  'configuracion.tienda.etiqueta.logo': 'Logo',
  'configuracion.tienda.ayuda.logo': 'PNG, JPG o WEBP, hasta 2 MB.',
  'configuracion.tienda.boton.subir-logo': 'Subir logo',
  'configuracion.tienda.boton.quitar-logo': 'Quitar',
  'configuracion.tienda.boton.guardar': 'Guardar tienda',
  'configuracion.tienda.exito': 'Tienda guardada.',
  'configuracion.tienda.error.nombre-vacio': 'El nombre de la tienda es obligatorio.',
  'configuracion.tienda.error.logo-pesado': 'El logo no debe superar los 2 MB.',
  'configuracion.tienda.error.logo-formato': 'El logo debe ser PNG, JPG o WEBP.',

  // Configuración · Sheet · fila de conectar
  'configuracion.sheet.titulo': 'Inventario en Google Sheets',
  'configuracion.sheet.bajada': 'El Google Sheet es la base de datos de la tienda.',
  'configuracion.sheet.texto.conectado': 'Conectado como {gmail}',
  'configuracion.sheet.etiqueta.link': 'ID o URL del Sheet',
  'configuracion.sheet.ayuda.link': 'Se acepta la URL completa o solo el ID.',
  'configuracion.sheet.ejemplo.link': 'Ej.: 1vSk7YvkaEKOsomY7hnRG… o la URL',
  'configuracion.sheet.etiqueta.pestana': 'Pestaña del inventario',
  'configuracion.sheet.ejemplo.pestana': 'Ej.: Inventario',
  'configuracion.sheet.ayuda.pestana': 'Si no existe, se crea. Si el Sheet no tiene ninguna, se crea la primera.',
  'configuracion.sheet.boton.guardar': 'Guardar inventario',
  'configuracion.sheet.exito': 'Inventario guardado.',
  'configuracion.sheet.aviso.creada': 'Se creó la pestaña «{pestana}».',
  'configuracion.sheet.traer.etiqueta': 'Traer cartas de',
  'configuracion.sheet.traer.ayuda': 'Solo si la pestaña del inventario es nueva. La de origen no se toca.',
  'configuracion.sheet.traer.opcion.ninguna': 'No traer cartas',
  'configuracion.sheet.traer.texto.sugerida': 'sugerida',
  'configuracion.sheet.traer.texto.sin-sheet': 'Sin Sheet',
  'configuracion.sheet.traer.ayuda.sin-sheet': 'Disponible cuando se escribe el link del Sheet.',
  'configuracion.sheet.traer.ayuda.ninguna-reconocida': 'Ninguna pestaña tiene encabezados de inventario.',
  'configuracion.sheet.traer.error.lista': 'No se pudieron cargar las pestañas del Sheet.',
  'configuracion.sheet.traer.error.origen-inexistente': 'La pestaña de origen ya no existe en el Sheet. Se actualizó la lista.',
  'configuracion.sheet.traer.carga': 'Trayendo las cartas…',
  'configuracion.sheet.traer.exito': 'Se creó la pestaña «{pestana}» con {n} cartas de «{origen}».',
  'configuracion.sheet.traer.exito-columnas-extra': 'Las columnas que la app no usa quedaron al final.',
  'configuracion.sheet.error.vacio': 'El ID o la URL del Sheet es obligatorio.',
  'configuracion.sheet.error.invalido': 'Debe ser una URL de Google Sheets o solo el ID.',
  'configuracion.sheet.error.sin-permiso': 'La app no tiene acceso a este Sheet. Compartirlo con {cuenta-servicio} como editor.',
  'configuracion.sheet.error.pestana-vacia': 'El nombre de la pestaña es obligatorio.',

  // Configuración · Sheet · fila de renombrar (HU-005)
  'configuracion.sheet.renombrar.titulo': 'Renombrar una pestaña',
  'configuracion.sheet.renombrar.ayuda.nombre-nuevo': 'Hasta 100 caracteres.',
  'configuracion.sheet.renombrar.etiqueta.pestana': 'Pestaña a renombrar',
  'configuracion.sheet.renombrar.ayuda.pestana': 'Pestañas del Sheet guardado.',
  'configuracion.sheet.renombrar.ejemplo.pestana': 'Elegir una pestaña',
  'configuracion.sheet.renombrar.carga.lista': 'Cargando pestañas…',
  'configuracion.sheet.renombrar.texto.sin-sheet': 'Sin Sheet guardado',
  'configuracion.sheet.renombrar.ayuda.sin-sheet': 'Disponible cuando haya un Sheet guardado.',
  'configuracion.sheet.renombrar.texto.sin-pestanas': 'El Sheet no tiene pestañas.',
  'configuracion.sheet.renombrar.texto.en-uso': 'en uso',
  'configuracion.sheet.renombrar.error.lista': 'No se pudieron cargar las pestañas del Sheet.',
  'configuracion.sheet.renombrar.etiqueta.nombre-nuevo': 'Nombre nuevo',
  'configuracion.sheet.renombrar.ejemplo.nombre-nuevo': 'Ej.: Stock 2026',
  'configuracion.sheet.renombrar.boton': 'Renombrar pestaña',
  'configuracion.sheet.renombrar.carga': 'Renombrando la pestaña…',
  'configuracion.sheet.renombrar.exito': 'La pestaña «{anterior}» se renombró a «{pestana}».',
  'configuracion.sheet.aviso.renombrada':
    'La pestaña «{anterior}» se renombró a «{pestana}». También se actualizó la pestaña del inventario.',
  'configuracion.sheet.renombrar.error.sin-pestana': 'La pestaña a renombrar es obligatoria.',
  'configuracion.sheet.renombrar.error.nombre-vacio': 'El nombre nuevo es obligatorio.',
  'configuracion.sheet.renombrar.error.nombre-largo': 'El nombre no debe superar los 100 caracteres.',
  'configuracion.sheet.renombrar.error.nombre-igual': 'El nombre nuevo es igual al actual.',
  'configuracion.sheet.renombrar.error.nombre-repetido': 'Ya existe una pestaña con ese nombre.',
  'configuracion.sheet.renombrar.error.fija': 'Esa pestaña la usa la app y no se puede renombrar.',
  'configuracion.sheet.renombrar.error.pestana-inexistente': 'La pestaña ya no existe en el Sheet. Se actualizó la lista.',

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
  'configuracion.precios.error.cotizacion-vacia': 'La cotización del dólar es obligatoria.',
  'configuracion.precios.error.cotizacion-menor': 'La cotización debe ser mayor a 0.',
  'configuracion.precios.error.redondeo-vacio': 'El múltiplo para redondear es obligatorio.',
  'configuracion.precios.error.redondeo-menor': 'El múltiplo debe ser mayor a 0.',
  'configuracion.precios.error.bulk-vacio': 'El precio es obligatorio.',
  'configuracion.precios.error.carpeta': 'La carpeta no existe. Se puede indicar otra o dejar el campo vacío.',
  'configuracion.precios.aviso.falla-ck':
    'Guardado, pero no se pudo sincronizar con Card Kingdom. Se puede reintentar más tarde.',

  // Configuración · Puntos
  'configuracion.puntos.titulo': 'Puntos de clientes',
  'configuracion.puntos.bajada': 'Cuánto debe gastar un cliente para sumar un punto.',
  'configuracion.puntos.etiqueta.pesos': 'Pesos por punto ($)',
  'configuracion.puntos.ejemplo.pesos': 'Ej.: 1000',
  'configuracion.puntos.texto.ejemplo': 'Una compra de $25.000 suma {puntos} puntos.',
  'configuracion.puntos.texto.ejemplo-vacio': 'Falta indicar cuántos pesos vale un punto.',
  'configuracion.puntos.boton.guardar': 'Guardar puntos',
  'configuracion.puntos.error.vacio': 'Los pesos por punto son obligatorios.',
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
