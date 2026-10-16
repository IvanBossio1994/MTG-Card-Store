import { useCallback, useEffect, useRef, useState } from 'react';
import { RefreshCw, WifiOff } from 'lucide-react';
import { ErrorApi } from '@/compartido/api/clienteApi';
import { AvisoSalirSinGuardar } from '@/compartido/componentes/AvisoSalirSinGuardar';
import { Boton } from '@/compartido/componentes/Boton';
import { CapaCarga } from '@/compartido/componentes/CapaCarga';
import { textos } from '@/compartido/textos/textos';
import { BloquePrecios } from './componentes/BloquePrecios';
import { BloquePuntos } from './componentes/BloquePuntos';
import { BloqueSheet } from './componentes/BloqueSheet';
import { BloqueTienda } from './componentes/BloqueTienda';
import {
  leerConfiguracion,
  type BloqueTienda as DatosTienda,
  type Configuracion,
} from './configuracionApi';

/** Bloques de la pantalla, en orden: cada uno se guarda por separado. */
export type BloqueConfiguracion = 'tienda' | 'sheet' | 'precios' | 'puntos';

/** Aviso que da cada bloque cuando queda (o deja de quedar) con cambios sin guardar. */
export type AlCambiarSinGuardar = (bloque: BloqueConfiguracion, sinGuardar: boolean) => void;

type EstadoCarga =
  | { tipo: 'cargando' }
  | { tipo: 'falla'; mensaje: string; urlInicioSesion?: string }
  | { tipo: 'lista'; configuracion: Configuracion };

export interface ConfiguracionPaginaProps {
  /** Se llama con la tienda al cargar y al guardarla, para mostrar su nombre y logo en el menú. */
  alCambiarTienda?: (tienda: DatosTienda) => void;
}

/**
 * Pantalla Configuración (docs/v2/ux/configuracion/spec.md): carga la configuración con la capa del gatito,
 * muestra la falla con "Reintentar" y los bloques en una grilla de 2 columnas, cada uno con su Guardar.
 * Ejemplo: `<MarcoApp nombreTienda={nombre} moduloActivo="configuracion"><ConfiguracionPagina alCambiarTienda={actualizar} /></MarcoApp>`
 */
export function ConfiguracionPagina({ alCambiarTienda }: ConfiguracionPaginaProps) {
  const [estado, setEstado] = useState<EstadoCarga>({ tipo: 'cargando' });
  // Bloques con cambios sin guardar: los usa el aviso al salir de la pantalla.
  const bloquesSinGuardar = useRef(new Set<BloqueConfiguracion>());
  const avisarTienda = useRef(alCambiarTienda);
  // eslint-disable-next-line react-hooks/refs -- HU-013: guardar el último valor en un ref durante el render; pasar a useEffectEvent
  avisarTienda.current = alCambiarTienda;

  const cargar = useCallback(async () => {
    setEstado({ tipo: 'cargando' });
    try {
      const configuracion = await leerConfiguracion();
      setEstado({ tipo: 'lista', configuracion });
      if (configuracion.tienda) {
        avisarTienda.current?.(configuracion.tienda);
      }
    } catch (error) {
      setEstado(
        error instanceof ErrorApi
          ? { tipo: 'falla', mensaje: error.message, urlInicioSesion: error.urlInicioSesion }
          : { tipo: 'falla', mensaje: textos['general.error.conexion'] },
      );
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- HU-013: la carga inicial pasa a useQuery
    void cargar();
  }, [cargar]);

  const marcarSinGuardar = useCallback<AlCambiarSinGuardar>((bloque, sinGuardar) => {
    if (sinGuardar) {
      bloquesSinGuardar.current.add(bloque);
    } else {
      bloquesSinGuardar.current.delete(bloque);
    }
  }, []);

  const hayCambiosSinGuardar = useCallback(() => bloquesSinGuardar.current.size > 0, []);

  const tiendaGuardada = useCallback((tienda: DatosTienda) => avisarTienda.current?.(tienda), []);

  return (
    <>
      <header className="mb-8">
        <h1 className="text-[28px] font-bold tracking-tight">{textos['configuracion.titulo']}</h1>
        <p className="mt-1 text-suave">{textos['configuracion.bajada']}</p>
      </header>

      {estado.tipo === 'cargando' && (
        <>
          <CapaCarga texto={textos['configuracion.carga']} />
          <div
            className="grid grid-cols-2 gap-4 max-[1100px]:grid-cols-[minmax(0,720px)]"
            aria-hidden="true"
          >
            {[1, 2, 3, 4].map((numero) => (
              <div key={numero} className="esqueleto h-[280px] rounded-2xl border border-borde" />
            ))}
          </div>
        </>
      )}

      {estado.tipo === 'falla' && (
        <div className="max-w-[720px] rounded-2xl border border-borde bg-white p-6 text-center shadow-tarjeta">
          <WifiOff className="mx-auto h-9 w-9 text-error" aria-hidden="true" />
          <h2 className="mt-2 flex justify-center text-[18px] font-semibold tracking-[-0.01em]">
            {textos['configuracion.error.cargar-titulo']}
          </h2>
          <p className="mt-1 mb-4 text-[14px] text-suave">
            {estado.mensaje}
            {estado.urlInicioSesion && (
              <>
                {' '}
                <a
                  href={estado.urlInicioSesion}
                  className="font-bold text-acento underline"
                  data-testid="configuracion-enlace-iniciar-sesion-google"
                >
                  {textos['general.boton.volver-a-iniciar-sesion']}
                </a>
              </>
            )}
          </p>
          <Boton
            Icono={RefreshCw}
            onClick={() => void cargar()}
            data-testid="configuracion-boton-reintentar"
          >
            {textos['general.boton.reintentar']}
          </Boton>
        </div>
      )}

      {estado.tipo === 'lista' && (
        // Grilla (HU-005): Tienda | Puntos arriba, de igual altura; Sheet y Precios a todo el ancho (col-span-2).
        <div className="grid grid-cols-2 items-start gap-4 max-[1100px]:grid-cols-[minmax(0,720px)]">
          <BloqueTienda
            tienda={estado.configuracion.tienda ?? {}}
            alGuardar={tiendaGuardada}
            alCambiarSinGuardar={marcarSinGuardar}
          />
          <BloquePuntos
            puntos={estado.configuracion.puntos ?? {}}
            alCambiarSinGuardar={marcarSinGuardar}
          />
          <BloqueSheet
            sheet={estado.configuracion.sheet ?? {}}
            alCambiarSinGuardar={marcarSinGuardar}
          />
          <BloquePrecios
            precios={estado.configuracion.precios ?? {}}
            alCambiarSinGuardar={marcarSinGuardar}
          />
        </div>
      )}

      <AvisoSalirSinGuardar pantalla="configuracion" hayCambiosSinGuardar={hayCambiosSinGuardar} />
    </>
  );
}
