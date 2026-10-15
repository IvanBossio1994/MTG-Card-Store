import { useEffect, useState } from 'react';
import { BadgeDollarSign, Save } from 'lucide-react';
import { Boton } from '@/compartido/componentes/Boton';
import { Campo } from '@/compartido/componentes/Campo';
import { MensajeBloque } from '@/compartido/componentes/MensajeBloque';
import { Tarjeta } from '@/compartido/componentes/Tarjeta';
import { useGuardarBloque } from '@/compartido/hooks/useGuardarBloque';
import { textos, type ClaveTexto } from '@/compartido/textos/textos';
import { guardarPrecios, type BloquePrecios as DatosPrecios } from '../configuracionApi';
import type { AlCambiarSinGuardar } from '../ConfiguracionPagina';

type Rareza = 'common' | 'uncommon' | 'rare' | 'mythic' | 'tierraBasica';

interface DatosRareza {
  rareza: Rareza;
  /** Parte del locator y del id del campo (`tierra-basica`). */
  nombre: string;
  etiqueta: ClaveTexto;
  /** Color del puntito de rareza (tokens `rareza-*`). */
  color: string;
}

const RAREZAS: DatosRareza[] = [
  { rareza: 'common', nombre: 'common', etiqueta: 'configuracion.precios.etiqueta.common', color: 'bg-rareza-common' },
  { rareza: 'uncommon', nombre: 'uncommon', etiqueta: 'configuracion.precios.etiqueta.uncommon', color: 'bg-rareza-uncommon' },
  { rareza: 'rare', nombre: 'rare', etiqueta: 'configuracion.precios.etiqueta.rare', color: 'bg-rareza-rare' },
  { rareza: 'mythic', nombre: 'mythic', etiqueta: 'configuracion.precios.etiqueta.mythic', color: 'bg-rareza-mythic' },
  {
    rareza: 'tierraBasica',
    nombre: 'tierra-basica',
    etiqueta: 'configuracion.precios.etiqueta.tierra-basica',
    color: 'bg-rareza-tierra',
  },
];

type BulkEscrito = Record<Rareza, string>;

/** Pasa un número guardado al texto del campo (vacío si no hay). */
function aTexto(valor?: number | null): string {
  return valor == null ? '' : String(valor);
}

/** Lee el número escrito; acepta coma o punto decimal. */
function aNumero(escrito: string): number {
  return Number(escrito.trim().replace(',', '.'));
}

function bulkEscrito(precios: DatosPrecios): BulkEscrito {
  return {
    common: aTexto(precios.bulk?.common),
    uncommon: aTexto(precios.bulk?.uncommon),
    rare: aTexto(precios.bulk?.rare),
    mythic: aTexto(precios.bulk?.mythic),
    tierraBasica: aTexto(precios.bulk?.tierraBasica),
  };
}

export interface BloquePreciosProps {
  precios: DatosPrecios;
  alCambiarSinGuardar?: AlCambiarSinGuardar;
}

/**
 * Bloque "Regla de precio": cotización, redondeo, carpeta de cache y los 5 precios bulk (enteros, sin centavos).
 * Valida en pantalla antes de llamar a la API y muestra el aviso si falla Card Kingdom (CFG-F04, A06, A08, A09).
 * Ejemplo: `<BloquePrecios precios={configuracion.precios} alCambiarSinGuardar={marcarSinGuardar} />`
 */
