import type { ComponentProps } from 'react';
import { Loader2, type LucideIcon } from 'lucide-react';
import { cn } from '@/compartido/estilos/combinarClases';
import { textos } from '@/compartido/textos/textos';

/** Tipos de botón del sistema de diseño: primario (42 × 160 mín.), secundario y de texto (42 × 120 mín.). */
export type VarianteBoton = 'primario' | 'secundario' | 'texto';

export interface BotonProps extends ComponentProps<'button'> {
  variante?: VarianteBoton;
  /** Ícono Lucide de 16 px antes del texto. */
  Icono?: LucideIcon;
  /**
   * Solo en botones que guardan: si se pasa, el botón reserva lugar para "Guardando…" y, con `true`,
   * se deshabilita y muestra el spinner sin cambiar de tamaño.
   */
  guardando?: boolean;
}

const CLASES_VARIANTE: Record<VarianteBoton, string> = {
  primario:
    'min-w-[160px] border border-acento bg-acento px-4 text-white hover:not-disabled:bg-acento-hover',
  secundario: 'min-w-[120px] border border-campo bg-white px-4 text-tinta hover:bg-[#f5f1e9]',
  texto: 'min-w-[120px] border-0 bg-transparent px-2 text-acento hover:bg-acento-suave',
};

const CELDA = 'col-start-1 row-start-1 inline-flex items-center justify-center gap-2';

/**
 * Botón del sistema de diseño. Con `guardando`, el texto normal y "Guardando…" ocupan la misma celda,
 * así el botón siempre mide lo del texto más largo y nunca se mueve.
 * Ejemplo: `<Boton type="submit" Icono={Save} guardando={guardando} data-testid="configuracion-boton-guardar-tienda">Guardar tienda</Boton>`
 */
export function Boton({
  variante = 'primario',
  Icono,
  guardando,
  className,
  disabled,
  children,
  type = 'button',
  ...props
}: BotonProps) {
  const contenido = (
    <>
      {Icono && <Icono className="h-4 w-4 shrink-0" aria-hidden="true" />}
      {children}
    </>
  );
  const conEspera = guardando !== undefined;

  return (
    <button
      type={type}
      disabled={disabled || guardando}
      aria-busy={guardando || undefined}
      className={cn(
        'h-[42px] flex-none cursor-pointer items-center justify-center gap-2 rounded-[10px] text-[15px] font-semibold whitespace-nowrap',
        'transition-[background-color,box-shadow,transform] duration-150 ease-[ease] active:not-disabled:scale-[.98]',
        'disabled:cursor-not-allowed disabled:opacity-60',
        conEspera ? 'inline-grid' : 'inline-flex',
        guardando && 'disabled:cursor-progress disabled:opacity-85',
        CLASES_VARIANTE[variante],
        className,
      )}
      {...props}
    >
      {conEspera ? (
        <>
          <span
            className={cn(CELDA, guardando && 'invisible')}
            aria-hidden={guardando || undefined}
          >
            {contenido}
          </span>
          <span
            className={cn(CELDA, !guardando && 'invisible')}
            aria-hidden={!guardando || undefined}
          >
            <Loader2 className="h-4 w-4 animate-girar" aria-hidden="true" />
            {textos['general.boton.guardando']}
          </span>
        </>
      ) : (
        contenido
      )}
    </button>
  );
}
