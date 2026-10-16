import { useCallback, useRef, useState } from 'react';
import { ErrorApi } from '@/compartido/api/clienteApi';
import type { MensajeDeBloque } from '@/compartido/componentes/MensajeBloque';
import { textos } from '@/compartido/textos/textos';

/** Qué mostrar cuando el guardado sale bien. */
export interface OpcionesGuardar<T> {
  /** Texto de éxito del bloque (por ejemplo, "Tienda guardada."), o una función que lo arma con la respuesta. */
  exito: string | ((resultado: T) => string);
  /** Si devuelve un texto, se muestra como aviso en lugar del éxito (por ejemplo, pestaña creada). */
  aviso?: (resultado: T) => string | null | undefined;
  /** Si hay errores de campo, suma el error de bloque "No se guardó. Hay un campo con error, marcado en rojo.". */
  avisarErroresDeCampo?: boolean;
  /** Se llama si el guardado falla, después de mostrar el error (por ejemplo, para recargar una lista). */
  alFallar?: (error: unknown) => void;
}

/**
 * Estado común de un bloque que se guarda solo: "Guardando…", el mensaje del bloque, los errores por campo
 * (con foco en el primero) y si quedaron cambios sin guardar. Los errores de campo usan las claves de la API.
 * Ejemplo:
 * `const bloque = useGuardarBloque();`
 * `<input ref={bloque.registrarCampo('nombre')} />` · `await bloque.guardar(() => guardarTienda(nombre, false), { exito: 'Tienda guardada.' })`
 */
export function useGuardarBloque() {
  const [guardando, setGuardando] = useState(false);
  const [mensaje, setMensaje] = useState<MensajeDeBloque | null>(null);
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [sinGuardar, setSinGuardar] = useState(false);
  const enCurso = useRef(false);
  // Campos registrados en orden visual: el foco va al primero que tenga error.
  const campos = useRef(new Map<string, HTMLElement>());
  const registros = useRef(new Map<string, (elemento: HTMLElement | null) => void>());

  /** Devuelve el `ref` de un campo, para poder ponerle el foco si tiene error. */
  const registrarCampo = useCallback((nombre: string) => {
    let registro = registros.current.get(nombre);
    if (!registro) {
      registro = (elemento: HTMLElement | null) => {
        if (elemento) {
          campos.current.set(nombre, elemento);
        }
      };
      registros.current.set(nombre, registro);
    }
    return registro;
  }, []);

  /** Borra el mensaje y los errores del bloque. */
  const limpiar = useCallback(() => {
    setMensaje(null);
    setErrores({});
  }, []);

  /**
   * Marca errores de campo (por ejemplo, de una validación en pantalla) y pone el foco en el primero.
   * Con `avisarBloque`, suma el error de bloque "No se guardó. Hay un campo con error, marcado en rojo.".
   */
  const marcarErrores = useCallback((nuevos: Record<string, string>, avisarBloque = false) => {
    setMensaje(
      avisarBloque ? { tipo: 'error', texto: textos['general.error.corregir-campo'] } : null,
    );
    setErrores(nuevos);
    const primero = [...campos.current.keys()].find((nombre) => nuevos[nombre]);
    if (primero) {
      campos.current.get(primero)?.focus();
    }
  }, []);

  /** Avisa que se editó el bloque: queda sin guardar y se va el mensaje de éxito (errores y avisos se quedan). */
  const marcarEditado = useCallback(() => {
    setSinGuardar(true);
    setMensaje((actual) => (actual?.tipo === 'exito' ? null : actual));
  }, []);

  /** Ejecuta el guardado (ignora un segundo clic mientras guarda) y muestra el resultado en el bloque. */
  const guardar = useCallback(
    async <T>(accion: () => Promise<T>, opciones: OpcionesGuardar<T>): Promise<T | undefined> => {
      if (enCurso.current) {
        return undefined;
      }
      enCurso.current = true;
      setGuardando(true);
      setMensaje(null);
      setErrores({});
      try {
        const resultado = await accion();
        const aviso = opciones.aviso?.(resultado);
        const exito =
          typeof opciones.exito === 'function' ? opciones.exito(resultado) : opciones.exito;
        setMensaje(aviso ? { tipo: 'aviso', texto: aviso } : { tipo: 'exito', texto: exito });
        setSinGuardar(false);
        return resultado;
      } catch (error) {
        // eslint-disable-next-line react-hooks/immutability -- HU-013: mostrarError se declara después; el guardado pasa a useMutation
        mostrarError(error, opciones.avisarErroresDeCampo ?? false);
        opciones.alFallar?.(error);
        return undefined;
      } finally {
        enCurso.current = false;
        setGuardando(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps -- HU-013: mostrarError solo usa refs y funciones estables; el guardado pasa a useMutation
    [],
  );

  function mostrarError(error: unknown, avisarErroresDeCampo: boolean) {
    if (!(error instanceof ErrorApi)) {
      setMensaje({ tipo: 'error', texto: textos['general.error.inesperado'] });
      return;
    }
    const todos = Object.entries(error.errores);
    const deCampos = Object.fromEntries(todos.filter(([nombre]) => campos.current.has(nombre)));
    const sueltos = todos
      .filter(([nombre]) => !campos.current.has(nombre))
      .map(([, texto]) => texto);
    const hayDeCampos = Object.keys(deCampos).length > 0;
    if (hayDeCampos) {
      marcarErrores(deCampos, avisarErroresDeCampo);
    }
    if (sueltos.length > 0) {
      setMensaje({ tipo: 'error', texto: sueltos.join(' ') });
    } else if (!hayDeCampos) {
      setMensaje({ tipo: 'error', texto: error.message, urlInicioSesion: error.urlInicioSesion });
    }
  }

  return {
    guardando,
    mensaje,
    errores,
    sinGuardar,
    registrarCampo,
    limpiar,
    marcarErrores,
    marcarEditado,
    guardar,
  };
}