export function BloquePrecios({ precios, alCambiarSinGuardar }: BloquePreciosProps) {
  const bloque = useGuardarBloque();
  const [cotizacion, setCotizacion] = useState(aTexto(precios.cotizacion));
  const [redondeo, setRedondeo] = useState(aTexto(precios.redondeo));
  const [carpetaCache, setCarpetaCache] = useState(precios.carpetaCache ?? '');
  const [bulk, setBulk] = useState<BulkEscrito>(() => bulkEscrito(precios));
  const { sinGuardar } = bloque;

  useEffect(() => {
    alCambiarSinGuardar?.('precios', sinGuardar);
  }, [alCambiarSinGuardar, sinGuardar]);

  /** Errores de la validación en pantalla, con las mismas claves que usa la API. */
  function validar(): Record<string, string> {
    const errores: Record<string, string> = {};
    const revisar = (clave: string, escrito: string, vacio: string, menor: string, entero = false) => {
      const numero = aNumero(escrito);
      if (!escrito.trim()) {
        errores[clave] = vacio;
      } else if (!(numero > 0)) {
        errores[clave] = menor;
      } else if (entero && !Number.isInteger(numero)) {
        errores[clave] = textos['general.error.sin-centavos'];
      }
    };
    revisar(
      'cotizacion',
      cotizacion,
      textos['configuracion.precios.error.cotizacion-vacia'],
      textos['configuracion.precios.error.cotizacion-menor'],
    );
    revisar(
      'redondeo',
      redondeo,
      textos['configuracion.precios.error.redondeo-vacio'],
      textos['configuracion.precios.error.redondeo-menor'],
    );
    for (const { rareza } of RAREZAS) {
      revisar(
        `bulk.${rareza}`,
        bulk[rareza],
        textos['configuracion.precios.error.bulk-vacio'],
        textos['general.error.mayor-a-cero'],
        true,
      );
    }
    return errores;
  }

  async function guardar() {
    const errores = validar();
    if (Object.keys(errores).length > 0) {
      bloque.marcarErrores(errores, true);
      return;
    }
    const carpeta = carpetaCache.trim();
    const guardados = await bloque.guardar(
      () =>
        guardarPrecios({
          cotizacion: aNumero(cotizacion),
          redondeo: aNumero(redondeo),
          bulk: {
            common: aNumero(bulk.common),
            uncommon: aNumero(bulk.uncommon),
            rare: aNumero(bulk.rare),
            mythic: aNumero(bulk.mythic),
            tierraBasica: aNumero(bulk.tierraBasica),
          },
          carpetaCache: carpeta || undefined,
        }),
      {
        exito: textos['configuracion.precios.exito'],
        aviso: (respuesta) => respuesta.aviso,
        avisarErroresDeCampo: true,
      },
    );
    if (guardados) {
      setCotizacion(aTexto(guardados.cotizacion) || cotizacion);
      setRedondeo(aTexto(guardados.redondeo) || redondeo);
      setCarpetaCache(guardados.carpetaCache ?? '');
      if (guardados.bulk) {
        setBulk(bulkEscrito(guardados));
      }
    }
  }

  function editar(cambiar: () => void) {
    cambiar();
    bloque.marcarEditado();
  }

  return (
    <Tarjeta
      Icono={BadgeDollarSign}
      titulo={textos['configuracion.precios.titulo']}
      bajada={textos['configuracion.precios.bajada']}
      className="col-span-2 max-[1100px]:col-span-1"
      alEnviar={guardar}
      pie={
        <>
          <Boton type="submit" Icono={Save} guardando={bloque.guardando} data-testid="configuracion-boton-guardar-precios">
            {textos['configuracion.precios.boton.guardar']}
          </Boton>
          <MensajeBloque pantalla="configuracion" bloque="precios" mensaje={bloque.mensaje} />
        </>
      }
    >
      <div className="grid grid-cols-3 items-start gap-4">
        <Campo
          id="configuracion-cotizacion"
          etiqueta={textos['configuracion.precios.etiqueta.cotizacion']}
          placeholder={textos['configuracion.precios.ejemplo.cotizacion']}
          inputMode="decimal"
          value={cotizacion}
          onChange={(evento) => editar(() => setCotizacion(evento.target.value))}
          ref={bloque.registrarCampo('cotizacion')}
          error={bloque.errores.cotizacion}
          data-testid="configuracion-campo-cotizacion"
          testIdError="configuracion-mensaje-error-cotizacion"
        />
        <Campo
          id="configuracion-redondeo"
          etiqueta={textos['configuracion.precios.etiqueta.redondeo']}
          placeholder={textos['configuracion.precios.ejemplo.redondeo']}
          inputMode="decimal"
          value={redondeo}
          onChange={(evento) => editar(() => setRedondeo(evento.target.value))}
          ref={bloque.registrarCampo('redondeo')}
          error={bloque.errores.redondeo}
          data-testid="configuracion-campo-redondeo"
          testIdError="configuracion-mensaje-error-redondeo"
        />
        <Campo
          id="configuracion-carpeta-cache"
          etiqueta={textos['configuracion.precios.etiqueta.cache']}
          placeholder={textos['configuracion.precios.ejemplo.cache']}
          value={carpetaCache}
          onChange={(evento) => editar(() => setCarpetaCache(evento.target.value))}
          ref={bloque.registrarCampo('carpetaCache')}
          error={bloque.errores.carpetaCache}
          data-testid="configuracion-campo-carpeta-cache"
          testIdError="configuracion-mensaje-error-carpeta-cache"
        />
      </div>
      <h3 className="mt-6 mb-2 text-[14px] font-semibold">{textos['configuracion.precios.titulo.bulk']}</h3>
      <div className="grid grid-cols-5 items-start gap-4 max-[1100px]:grid-cols-3">
        {RAREZAS.map(({ rareza, nombre, etiqueta, color }) => (
          <Campo
            key={rareza}
            id={`configuracion-bulk-${nombre}`}
            etiqueta={
              <>
                <span
                  className={`h-2.5 w-2.5 flex-none rounded-full shadow-[inset_0_0_0_1px_rgba(0,0,0,0.18)] ${color}`}
                  aria-hidden="true"
                />
                {textos[etiqueta]}
              </>
            }
            inputMode="numeric"
            value={bulk[rareza]}
            onChange={(evento) => {
              const valor = evento.target.value;
              editar(() => setBulk((actual) => ({ ...actual, [rareza]: valor })));
            }}
            ref={bloque.registrarCampo(`bulk.${rareza}`)}
            error={bloque.errores[`bulk.${rareza}`]}
            data-testid={`configuracion-campo-bulk-${nombre}`}
            testIdError={`configuracion-mensaje-error-bulk-${nombre}`}
            lineasError={2}
            placeholder={textos['configuracion.precios.ejemplo.bulk']}
          />
        ))}
      </div>
    </Tarjeta>
  );
}
