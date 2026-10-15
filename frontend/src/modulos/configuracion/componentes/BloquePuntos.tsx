import { useEffect, useState } from 'react';
import { Lightbulb, Save, Star } from 'lucide-react';
import { Boton } from '@/compartido/componentes/Boton';
import { Campo } from '@/compartido/componentes/Campo';
import { MensajeBloque } from '@/compartido/componentes/MensajeBloque';
import { Tarjeta } from '@/compartido/componentes/Tarjeta';
import { useGuardarBloque } from '@/compartido/hooks/useGuardarBloque';
import { texto, textos } from '@/compartido/textos/textos';
import { guardarPuntos, type BloquePuntos as DatosPuntos } from '../configuracionApi';
import type { AlCambiarSinGuardar } from '../ConfiguracionPagina';

/** Compra de referencia del ejemplo en vivo ($25.000). */
const COMPRA_DE_EJEMPLO = 25000;

/** Lee el número escrito; acepta coma o punto decimal. */
function aNumero(escrito: string): number {
  return Number(escrito.trim().replace(',', '.'));
}

/**
 * Texto del ejemplo en vivo: cuántos puntos suma una compra de $25.000 con los pesos escritos.
 * Ejemplo: `ejemploPuntos('1000')` → "Una compra de $25.000 suma 25 puntos."
 */
function ejemploPuntos(pesosEscritos: string): string {
  const pesos = aNumero(pesosEscritos);
  if (!pesosEscritos.trim() || !(pesos > 0)) {
    return textos['configuracion.puntos.texto.ejemplo-vacio'];
  }
  return texto('configuracion.puntos.texto.ejemplo', {
    puntos: Math.floor(COMPRA_DE_EJEMPLO / pesos).toLocaleString('es-AR'),
  });
}

export interface BloquePuntosProps {
  puntos: DatosPuntos;
  alCambiarSinGuardar?: AlCambiarSinGuardar;
}

/**
 * Bloque "Puntos de clientes": pesos por punto, con un ejemplo en vivo que se recalcula al escribir (CFG-F05, A06).
 * Ejemplo: `<BloquePuntos puntos={configuracion.puntos} alCambiarSinGuardar={marcarSinGuardar} />`
 */
export function BloquePuntos({ puntos, alCambiarSinGuardar }: BloquePuntosProps) {
  const bloque = useGuardarBloque();
  const [pesos, setPesos] = useState(puntos.pesosPorPunto == null ? '' : String(puntos.pesosPorPunto));
  const { sinGuardar } = bloque;

  useEffect(() => {
    alCambiarSinGuardar?.('puntos', sinGuardar);
  }, [alCambiarSinGuardar, sinGuardar]);

  async function guardar() {
    const numero = aNumero(pesos);
    if (!pesos.trim()) {
      bloque.marcarErrores({ pesosPorPunto: textos['configuracion.puntos.error.vacio'] });
      return;
    }
    if (!(numero > 0)) {
      bloque.marcarErrores({ pesosPorPunto: textos['general.error.mayor-a-cero'] });
      return;
    }
    const guardados = await bloque.guardar(() => guardarPuntos(numero), { exito: textos['configuracion.puntos.exito'] });
    if (guardados?.pesosPorPunto != null) {
      setPesos(String(guardados.pesosPorPunto));
    }
  }

  return (
    <Tarjeta
      Icono={Star}
      titulo={textos['configuracion.puntos.titulo']}
      bajada={textos['configuracion.puntos.bajada']}
      alEnviar={guardar}
      pie={
        <>
          <Boton type="submit" Icono={Save} guardando={bloque.guardando} data-testid="configuracion-boton-guardar-puntos">
            {textos['configuracion.puntos.boton.guardar']}
          </Boton>
          <MensajeBloque pantalla="configuracion" bloque="puntos" mensaje={bloque.mensaje} />
        </>
      }
    >
      <div className="grid grid-cols-2 items-start gap-4">
        <Campo
          id="configuracion-pesos-por-punto"
          etiqueta={textos['configuracion.puntos.etiqueta.pesos']}
          placeholder={textos['configuracion.puntos.ejemplo.pesos']}
          inputMode="decimal"
          value={pesos}
          onChange={(evento) => {
            setPesos(evento.target.value);
            bloque.marcarEditado();
          }}
          ref={bloque.registrarCampo('pesosPorPunto')}
          error={bloque.errores.pesosPorPunto}
          data-testid="configuracion-campo-pesos-por-punto"
          testIdError="configuracion-mensaje-error-pesos-por-punto"
        />
        {/* Ejemplo en vivo: ayuda a entender la regla. */}
        <div className="mt-[22px] self-start rounded-xl bg-acento-suave px-4 py-3 text-[13px] text-acento-hover">
          <Lightbulb className="h-4 w-4" aria-hidden="true" />
          <p className="mt-1" data-testid="configuracion-texto-ejemplo-puntos">
            {ejemploPuntos(pesos)}
          </p>
        </div>
      </div>
    </Tarjeta>
  );
}
