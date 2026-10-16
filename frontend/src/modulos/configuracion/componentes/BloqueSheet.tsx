import { useCallback, useEffect, useRef, useState } from 'react';
import { Pencil, PlugZap, Save, Table2 } from 'lucide-react';
import { ErrorApi } from '@/compartido/api/clienteApi';
import { Boton } from '@/compartido/componentes/Boton';
import { CapaCarga } from '@/compartido/componentes/CapaCarga';
import { Campo } from '@/compartido/componentes/Campo';
import { ListaDesplegable, type OpcionLista } from '@/compartido/componentes/ListaDesplegable';
import { MensajeBloque } from '@/compartido/componentes/MensajeBloque';
import { Tarjeta } from '@/compartido/componentes/Tarjeta';
import { useGuardarBloque } from '@/compartido/hooks/useGuardarBloque';
import { texto, textos } from '@/compartido/textos/textos';
import {
  conectarSheet,
  listarPestanas,
  renombrarPestana,
  type BloqueSheet as DatosSheet,
  type Pestana,
} from '../configuracionApi';
import type { AlCambiarSinGuardar } from '../ConfiguracionPagina';

/** Largo máximo del nombre de una pestaña (límite de Google). */
const LARGO_MAXIMO_NOMBRE = 100;

/** Link completo de Google Sheets o solo el ID (letras, números, `-` y `_`, al menos 20), igual que la API. */
const FORMATO_LINK = /^(https?:\/\/docs\.google\.com\/spreadsheets\/d\/[\w-]+.*|[\w-]{20,})$/;

/** Estado de una lista de pestañas (spec 2.1): cargando, sin Sheet, falla al listar o con las pestañas. */
type EstadoLista =
  | { tipo: 'cargando' }
  | { tipo: 'sin-sheet' }
  | { tipo: 'error' }
  | { tipo: 'lista'; pestanas: Pestana[] };

/** Qué listas recargar con una misma llamada. */
interface Destinos {
  traer?: boolean;
  renombrar?: boolean;
}

/**
 * Pestaña que se preselecciona en "Traer cartas de": la sugerida; si hay varias, la que está en uso o, si no, la primera.
 * Ejemplo: `origenSugerido([{ nombre: 'stock viejo', sugerida: true }])` → "stock viejo"
 */
function origenSugerido(pestanas: Pestana[]): string {
  const sugeridas = pestanas.filter((pestana) => pestana.sugerida);
  return (sugeridas.find((pestana) => pestana.enUso) ?? sugeridas[0])?.nombre ?? '';
}

/**
 * Éxito de traer cartas: "Se creó la pestaña «X» con N cartas de «Y»." (con 1, "1 carta"), más la frase de las
 * columnas extra si las hubo.
 */
function mensajeCartasTraidas(
  pestana: string,
  origen: string,
  cantidad: number,
  hayColumnasExtra: boolean,
): string {
  const base = texto('configuracion.sheet.traer.exito', { pestana, n: cantidad, origen });
  const conNumero = cantidad === 1 ? base.replace('1 cartas', '1 carta') : base;
  return hayColumnasExtra
    ? `${conNumero} ${textos['configuracion.sheet.traer.exito-columnas-extra']}`
    : conNumero;
}

export interface BloqueSheetProps {
  sheet: DatosSheet;
  alCambiarSinGuardar?: AlCambiarSinGuardar;
}

/**
 * Bloque "Inventario en Google Sheets" (spec 2.1), en dos filas con su resultado debajo de cada una:
 * conectar el Sheet (link, pestaña del inventario y "Traer cartas de") y renombrar una pestaña del Sheet guardado.
 * Lo que habla con Google usa la capa de carga; las listas de pestañas, la ruedita (CFG-F02, F03, A03 a A05, A07, A10 a A14).
 * Ejemplo: `<BloqueSheet sheet={configuracion.sheet} alCambiarSinGuardar={marcarSinGuardar} />`
 */
