import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { TriangleAlert } from 'lucide-react';
import { Boton } from '@/compartido/componentes/Boton';
import { textos } from '@/compartido/textos/textos';

export interface AvisoSalirSinGuardarProps {
  /** Dice si hay algún bloque con cambios sin guardar en este momento. */
  hayCambiosSinGuardar: () => boolean;
  /** Prefijo de los `data-testid` de los botones (por ejemplo `configuracion`). */
  pantalla: string;
}

/**
 * Avisa al salir con cambios sin guardar: al tocar un enlace muestra el modal "Tenés cambios sin guardar"
 * con "Seguir editando" (foco inicial y Esc) y "Salir igual"; al cerrar la pestaña usa el aviso del navegador.
 * Ejemplo: `<AvisoSalirSinGuardar pantalla="configuracion" hayCambiosSinGuardar={() => sinGuardar.size > 0} />`
 */
export function AvisoSalirSinGuardar({ hayCambiosSinGuardar, pantalla }: AvisoSalirSinGuardarProps) {
  const [enlacePendiente, setEnlacePendiente] = useState<HTMLAnchorElement | null>(null);
  const seguirEditando = useRef<HTMLButtonElement>(null);
  const salirIgualBoton = useRef<HTMLButtonElement>(null);
  const saliendo = useRef(false);
  const hayCambios = useRef(hayCambiosSinGuardar);
  hayCambios.current = hayCambiosSinGuardar;

  useEffect(() => {
    function alHacerClic(evento: MouseEvent) {
      if (saliendo.current || evento.defaultPrevented || evento.button !== 0) {
        return;
      }
      if (evento.ctrlKey || evento.metaKey || evento.shiftKey || evento.altKey) {
        return;
      }
      const enlace = (evento.target as Element | null)?.closest?.('a[href]');
      if (!(enlace instanceof HTMLAnchorElement) || enlace.target === '_blank') {
        return;
      }
      if (enlace.getAttribute('href')?.startsWith('#') || !hayCambios.current()) {
        return;
      }
      evento.preventDefault();
      setEnlacePendiente(enlace);
    }

    function alCerrarPestana(evento: BeforeUnloadEvent) {
      if (!saliendo.current && hayCambios.current()) {
        evento.preventDefault();
        evento.returnValue = '';
      }
    }

    // En captura, para frenar la navegación antes que nadie.
    document.addEventListener('click', alHacerClic, true);
    window.addEventListener('beforeunload', alCerrarPestana);
    return () => {
      document.removeEventListener('click', alHacerClic, true);
      window.removeEventListener('beforeunload', alCerrarPestana);
    };
  }, []);

  useEffect(() => {
    if (!enlacePendiente) {
      return;
    }
    seguirEditando.current?.focus();
    // Esc es "Seguir editando", esté donde esté el foco.
    function alApretarEsc(evento: globalThis.KeyboardEvent) {
      if (evento.key === 'Escape') {
        evento.preventDefault();
        setEnlacePendiente(null);
        enlacePendiente?.focus();
      }
    }
    document.addEventListener('keydown', alApretarEsc);
    return () => document.removeEventListener('keydown', alApretarEsc);
  }, [enlacePendiente]);

  function quedarse() {
    const enlace = enlacePendiente;
    setEnlacePendiente(null);
    enlace?.focus();
  }

  function salirIgual() {
    const enlace = enlacePendiente;
    setEnlacePendiente(null);
    // Queda en true: la página se va y el aviso del navegador (beforeunload) no tiene que volver a preguntar.
    saliendo.current = true;
    enlace?.click();
  }

  function alApretarTecla(evento: KeyboardEvent<HTMLDivElement>) {
    // El foco no sale del modal: Tab alterna entre los dos botones.
    if (evento.key === 'Tab') {
      evento.preventDefault();
      const siguiente = document.activeElement === seguirEditando.current ? salirIgualBoton : seguirEditando;
      siguiente.current?.focus();
    }
  }

  if (!enlacePendiente) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-stone-900/40 p-6"
      role="dialog"
      aria-modal="true"
      aria-labelledby="aviso-salir-titulo"
      onKeyDown={alApretarTecla}
    >
      <div className="w-full max-w-[420px] animate-entra rounded-2xl border border-borde bg-white p-6 text-center shadow-[0_20px_50px_rgba(41,37,36,0.2)]">
        <TriangleAlert className="mx-auto h-9 w-9 text-aviso" aria-hidden="true" />
        <h2 id="aviso-salir-titulo" className="mt-2 text-[18px] font-semibold tracking-[-0.01em]">
          {textos['general.modal.sin-guardar-titulo']}
        </h2>
        <p className="mt-1 mb-4 text-[14px] text-suave">{textos['general.modal.sin-guardar-pregunta']}</p>
        <div className="flex items-center justify-center gap-4">
          <Boton ref={seguirEditando} onClick={quedarse} data-testid={`${pantalla}-boton-seguir-editando`}>
            {textos['general.boton.seguir-editando']}
          </Boton>
          <Boton
            ref={salirIgualBoton}
            variante="secundario"
            onClick={salirIgual}
            data-testid={`${pantalla}-boton-salir-igual`}
          >
            {textos['general.boton.salir-igual']}
          </Boton>
        </div>
      </div>
    </div>
  );
}
