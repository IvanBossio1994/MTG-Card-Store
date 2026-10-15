import { useEffect, useState } from 'react';
import { PlugZap, Save, Table2 } from 'lucide-react';
import { Boton } from '@/compartido/componentes/Boton';
import { CapaCarga } from '@/compartido/componentes/CapaCarga';
import { Campo } from '@/compartido/componentes/Campo';
import { MensajeBloque } from '@/compartido/componentes/MensajeBloque';
import { Tarjeta } from '@/compartido/componentes/Tarjeta';
import { useGuardarBloque } from '@/compartido/hooks/useGuardarBloque';
import { texto, textos } from '@/compartido/textos/textos';
import { conectarSheet, type BloqueSheet as DatosSheet } from '../configuracionApi';
import type { AlCambiarSinGuardar } from '../ConfiguracionPagina';

export interface BloqueSheetProps {
  sheet: DatosSheet;
  alCambiarSinGuardar?: AlCambiarSinGuardar;
}

/**
 * Bloque "Inventario en Google Sheets": link o ID del Sheet, pestaña del inventario y el Gmail conectado.
 * Guardar tarda (habla con Google), por eso usa la capa de carga "Sincronizando con tu Sheet…" (CFG-F02, F03, A03 a A05, A07).
 * Ejemplo: `<BloqueSheet sheet={configuracion.sheet} />`
 */
export function BloqueSheet({ sheet, alCambiarSinGuardar }: BloqueSheetProps) {
  const bloque = useGuardarBloque();
  const [linkOId, setLinkOId] = useState(sheet.link ?? sheet.sheetId ?? '');
  const [pestana, setPestana] = useState(sheet.pestanaInventario ?? '');
  const [gmail, setGmail] = useState(sheet.gmail ?? null);
  const { sinGuardar } = bloque;

  useEffect(() => {
    alCambiarSinGuardar?.('sheet', sinGuardar);
  }, [alCambiarSinGuardar, sinGuardar]);

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
    const conectado = await bloque.guardar(() => conectarSheet(linkLimpio, pestanaLimpia), {
      exito: textos['configuracion.sheet.exito'],
      aviso: (respuesta) => respuesta.aviso,
    });
    if (conectado) {
      setLinkOId(conectado.link ?? linkLimpio);
      setPestana(conectado.pestanaInventario ?? pestanaLimpia);
      setGmail(conectado.gmail ?? gmail);
    }
  }

  return (
    <Tarjeta
      Icono={Table2}
      titulo={textos['configuracion.sheet.titulo']}
      bajada={textos['configuracion.sheet.bajada']}
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
      alEnviar={guardar}
      pie={
        <>
          {/* Regla de espera: lo que tarda usa la capa de carga, no la ruedita del botón. */}
          <Boton type="submit" Icono={Save} disabled={bloque.guardando} data-testid="configuracion-boton-guardar-sheet">
            {textos['configuracion.sheet.boton.guardar']}
          </Boton>
          <MensajeBloque pantalla="configuracion" bloque="sheet" mensaje={bloque.mensaje} />
        </>
      }
    >
      <div className="grid grid-cols-2 items-start gap-4">
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
      </div>
      {bloque.guardando && <CapaCarga texto={textos['general.carga.sincronizando-sheet']} />}
    </Tarjeta>
  );
}