export function BloqueSheet({ sheet, alCambiarSinGuardar }: BloqueSheetProps) {
  const bloque = useGuardarBloque();
  const filaRenombrar = useGuardarBloque();
  const linkInicial = sheet.link ?? sheet.sheetId ?? '';
  const haySheetGuardado = Boolean(sheet.sheetId);
  const [linkOId, setLinkOId] = useState(linkInicial);
  const [pestana, setPestana] = useState(sheet.pestanaInventario ?? '');
  const [gmail, setGmail] = useState(sheet.gmail ?? null);
  const [origen, setOrigen] = useState('');
  const [trayendo, setTrayendo] = useState(false);
  const [listaTraer, setListaTraer] = useState<EstadoLista>({
    tipo: haySheetGuardado ? 'cargando' : 'sin-sheet',
  });
  const [listaRenombrar, setListaRenombrar] = useState<EstadoLista>({
    tipo: haySheetGuardado ? 'cargando' : 'sin-sheet',
  });
  const [pestanaElegida, setPestanaElegida] = useState('');
  const [nombreNuevo, setNombreNuevo] = useState('');
  // Cada lista ignora las respuestas de pedidos viejos (por ejemplo, si se cambió el link mientras cargaba).
  const pedidoTraer = useRef(0);
  const pedidoRenombrar = useRef(0);
  // De qué Sheet es la lista "Traer cartas de": null = el guardado; si no, el link escrito.
  const fuenteTraer = useRef<string | null>(null);
  const ultimoLinkListado = useRef(haySheetGuardado ? linkInicial : '');
  const { sinGuardar } = bloque;

  useEffect(() => {
    alCambiarSinGuardar?.('sheet', sinGuardar);
  }, [alCambiarSinGuardar, sinGuardar]);

  /** Pone una lista de "Traer cartas de" y preselecciona la sugerida. */
  const mostrarListaTraer = useCallback((sheetGuardado: boolean, pestanas: Pestana[]) => {
    setListaTraer(sheetGuardado ? { tipo: 'lista', pestanas } : { tipo: 'sin-sheet' });
    setOrigen(origenSugerido(pestanas));
  }, []);

  /** Pide las pestañas (del guardado o de `idOLink`) y las pone en las listas pedidas, con una sola llamada. */
  const cargarListas = useCallback(
    async (destinos: Destinos, idOLink?: string) => {
      const numeroTraer = destinos.traer ? ++pedidoTraer.current : -1;
      const numeroRenombrar = destinos.renombrar ? ++pedidoRenombrar.current : -1;
      if (destinos.traer) {
        fuenteTraer.current = idOLink ?? null;
        setListaTraer({ tipo: 'cargando' });
      }
      if (destinos.renombrar) {
        setListaRenombrar({ tipo: 'cargando' });
      }
      try {
        const { sheetGuardado = false, pestanas = [] } = await listarPestanas(idOLink);
        if (numeroTraer === pedidoTraer.current) {
          mostrarListaTraer(sheetGuardado, pestanas);
        }
        if (numeroRenombrar === pedidoRenombrar.current) {
          setListaRenombrar(sheetGuardado ? { tipo: 'lista', pestanas } : { tipo: 'sin-sheet' });
        }
      } catch {
        if (numeroTraer === pedidoTraer.current) {
          setListaTraer({ tipo: 'error' });
          setOrigen('');
        }
        if (numeroRenombrar === pedidoRenombrar.current) {
          setListaRenombrar({ tipo: 'error' });
        }
      }
    },
    [mostrarListaTraer],
  );

  // Al abrir, con un Sheet guardado, una sola llamada llena las dos listas.
  useEffect(() => {
    if (haySheetGuardado) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- HU-013: la carga de las listas pasa a useQuery
      void cargarListas({ traer: true, renombrar: true });
    }
  }, [haySheetGuardado, cargarListas]);

  /** Vuelve a pedir la lista "Traer cartas de" del mismo Sheet que antes. */
  function recargarListaTraer() {
    void cargarListas({ traer: true }, fuenteTraer.current ?? undefined);
  }

  /** Al salir del campo del link: si es válido y cambió, lista las pestañas de ese Sheet (nunca por cada tecla). */
  function alSalirDelLink() {
    const link = linkOId.trim();
    if (!link || !FORMATO_LINK.test(link) || link === ultimoLinkListado.current) {
      return;
    }
    ultimoLinkListado.current = link;
    void cargarListas({ traer: true }, link);
  }

  const sinLink = !linkOId.trim();
  const estadoTraer: EstadoLista = sinLink ? { tipo: 'sin-sheet' } : listaTraer;
  const pestanasTraer = estadoTraer.tipo === 'lista' ? estadoTraer.pestanas : [];
  const origenElegido = estadoTraer.tipo === 'lista' ? origen : '';

  async function guardar() {
    const linkLimpio = linkOId.trim();
    const pestanaLimpia = pestana.trim();
    const errores: Record<string, string> = {};
    if (!linkLimpio) {
      errores.sheet = textos['configuracion.sheet.error.vacio'];
    }
    if (!pestanaLimpia) {
      errores.pestanaInventario = textos['configuracion.sheet.error.pestana-vacia'];
    }
    if (Object.keys(errores).length > 0) {
      bloque.marcarErrores(errores);
      return;
    }
    // "Traer cartas de" solo cuenta si la pestaña del inventario es nueva; si ya existe, se usa tal cual.
    const pestanaExiste = pestanasTraer.some((existente) => existente.nombre === pestanaLimpia);
    const traerDe = origenElegido && !pestanaExiste ? origenElegido : undefined;
    setTrayendo(Boolean(traerDe));
    const conectado = await bloque.guardar(
      () => conectarSheet(linkLimpio, pestanaLimpia, traerDe),
      {
        exito: (respuesta) =>
          respuesta.cartasTraidas
            ? mensajeCartasTraidas(
                respuesta.pestanaInventario ?? pestanaLimpia,
                respuesta.cartasTraidas.origen ?? origenElegido,
                respuesta.cartasTraidas.cantidad ?? 0,
                respuesta.cartasTraidas.hayColumnasExtra ?? false,
              )
            : textos['configuracion.sheet.exito'],
        aviso: (respuesta) =>
          respuesta.pestanaCreada && !respuesta.cartasTraidas
            ? texto('configuracion.sheet.aviso.creada', {
                pestana: respuesta.pestanaInventario ?? pestanaLimpia,
              })
            : null,
        alFallar: (error) => {
          if (error instanceof ErrorApi && error.codigo === 'ORIGEN_INEXISTENTE') {
            recargarListaTraer();
          }
        },
      },
    );
    if (conectado) {
      const linkGuardado = conectado.link ?? linkLimpio;
      setLinkOId(linkGuardado);
      setPestana(conectado.pestanaInventario ?? pestanaLimpia);
      setGmail(conectado.gmail ?? gmail);
      ultimoLinkListado.current = linkGuardado;
      // El Sheet pudo cambiar o la pestaña se pudo crear: se recargan las dos listas.
      void cargarListas({ traer: true, renombrar: true });
    }
  }

  const pestanasRenombrar = listaRenombrar.tipo === 'lista' ? listaRenombrar.pestanas : [];
  const renombrarLista = pestanasRenombrar.length > 0;

  async function renombrar() {
    const actual = pestanaElegida;
    const nuevo = nombreNuevo.trim();
    const errores: Record<string, string> = {};
    if (!actual) {
      errores.nombreActual = textos['configuracion.sheet.renombrar.error.sin-pestana'];
    }
    if (!nuevo) {
      errores.nombreNuevo = textos['configuracion.sheet.renombrar.error.nombre-vacio'];
    } else if (nuevo.length > LARGO_MAXIMO_NOMBRE) {
      errores.nombreNuevo = textos['configuracion.sheet.renombrar.error.nombre-largo'];
    } else if (nuevo === actual) {
      errores.nombreNuevo = textos['configuracion.sheet.renombrar.error.nombre-igual'];
    } else if (
      pestanasRenombrar.some(
        (otra) =>
          otra.nombre !== actual && otra.nombre?.toLocaleLowerCase() === nuevo.toLocaleLowerCase(),
      )
    ) {
      errores.nombreNuevo = textos['configuracion.sheet.renombrar.error.nombre-repetido'];
    }
    if (Object.keys(errores).length > 0) {
      filaRenombrar.marcarErrores(errores);
      return;
    }
    const renombrada = await filaRenombrar.guardar(() => renombrarPestana(actual, nuevo), {
      exito: (respuesta) =>
        texto(
          respuesta.eraPestanaInventario
            ? 'configuracion.sheet.aviso.renombrada'
            : 'configuracion.sheet.renombrar.exito',
          {
            anterior: respuesta.nombreAnterior ?? actual,
            pestana: respuesta.nombreNuevo ?? nuevo,
          },
        ),
      alFallar: (error) => {
        if (error instanceof ErrorApi && error.codigo === 'PESTANA_INEXISTENTE') {
          setPestanaElegida('');
          void cargarListas({ renombrar: true });
        }
      },
    });
    if (renombrada) {
      const pestanas = renombrada.pestanas ?? [];
      setNombreNuevo('');
      setPestanaElegida('');
      pedidoRenombrar.current++;
      setListaRenombrar({ tipo: 'lista', pestanas });
      if (fuenteTraer.current === null) {
        pedidoTraer.current++;
        mostrarListaTraer(true, pestanas);
      }
      // La API ya guardó el nombre nuevo: el campo se actualiza sin quedar como cambio sin guardar.
      if (renombrada.eraPestanaInventario && renombrada.nombrePestanaInventario) {
        setPestana(renombrada.nombrePestanaInventario);
      }
    }
  }

  const opcionesTraer: OpcionLista[] = pestanasTraer.map(({ nombre = '', sugerida }) => ({
    valor: nombre,
    texto: sugerida ? `${nombre} · ${textos['configuracion.sheet.traer.texto.sugerida']}` : nombre,
  }));
  const ningunaReconocida =
    estadoTraer.tipo === 'lista' && !pestanasTraer.some((existente) => existente.sugerida);

  const opcionesRenombrar: OpcionLista[] = pestanasRenombrar.map(
    ({ nombre = '', enUso, fija }) => ({
      valor: nombre,
      texto: enUso ? `${nombre} · ${textos['configuracion.sheet.renombrar.texto.en-uso']}` : nombre,
      deshabilitada: fija,
    }),
  );

  const textoCapa = filaRenombrar.guardando
    ? textos['configuracion.sheet.renombrar.carga']
    : trayendo
      ? textos['configuracion.sheet.traer.carga']
      : textos['general.carga.sincronizando-sheet'];

  return (
    <Tarjeta
      Icono={Table2}
      titulo={textos['configuracion.sheet.titulo']}
      bajada={textos['configuracion.sheet.bajada']}
      className="col-span-2 max-[1100px]:col-span-1"
      detalle={
        gmail && (
          <p
            className="mb-4 inline-flex items-center gap-1 text-[13px] font-semibold text-exito"
            data-testid="configuracion-texto-gmail-conectado"
          >
            <PlugZap className="h-4 w-4" aria-hidden="true" />
            {texto('configuracion.sheet.texto.conectado', { gmail })}
          </p>
        )
      }
    >
      {/* Fila 1: conectar el Sheet. Enter en sus campos guarda el inventario. */}
      <form
        noValidate
        onSubmit={(evento) => {
          evento.preventDefault();
          void guardar();
        }}
      >
        <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_180px] items-start gap-4 max-[1100px]:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_180px]">
          <Campo
            id="configuracion-sheet"
            etiqueta={textos['configuracion.sheet.etiqueta.link']}
            placeholder={textos['configuracion.sheet.ejemplo.link']}
            ayuda={textos['configuracion.sheet.ayuda.link']}
            value={linkOId}
            onChange={(evento) => {
              setLinkOId(evento.target.value);
              bloque.marcarEditado();
            }}
            onBlur={alSalirDelLink}
            ref={bloque.registrarCampo('sheet')}
            error={bloque.errores.sheet}
            data-testid="configuracion-campo-sheet"
            testIdError="configuracion-mensaje-error-link-sheet"
          />
          <Campo
            id="configuracion-pestana-inventario"
            etiqueta={textos['configuracion.sheet.etiqueta.pestana']}
            placeholder={textos['configuracion.sheet.ejemplo.pestana']}
            ayuda={textos['configuracion.sheet.ayuda.pestana']}
            value={pestana}
            onChange={(evento) => {
              setPestana(evento.target.value);
              bloque.marcarEditado();
            }}
            ref={bloque.registrarCampo('pestanaInventario')}
            error={bloque.errores.pestanaInventario}
            data-testid="configuracion-campo-pestana-inventario"
            testIdError="configuracion-mensaje-error-pestana-inventario"
          />
          <ListaDesplegable
            id="configuracion-traer-cartas-de"
            etiqueta={textos['configuracion.sheet.traer.etiqueta']}
            textoVacio={
              estadoTraer.tipo === 'cargando'
                ? textos['configuracion.sheet.renombrar.carga.lista']
                : estadoTraer.tipo === 'sin-sheet'
                  ? textos['configuracion.sheet.traer.texto.sin-sheet']
                  : textos['configuracion.sheet.traer.opcion.ninguna']
            }
            opciones={opcionesTraer}
            cargando={estadoTraer.tipo === 'cargando'}
            disabled={estadoTraer.tipo !== 'lista'}
            value={origenElegido}
            onChange={(evento) => {
              setOrigen(evento.target.value);
              bloque.marcarEditado();
            }}
            ayuda={
              estadoTraer.tipo === 'sin-sheet'
                ? textos['configuracion.sheet.traer.ayuda.sin-sheet']
                : ningunaReconocida
                  ? textos['configuracion.sheet.traer.ayuda.ninguna-reconocida']
                  : textos['configuracion.sheet.traer.ayuda']
            }
            testIdAyuda="configuracion-texto-ayuda-traer-cartas-de"
            ref={bloque.registrarCampo('traerCartasDe')}
            error={
              bloque.errores.traerCartasDe ??
              (estadoTraer.tipo === 'error'
                ? textos['configuracion.sheet.traer.error.lista']
                : undefined)
            }
            errorSinMarcar={!bloque.errores.traerCartasDe}
            accionError={
              !bloque.errores.traerCartasDe &&
              estadoTraer.tipo === 'error' && (
                <BotonReintentarLista
                  alReintentar={recargarListaTraer}
                  data-testid="configuracion-boton-reintentar-pestanas-origen"
                />
              )
            }
            data-testid="configuracion-selector-traer-cartas-de"
            testIdError="configuracion-mensaje-error-traer-cartas-de"
          />
          <div>
            <span className="mb-1 block text-[14px] font-semibold" aria-hidden="true">
              &nbsp;
            </span>
            {/* Regla de espera: lo que tarda usa la capa de carga, no la ruedita del botón. */}
            <Boton
              type="submit"
              Icono={Save}
              disabled={bloque.guardando}
              className="w-full"
              data-testid="configuracion-boton-guardar-sheet"
            >
              {textos['configuracion.sheet.boton.guardar']}
            </Boton>
          </div>
        </div>
        <div className="mt-2 flex min-h-[40px]">
          <MensajeBloque pantalla="configuracion" bloque="sheet" mensaje={bloque.mensaje} />
        </div>
      </form>

      <hr className="my-4 border-borde" />

      {/* Fila 2: renombrar una pestaña del Sheet guardado. Enter en el nombre nuevo renombra. */}
      <form
        noValidate
        onSubmit={(evento) => {
          evento.preventDefault();
          void renombrar();
        }}
      >
        <h3 className="mb-3 text-[14px] font-semibold">
          {textos['configuracion.sheet.renombrar.titulo']}
        </h3>
        <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_180px] items-start gap-4">
          <ListaDesplegable
            id="configuracion-pestana-a-renombrar"
            etiqueta={textos['configuracion.sheet.renombrar.etiqueta.pestana']}
            textoVacio={
              listaRenombrar.tipo === 'cargando'
                ? textos['configuracion.sheet.renombrar.carga.lista']
                : listaRenombrar.tipo === 'sin-sheet'
                  ? textos['configuracion.sheet.renombrar.texto.sin-sheet']
                  : listaRenombrar.tipo === 'lista' && !renombrarLista
                    ? textos['configuracion.sheet.renombrar.texto.sin-pestanas']
                    : textos['configuracion.sheet.renombrar.ejemplo.pestana']
            }
            opciones={opcionesRenombrar}
            cargando={listaRenombrar.tipo === 'cargando'}
            disabled={!renombrarLista}
            value={pestanaElegida}
            onChange={(evento) => {
              setPestanaElegida(evento.target.value);
              filaRenombrar.marcarEditado();
            }}
            ayuda={
              listaRenombrar.tipo === 'sin-sheet'
                ? textos['configuracion.sheet.renombrar.ayuda.sin-sheet']
                : textos['configuracion.sheet.renombrar.ayuda.pestana']
            }
            ref={filaRenombrar.registrarCampo('nombreActual')}
            error={
              filaRenombrar.errores.nombreActual ??
              (listaRenombrar.tipo === 'error'
                ? textos['configuracion.sheet.renombrar.error.lista']
                : undefined)
            }
            errorSinMarcar={!filaRenombrar.errores.nombreActual}
            accionError={
              !filaRenombrar.errores.nombreActual &&
              listaRenombrar.tipo === 'error' && (
                <BotonReintentarLista
                  alReintentar={() => void cargarListas({ renombrar: true })}
                  data-testid="configuracion-boton-reintentar-pestanas"
                />
              )
            }
            data-testid="configuracion-selector-pestana-a-renombrar"
            testIdError="configuracion-mensaje-error-pestana-a-renombrar"
          />
          <Campo
            id="configuracion-nombre-nuevo"
            etiqueta={textos['configuracion.sheet.renombrar.etiqueta.nombre-nuevo']}
            placeholder={textos['configuracion.sheet.renombrar.ejemplo.nombre-nuevo']}
            ayuda={textos['configuracion.sheet.renombrar.ayuda.nombre-nuevo']}
            value={nombreNuevo}
            disabled={!renombrarLista}
            onChange={(evento) => {
              // El nombre nuevo no cuenta como "cambio sin guardar": solo se va el éxito anterior.
              setNombreNuevo(evento.target.value);
              filaRenombrar.marcarEditado();
            }}
            ref={filaRenombrar.registrarCampo('nombreNuevo')}
            error={filaRenombrar.errores.nombreNuevo}
            data-testid="configuracion-campo-nombre-nuevo"
            testIdError="configuracion-mensaje-error-nombre-nuevo"
          />
          <div>
            <span className="mb-1 block text-[14px] font-semibold" aria-hidden="true">
              &nbsp;
            </span>
            <Boton
              type="submit"
              variante="secundario"
              Icono={Pencil}
              disabled={!renombrarLista || filaRenombrar.guardando}
              className="w-full"
              data-testid="configuracion-boton-renombrar-pestana"
            >
              {textos['configuracion.sheet.renombrar.boton']}
            </Boton>
          </div>
        </div>
        <div className="mt-2 flex min-h-[40px]">
          <MensajeBloque
            pantalla="configuracion"
            bloque="renombrar"
            mensaje={filaRenombrar.mensaje}
          />
        </div>
      </form>

      {(bloque.guardando || filaRenombrar.guardando) && <CapaCarga texto={textoCapa} />}
    </Tarjeta>
  );
}

/** Enlace-botón "Reintentar" dentro del error de una lista de pestañas. */
function BotonReintentarLista({
  alReintentar,
  'data-testid': testId,
}: {
  alReintentar: () => void;
  'data-testid': string;
}) {
  return (
    <button
      type="button"
      onClick={alReintentar}
      className="ml-1 shrink-0 cursor-pointer border-0 bg-transparent p-0 text-[11px] font-semibold text-error underline"
      data-testid={testId}
    >
      {textos['general.boton.reintentar']}
    </button>
  );
}
