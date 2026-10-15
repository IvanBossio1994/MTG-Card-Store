import type { components } from '@/compartido/api/tiposApi';
import { enviarJson, pedirApi } from '@/compartido/api/clienteApi';

// Todas las llamadas del módulo Configuración (contrato: docs/v2/api/configuracion.md).
// Los campos que el contrato marca "puede ser null" llegan en null: se leen con `?? valorPorDefecto`.

type Esquemas = components['schemas'];

/** Configuración completa, por bloques (GET /api/v1/configuracion). */
export type Configuracion = Esquemas['ConfiguracionRespuesta'];
/** Bloque tienda: nombre y dirección del logo. */
export type BloqueTienda = Esquemas['BloqueTienda'];
/** Bloque Sheet: ID, link, pestaña, Gmail conectado y, al guardar, si creó la pestaña y si trajo cartas. */
export type BloqueSheet = Esquemas['BloqueSheet'];
/** Una pestaña del Sheet: nombre, si es la del inventario (`enUso`), si parece de inventario (`sugerida`) y si es `fija`. */
export type Pestana = Esquemas['PestanaRespuesta'];
/** Lista de pestañas (GET /sheet/pestanas): `sheetGuardado` false si no hay Sheet para listar. */
export type ListaPestanas = Esquemas['ListaPestanasRespuesta'];
/** Respuesta de renombrar: nombres, si era la del inventario y la lista actualizada. */
export type PestanaRenombrada = Esquemas['PestanaRenombradaRespuesta'];
/** Bloque precios: cotización, redondeo, bulk, carpeta de cache y aviso de Card Kingdom. */
export type BloquePrecios = Esquemas['BloquePrecios'];
/** Bloque puntos: pesos por punto. */
export type BloquePuntos = Esquemas['BloquePuntos'];
/** Pedido de PUT /precios. */
export type PreciosPedido = Esquemas['PreciosPedido'];

const BASE = '/api/v1/configuracion';

/** Peso máximo del logo (2 MB), igual que la API. */
export const PESO_MAXIMO_LOGO = 2 * 1024 * 1024;
/** Formatos de logo que acepta la API. */
export const FORMATOS_LOGO = ['image/png', 'image/jpeg', 'image/webp'];

/**
 * Lee los cuatro bloques guardados y el Gmail conectado.
 * Ejemplo: `const { tienda, sheet } = await leerConfiguracion()`
 */
export function leerConfiguracion(): Promise<Configuracion> {
  return pedirApi<Configuracion>(BASE);
}

/**
 * Guarda el nombre de la tienda y, si se pide, quita el logo.
 * Ejemplo: `await guardarTienda('Codex Live Store', false)`
 */
export function guardarTienda(nombre: string, quitarLogo: boolean): Promise<BloqueTienda> {
  return enviarJson<BloqueTienda>(`${BASE}/tienda`, 'PUT', { nombre, quitarLogo });
}

/**
 * Sube el logo de la tienda (reemplaza al anterior) y devuelve la dirección nueva para mostrarlo.
 * Ejemplo: `const { logoUrl } = await subirLogo(archivoElegido)`
 */
export function subirLogo(archivo: File): Promise<Esquemas['LogoRespuesta']> {
  const formulario = new FormData();
  formulario.append('archivo', archivo);
  return pedirApi<Esquemas['LogoRespuesta']>(`${BASE}/tienda/logo`, { method: 'POST', body: formulario });
}

/**
 * Conecta el Google Sheet y deja lista la pestaña del inventario. Si la pestaña es nueva y viene `traerCartasDe`,
 * la crea con las cartas de esa pestaña (`cartasTraidas`); si la crea sin cartas, `aviso` lo dice.
 * Ejemplo: `const { cartasTraidas } = await conectarSheet('https://docs.google.com/spreadsheets/d/1vSk…/edit', 'Stock 2026', 'stock viejo')`
 */
export function conectarSheet(sheet: string, pestanaInventario: string, traerCartasDe?: string): Promise<BloqueSheet> {
  const pedido: Esquemas['SheetPedido'] = { sheet, pestanaInventario };
  if (traerCartasDe) {
    pedido.traerCartasDe = traerCartasDe;
  }
  return enviarJson<BloqueSheet>(`${BASE}/sheet`, 'PUT', pedido);
}

/**
 * Lista las pestañas del Sheet guardado o, si se pasa `idOLink`, las de ese Sheet (aunque no esté guardado).
 * Ejemplo: `const { sheetGuardado, pestanas } = await listarPestanas()` · `await listarPestanas('1vSk7YvkaEKOsomY7hnRG…')`
 */
export function listarPestanas(idOLink?: string): Promise<ListaPestanas> {
  const consulta = idOLink ? `?${new URLSearchParams({ idOLink })}` : '';
  return pedirApi<ListaPestanas>(`${BASE}/sheet/pestanas${consulta}`);
}

/**
 * Le cambia el nombre a una pestaña del Sheet guardado. Si era la del inventario, la API ya guardó el nombre nuevo.
 * Ejemplo: `const { eraPestanaInventario, pestanas } = await renombrarPestana('Hoja 1', 'Stock 2026')`
 */
export function renombrarPestana(nombreActual: string, nombreNuevo: string): Promise<PestanaRenombrada> {
  return enviarJson<PestanaRenombrada>(`${BASE}/sheet/pestanas/renombrar`, 'PUT', { nombreActual, nombreNuevo });
}

/**
 * Guarda la regla de precio; `aviso` viene si se guardó pero falló Card Kingdom.
 * Ejemplo: `await guardarPrecios({ cotizacion: 1350, redondeo: 500, bulk: { common: 50, … }, carpetaCache: undefined })`
 */
export function guardarPrecios(pedido: PreciosPedido): Promise<BloquePrecios> {
  return enviarJson<BloquePrecios>(`${BASE}/precios`, 'PUT', pedido);
}

/**
 * Guarda cuántos pesos vale un punto de cliente.
 * Ejemplo: `await guardarPuntos(500)`
 */
export function guardarPuntos(pesosPorPunto: number): Promise<BloquePuntos> {
  return enviarJson<BloquePuntos>(`${BASE}/puntos`, 'PUT', { pesosPorPunto });
}
