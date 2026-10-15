import type { components } from './tiposApi';
import { textos } from '@/compartido/textos/textos';

/** ProblemDetail de la API (docs/v2/api/compartido.md) con las propiedades extra que usan los módulos. */
export type ProblemaApi = components['schemas']['ProblemDetail'] & {
  /** Solo en 400 de validación: campo → mensaje de textos.md. */
  errores?: Record<string, string>;
  /** Solo en 401 de Google: adónde ir para volver a iniciar sesión. */
  urlInicioSesion?: string;
};

/**
 * Error de una llamada a la API, con lo que hace falta para mostrarlo: código, mensaje para el usuario
 * (`detail` del ProblemDetail), errores por campo y, si la sesión de Google venció, adónde volver a iniciarla.
 * Ejemplo: `if (error instanceof ErrorApi && error.estado === 401) irA(error.urlInicioSesion)`
 */
export class ErrorApi extends Error {
  readonly estado: number;
  readonly errores: Record<string, string>;
  readonly urlInicioSesion?: string;

  constructor(estado: number, mensaje: string, errores: Record<string, string> = {}, urlInicioSesion?: string) {
    super(mensaje);
    this.name = 'ErrorApi';
    this.estado = estado;
    this.errores = errores;
    this.urlInicioSesion = urlInicioSesion;
  }
}

/**
 * Llama a la API v2 y devuelve el JSON de la respuesta. Si falla, lanza un `ErrorApi`: con el `detail` del
 * ProblemDetail, o con "Revisá tu conexión…" si no hubo respuesta (estado 0).
 * Ejemplo: `const config = await pedirApi<Configuracion>('/api/v1/configuracion')`
 */
export async function pedirApi<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  let respuesta: Response;
  try {
    respuesta = await fetch(ruta, { ...opciones, headers: { Accept: 'application/json', ...opciones.headers } });
  } catch {
    throw new ErrorApi(0, textos['general.error.conexion']);
  }
  if (!respuesta.ok) {
    throw await errorDeRespuesta(respuesta);
  }
  return (await respuesta.json()) as T;
}

/**
 * Atajo para mandar un JSON con PUT o POST.
 * Ejemplo: `enviarJson<BloquePuntos>('/api/v1/configuracion/puntos', 'PUT', { pesosPorPunto: 500 })`
 */
export function enviarJson<T>(ruta: string, metodo: 'PUT' | 'POST', cuerpo: unknown): Promise<T> {
  return pedirApi<T>(ruta, {
    method: metodo,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(cuerpo),
  });
}

async function errorDeRespuesta(respuesta: Response): Promise<ErrorApi> {
  try {
    const problema = (await respuesta.json()) as ProblemaApi;
    return new ErrorApi(
      respuesta.status,
      problema.detail ?? textos['general.error.inesperado'],
      problema.errores ?? {},
      problema.urlInicioSesion,
    );
  } catch {
    return new ErrorApi(respuesta.status, textos['general.error.inesperado']);
  }
}
