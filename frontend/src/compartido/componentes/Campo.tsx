import type { ComponentProps, ReactNode } from 'react';
import { CircleAlert } from 'lucide-react';
import { cn } from '@/compartido/estilos/combinarClases';

/**
 * Etiqueta de un campo (14 px semibold). Con `htmlFor` es un `<label>`; sin él, un texto (por ejemplo, "Logo").
 * Ejemplo: `<EtiquetaCampo htmlFor="nombre">Nombre de la tienda</EtiquetaCampo>`
 */
export function EtiquetaCampo({ htmlFor, children }: { htmlFor?: string; children: ReactNode }) {
  const clases = 'mb-1 flex items-center gap-2 text-[14px] font-semibold';
  return htmlFor ? (
    <label htmlFor={htmlFor} className={clases}>
      {children}
    </label>
  ) : (
    <span className={clases}>{children}</span>
  );
}

/**
 * Error de un campo, en su lugar reservado debajo del campo (18 px, o 36 px con `lineas={2}` en campos angostos): aparece sin mover nada.
 * Sin `mensaje` queda invisible, pero ocupa su lugar, y no lleva `data-testid` (QA solo encuentra errores visibles).
 * `accion` suma algo después del texto (por ejemplo, el enlace-botón "Reintentar").
 * Ejemplo: `<ErrorCampo id="nombre-error" testId="configuracion-mensaje-error-nombre-tienda" mensaje={errores.nombre} />`
 */
export function ErrorCampo({
  id,
  testId,
  mensaje,
  lineas = 1,
  accion,
}: {
  id?: string;
  testId: string;
  mensaje?: string;
  lineas?: 1 | 2;
  accion?: ReactNode;
}) {
  return (
    <div
      id={id}
      data-testid={mensaje ? testId : undefined}
      className={cn(
        'mt-1 flex gap-1 overflow-hidden text-[11px] leading-[18px] font-normal text-error/80',
        lineas === 1 ? 'h-[18px] items-center whitespace-nowrap' : 'h-[36px] items-start',
        !mensaje && 'invisible',
      )}
    >
      {mensaje && (
        <>
          <CircleAlert
            className={cn('h-3.5 w-3.5 shrink-0', lineas === 2 && 'mt-[2px]')}
            aria-hidden="true"
          />
          <span className={lineas === 1 ? 'truncate' : 'line-clamp-2'}>
            {mensaje}
            {accion && ' '}
          </span>
          {accion}
        </>
      )}
    </div>
  );
}

export interface CampoProps extends ComponentProps<'input'> {
  id: string;
  etiqueta: ReactNode;
  /** Ayuda siempre visible debajo del campo. */
  ayuda?: string;
  /** Mensaje de error: marca el campo en rojo y se muestra debajo. */
  error?: string;
  /** `data-testid` del error del campo (por ejemplo `configuracion-mensaje-error-link-sheet`). */
  testIdError: string;
  /** Líneas reservadas para el error: 2 en campos angostos (por ejemplo los bulk), para que el mensaje entre entero. */
  lineasError?: 1 | 2;
}

/**
 * Campo del sistema de diseño: etiqueta, input de 42 px, ayuda opcional y error en su lugar reservado.
 * Ejemplo: `<Campo id="pestana" etiqueta="Pestaña del inventario" value={pestana} onChange={…} error={errores.pestanaInventario} testIdError="configuracion-mensaje-error-pestana-inventario" data-testid="configuracion-campo-pestana-inventario" />`
 */
export function Campo({
  id,
  etiqueta,
  ayuda,
  error,
  testIdError,
  lineasError,
  className,
  ...props
}: CampoProps) {
  const idError = `${id}-error`;
  return (
    <div>
      <EtiquetaCampo htmlFor={id}>{etiqueta}</EtiquetaCampo>
      <input
        id={id}
        autoComplete="off"
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? idError : undefined}
        className={cn(
          'h-[42px] w-full rounded-[10px] border border-campo bg-white px-3 text-[15px] text-tinta',
          'transition-[border-color,box-shadow] duration-150 ease-[ease] placeholder:text-[#a8a29e] hover:border-[#b8b1a3]',
          'focus-visible:border-acento focus-visible:shadow-[0_0_0_3px_rgba(43,95,166,0.2)] focus-visible:outline-none',
          error &&
            'border-error bg-error-suave hover:border-error focus-visible:border-error focus-visible:shadow-[0_0_0_3px_rgba(179,38,30,0.18)]',
          className,
        )}
        {...props}
      />
      {ayuda && <p className="mt-1 text-[12px] text-suave">{ayuda}</p>}
      <ErrorCampo id={idError} testId={testIdError} mensaje={error} lineas={lineasError} />
    </div>
  );
}
