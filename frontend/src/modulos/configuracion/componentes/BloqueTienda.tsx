import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { Save, Store, Trash2, Upload } from 'lucide-react';
import { LOGO_POR_DEFECTO } from '@/app/MarcoApp';
import { Boton } from '@/compartido/componentes/Boton';
import { Campo, ErrorCampo, EtiquetaCampo } from '@/compartido/componentes/Campo';
import { MensajeBloque } from '@/compartido/componentes/MensajeBloque';
import { Tarjeta } from '@/compartido/componentes/Tarjeta';
import { useGuardarBloque } from '@/compartido/hooks/useGuardarBloque';
import { textos } from '@/compartido/textos/textos';
import {
  FORMATOS_LOGO,
  PESO_MAXIMO_LOGO,
  guardarTienda,
  subirLogo,
  type BloqueTienda as DatosTienda,
} from '../configuracionApi';
import type { AlCambiarSinGuardar } from '../ConfiguracionPagina';

export interface BloqueTiendaProps {
  tienda: DatosTienda;
  /** Se llama con la tienda guardada (para actualizar el nombre y el logo del menú). */
  alGuardar?: (tienda: DatosTienda) => void;
  alCambiarSinGuardar?: AlCambiarSinGuardar;
}

/**
 * Bloque "Tienda":nombre y logo. El logo elegido (o "Quitar") solo cambia la vista previa;
 * se aplica al apretar "Guardar tienda", que primero sube el logo y después guarda el nombre (CFG-F01, A01, A02).
 * Ejemplo: `<BloqueTienda tienda={configuracion.tienda} alGuardar={actualizarMenu} />`
 */
export function BloqueTienda({ tienda, alGuardar, alCambiarSinGuardar }: BloqueTiendaProps) {
  const bloque = useGuardarBloque();
  const [nombre, setNombre] = useState(tienda.nombre ?? '');
  const [logoGuardado, setLogoGuardado] = useState(tienda.logoUrl ?? null);
  const [logoNuevo, setLogoNuevo] = useState<File | null>(null);
  const [vistaPrevia, setVistaPrevia] = useState<string | null>(null);
  const [quitarLogo, setQuitarLogo] = useState(false);
  const selectorArchivo = useRef<HTMLInputElement>(null);
  const archivoEnVista = useRef<File | null>(null);
  const { sinGuardar } = bloque;

  useEffect(() => {
    alCambiarSinGuardar?.('tienda', sinGuardar);
  }, [alCambiarSinGuardar, sinGuardar]);

  const logoVisible =
    vistaPrevia ?? (quitarLogo || !logoGuardado ? LOGO_POR_DEFECTO : logoGuardado);

  function elegirLogo(evento: ChangeEvent<HTMLInputElement>) {
    const archivo = evento.target.files?.[0];
    evento.target.value = '';
    if (!archivo) {
      return;
    }
    if (!FORMATOS_LOGO.includes(archivo.type)) {
      bloque.marcarErrores({ archivo: textos['configuracion.tienda.error.logo-formato'] });
      return;
    }
    if (archivo.size > PESO_MAXIMO_LOGO) {
      bloque.marcarErrores({ archivo: textos['configuracion.tienda.error.logo-pesado'] });
      return;
    }
    bloque.limpiar();
    bloque.marcarEditado();
    setLogoNuevo(archivo);
    setQuitarLogo(false);
    archivoEnVista.current = archivo;
    const lector = new FileReader();
    lector.onload = () => {
      // Si mientras leía se guardó, se quitó o se eligió otro, esta vista previa ya no corresponde.
      if (archivoEnVista.current === archivo && typeof lector.result === 'string') {
        setVistaPrevia(lector.result);
      }
    };
    lector.readAsDataURL(archivo);
  }

  function quitar() {
    bloque.marcarEditado();
    archivoEnVista.current = null;
    setLogoNuevo(null);
    setVistaPrevia(null);
    setQuitarLogo(true);
  }

  async function guardar() {
    const nombreLimpio = nombre.trim();
    if (!nombreLimpio) {
      bloque.marcarErrores({ nombre: textos['configuracion.tienda.error.nombre-vacio'] });
      return;
    }
    const guardada = await bloque.guardar(
      async () => {
        if (logoNuevo) {
          await subirLogo(logoNuevo);
        }
        return guardarTienda(nombreLimpio, quitarLogo && !logoNuevo);
      },
      { exito: textos['configuracion.tienda.exito'] },
    );
    if (guardada) {
      setNombre(guardada.nombre ?? nombreLimpio);
      setLogoGuardado(guardada.logoUrl ?? null);
      archivoEnVista.current = null;
      setLogoNuevo(null);
      setVistaPrevia(null);
      setQuitarLogo(false);
      alGuardar?.(guardada);
    }
  }

  return (
    <Tarjeta
      Icono={Store}
      titulo={textos['configuracion.tienda.titulo']}
      bajada={textos['configuracion.tienda.bajada']}
      alEnviar={() => void guardar()}
      pie={
        <>
          <Boton
            type="submit"
            Icono={Save}
            guardando={bloque.guardando}
            data-testid="configuracion-boton-guardar-tienda"
          >
            {textos['configuracion.tienda.boton.guardar']}
          </Boton>
          <MensajeBloque pantalla="configuracion" bloque="tienda" mensaje={bloque.mensaje} />
        </>
      }
    >
      <div className="grid grid-cols-2 items-start gap-4">
        <Campo
          id="configuracion-nombre-tienda"
          etiqueta={textos['configuracion.tienda.etiqueta.nombre']}
          placeholder={textos['configuracion.tienda.ejemplo.nombre']}
          value={nombre}
          onChange={(evento) => {
            setNombre(evento.target.value);
            bloque.marcarEditado();
          }}
          ref={bloque.registrarCampo('nombre')}
          autoFocus
          error={bloque.errores.nombre}
          data-testid="configuracion-campo-nombre-tienda"
          testIdError="configuracion-mensaje-error-nombre-tienda"
        />
        <div>
          <EtiquetaCampo>{textos['configuracion.tienda.etiqueta.logo']}</EtiquetaCampo>
          <div className="flex items-center gap-4">
            <img
              className="h-[72px] w-[72px] rounded-xl border border-borde bg-white object-contain p-1"
              src={logoVisible}
              alt=""
            />
            <div className="flex flex-col items-start gap-1">
              <div className="flex items-center gap-2">
                <Boton
                  variante="secundario"
                  Icono={Upload}
                  ref={bloque.registrarCampo('archivo')}
                  onClick={() => selectorArchivo.current?.click()}
                  data-testid="configuracion-boton-subir-logo"
                >
                  {textos['configuracion.tienda.boton.subir-logo']}
                </Boton>
                <Boton
                  variante="texto"
                  Icono={Trash2}
                  onClick={quitar}
                  data-testid="configuracion-boton-quitar-logo"
                >
                  {textos['configuracion.tienda.boton.quitar-logo']}
                </Boton>
              </div>
              <input
                ref={selectorArchivo}
                type="file"
                accept={FORMATOS_LOGO.join(',')}
                hidden
                onChange={elegirLogo}
              />
              <p className="text-[12px] text-suave">{textos['configuracion.tienda.ayuda.logo']}</p>
            </div>
          </div>
          <ErrorCampo testId="configuracion-mensaje-error-logo" mensaje={bloque.errores.archivo} />
        </div>
      </div>
    </Tarjeta>
  );
}
