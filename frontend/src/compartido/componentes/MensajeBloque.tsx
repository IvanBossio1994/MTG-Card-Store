import { CheckCircle2, CircleAlert, TriangleAlert, type LucideIcon } from 'lucide-react';
import { cn } from '@/compartido/estilos/combinarClases';
import { textos } from '@/compartido/textos/textos';

/** Tipo de resultado de un bloque: éxito (verde), aviso (ámbar) o error (rojo). */
export type TipoMensaje = 'exito' | 'aviso' | 'error';

/** Resultado que se muestra al lado del Guardar de un bloque. */
export interface MensajeDeBloque {
  tipo: TipoMensaje;
  texto: string;
  /** Solo si venció la sesión de Google: suma el enlace "Volver a iniciar sesión". */
  urlInicioSesion?: string;
}

const ESTILO: Record<TipoMensaje, { Icono: LucideIcon; clases: string }> = {
  exito: { Icono: CheckCircle2, clases: 'border-exito-borde bg-exito-suave text-exito' },
  aviso: { Icono: TriangleAlert, clases: 'border-aviso-borde bg-aviso-suave text-aviso' },
  error: { Icono: CircleAlert, clases: 'border-error-borde bg-error-suave text-error' },
};

export interface MensajeBloqueProps {
  mensaje: MensajeDeBloque | null;
  /** Pantalla del locator (por ejemplo `configuracion`). */
  pantalla: string;
  /** Bloque del locator (por ejemplo `sheet`): queda `<pantalla>-mensaje-<tipo>-<bloque>`. */
  bloque: string;
}

/**
 * Mensaje de resultado de un bloque, en la fila del botón Guardar y a su derecha (no mueve nada).
 * Error con `role="alert"`; éxito y aviso con `role="status"`.
 * Ejemplo: `<MensajeBloque pantalla="configuracion" bloque="tienda" mensaje={{ tipo: 'exito', texto: 'Tienda guardada.' }} />`
 */
export function MensajeBloque({ mensaje, pantalla, bloque }: MensajeBloqueProps) {
  if (!mensaje) {
    return null;
  }
  const { Icono, clases } = ESTILO[mensaje.tipo];
  return (
    <div
      data-testid={`${pantalla}-mensaje-${mensaje.tipo}-${bloque}`}
      role={mensaje.tipo === 'error' ? 'alert' : 'status'}
      className={cn(
        'flex min-w-0 flex-1 animate-entra items-center gap-2 rounded-xl border px-3 py-2 text-[12.5px] font-normal',
        clases,
      )}
    >
      <Icono className="h-4 w-4 shrink-0" aria-hidden="true" />
      <span>
        {mensaje.texto}
        {mensaje.urlInicioSesion && (
          <>
            {' '}
            <a
              href={mensaje.urlInicioSesion}
              className="font-bold underline"
              data-testid={`${pantalla}-enlace-iniciar-sesion-google`}
            >
              {textos['general.boton.volver-a-iniciar-sesion']}
            </a>
          </>
        )}
      </span>
    </div>
  );
}
