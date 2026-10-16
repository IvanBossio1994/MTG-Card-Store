import { useId, type FormEvent, type ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '@/compartido/estilos/combinarClases';

export interface TarjetaProps {
  /** Ícono Lucide del bloque (20 px, en un cuadradito de 32 px). */
  Icono: LucideIcon;
  titulo: string;
  bajada: string;
  /** Línea opcional pegada a la bajada (por ejemplo, "Conectado como …"). */
  detalle?: ReactNode;
  /** Fila del pie: el botón Guardar y su mensaje. Queda al pie, alineada con la tarjeta vecina. */
  pie?: ReactNode;
  /**
   * Se llama al apretar Guardar o Enter en un campo del bloque. Sin `alEnviar`, la tarjeta no es un formulario:
   * sirve para bloques con varias filas, cada una con su propio `<form>` (por ejemplo, el del Sheet).
   */
  alEnviar?: () => void;
  /** Clases extra de la tarjeta (por ejemplo, cuántas columnas de la grilla ocupa). */
  className?: string;
  children: ReactNode;
}

/**
 * Bloque (tarjeta) de un formulario: título con ícono, bajada, campos y pie con el Guardar.
 * Con `alEnviar` es un `<form>`, así Enter en cualquier campo guarda el bloque.
 * Ejemplo: `<Tarjeta Icono={Store} titulo="Tienda" bajada="…" alEnviar={guardar} pie={<Boton type="submit">Guardar tienda</Boton>}>…</Tarjeta>`
 */
export function Tarjeta({
  Icono,
  titulo,
  bajada,
  detalle,
  pie,
  alEnviar,
  className,
  children,
}: TarjetaProps) {
  const idTitulo = useId();

  function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    alEnviar?.();
  }

  return (
    <section
      aria-labelledby={idTitulo}
      className={cn(
        'flex flex-col self-stretch rounded-2xl border border-borde bg-white p-6 shadow-tarjeta',
        'transition-[box-shadow,border-color] duration-200 ease-[ease] hover:border-[#ddd6c9] hover:shadow-tarjeta-hover',
        className,
      )}
    >
      <h2
        id={idTitulo}
        className="flex items-center gap-3 text-[18px] font-semibold tracking-[-0.01em]"
      >
        <span className="inline-flex h-8 w-8 flex-none items-center justify-center rounded-[10px] bg-acento-suave text-acento">
          <Icono className="h-5 w-5" aria-hidden="true" />
        </span>
        {titulo}
      </h2>
      <p className={cn('mt-1 text-[14px] text-suave', detalle ? 'mb-1' : 'mb-4')}>{bajada}</p>
      {detalle}
      {alEnviar ? (
        <form noValidate onSubmit={enviar} className="flex flex-1 flex-col">
          {children}
          {pie && <div className="mt-auto flex items-center gap-4 pt-4">{pie}</div>}
        </form>
      ) : (
        <div className="flex flex-1 flex-col">
          {children}
          {pie && <div className="mt-auto flex items-center gap-4 pt-4">{pie}</div>}
        </div>
      )}
    </section>
  );
}
