import type { ComponentProps, ReactNode } from 'react';
import { ChevronDown, Loader2 } from 'lucide-react';
import { cn } from '@/compartido/estilos/combinarClases';
import { ErrorCampo, EtiquetaCampo } from './Campo';

/** Una opción de la lista. */
export interface OpcionLista {
  valor: string;
  texto: string;
  /** Se ve, pero no se puede elegir (por ejemplo, una pestaña que la app usa y no se puede renombrar). */
  deshabilitada?: boolean;
}

export interface ListaDesplegableProps extends Omit<ComponentProps<'select'>, 'children'> {
  id: string;
  etiqueta: ReactNode;
  /** Primera opción, de valor vacío: "Elegir una pestaña", "No traer cartas" o el texto del estado (cargando, sin Sheet…). */
  textoVacio: string;
  opciones: OpcionLista[];
  /** Muestra la ruedita en lugar de la flecha y deshabilita la lista. */
  cargando?: boolean;
  /** Ayuda siempre visible debajo de la lista. */
  ayuda?: string;
  /** `data-testid` de la ayuda, si QA la valida. */
  testIdAyuda?: string;
  /** Mensaje de error: marca la lista en rojo y se muestra debajo. */
  error?: string;
  /** Algo después del error (por ejemplo, el enlace-botón "Reintentar"). */
  accionError?: ReactNode;
  /** Sin borde rojo aunque haya error (por ejemplo, cuando falló la carga de la lista y no es culpa de lo elegido). */
  errorSinMarcar?: boolean;
  /** `data-testid` del error de la lista. */
  testIdError: string;
}

/**
 * Lista desplegable del sistema de diseño (HU-005): misma altura y borde que un campo (42 px), flecha a la derecha,
 * texto gris sin nada elegido y fondo arena cuando está deshabilitada. Se recorre con el teclado como cualquier lista.
 * Ejemplo: `<ListaDesplegable id="pestana" etiqueta="Pestaña a renombrar" textoVacio="Elegir una pestaña" opciones={[{ valor: 'Hoja 1', texto: 'Hoja 1' }]} value={elegida} onChange={…} testIdError="configuracion-mensaje-error-pestana-a-renombrar" />`
 */
export function ListaDesplegable({
  id,
  etiqueta,
  textoVacio,
  opciones,
  cargando = false,
  ayuda,
  testIdAyuda,
  error,
  accionError,
  errorSinMarcar = false,
  testIdError,
  disabled,
  value,
  className,
  ...props
}: ListaDesplegableProps) {
  const idError = `${id}-error`;
  const marcada = Boolean(error) && !errorSinMarcar;
  return (
    <div>
      <EtiquetaCampo htmlFor={id}>{etiqueta}</EtiquetaCampo>
      <div className="relative">
        <select
          id={id}
          value={value}
          disabled={disabled || cargando}
          aria-invalid={marcada || undefined}
          aria-describedby={error ? idError : undefined}
          aria-busy={cargando || undefined}
          className={cn(
            'h-[42px] w-full cursor-pointer appearance-none truncate rounded-[10px] border border-campo bg-white pr-9 pl-3 text-[15px] text-tinta',
            'transition-[border-color,box-shadow] duration-150 ease-[ease] hover:not-disabled:border-[#b8b1a3]',
            'focus-visible:border-acento focus-visible:shadow-[0_0_0_3px_rgba(43,95,166,0.2)] focus-visible:outline-none',
            'disabled:cursor-not-allowed disabled:bg-[#f5f1e9] disabled:text-suave',
            !value && 'text-[#a8a29e]',
            marcada &&
              'border-error bg-error-suave hover:not-disabled:border-error focus-visible:border-error focus-visible:shadow-[0_0_0_3px_rgba(179,38,30,0.18)]',
            className,
          )}
          {...props}
        >
          <option value="">{textoVacio}</option>
          {opciones.map((opcion) => (
            <option
              key={opcion.valor}
              value={opcion.valor}
              disabled={opcion.deshabilitada}
              className="text-tinta"
            >
              {opcion.texto}
            </option>
          ))}
        </select>
        <span
          className="pointer-events-none absolute top-0 right-3 flex h-[42px] items-center text-suave"
          aria-hidden="true"
        >
          {cargando ? (
            <Loader2 className="h-4 w-4 animate-girar" />
          ) : (
            <ChevronDown className="h-4 w-4" />
          )}
        </span>
      </div>
      {ayuda && (
        <p className="mt-1 text-[12px] text-suave" data-testid={testIdAyuda}>
          {ayuda}
        </p>
      )}
      <ErrorCampo id={idError} testId={testIdError} mensaje={error} accion={accionError} />
    </div>
  );
}
