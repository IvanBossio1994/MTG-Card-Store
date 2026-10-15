import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Junta clases de Tailwind y resuelve los choques (gana la última). Se llama `cn` porque
 * así la importan los componentes de shadcn/ui.
 * Ejemplo: `cn('h-10 px-3', activo && 'bg-acento-suave')`
 */
export function cn(...clases: ClassValue[]): string {
  return twMerge(clsx(clases));
}
