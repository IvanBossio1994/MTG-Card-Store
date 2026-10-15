import type { ReactNode } from 'react';
import { ArrowLeftRight, Bookmark, Package, Settings2, Wallet, type LucideIcon } from 'lucide-react';
import { cn } from '@/compartido/estilos/combinarClases';
import { textos, type ClaveTexto } from '@/compartido/textos/textos';

/** Módulos que aparecen en el menú lateral, en orden. */
export type ModuloMenu = 'inventario' | 'reservas' | 'caja' | 'movimientos' | 'configuracion';

interface ItemMenu {
  modulo: ModuloMenu;
  clave: ClaveTexto;
  Icono: LucideIcon;
}

const ITEMS_MENU: ItemMenu[] = [
  { modulo: 'inventario', clave: 'menu.inventario', Icono: Package },
  { modulo: 'reservas', clave: 'menu.reservas', Icono: Bookmark },
  { modulo: 'caja', clave: 'menu.caja', Icono: Wallet },
  { modulo: 'movimientos', clave: 'menu.movimientos', Icono: ArrowLeftRight },
  { modulo: 'configuracion', clave: 'menu.configuracion', Icono: Settings2 },
];

/** Logo por defecto del comercio (lo sirve Spring desde static/images). */
export const LOGO_POR_DEFECTO = '/images/tcg-default-logo.png';

export interface MarcoAppProps {
  /** Nombre de la tienda que se ve arriba del menú. */
  nombreTienda: string;
  /** URL del logo de la tienda; si no viene, se usa el logo por defecto. */
  logoTienda?: string;
  /** Módulo de la pantalla actual: su ítem queda marcado con `aria-current="page"`. */
  moduloActivo?: ModuloMenu;
  /** Contenido de la pantalla, a la derecha del menú. */
  children?: ReactNode;
}

/**
 * Marco de todas las pantallas v2: barra lateral de 240 px con logo, nombre de la tienda y menú,
 * y el contenido a la derecha, pegado al menú (igual a docs/v2/ux/configuracion/prototipo.html).
 * Ejemplo: `<MarcoApp nombreTienda="Mazo Mágico" moduloActivo="configuracion"><ConfiguracionPagina /></MarcoApp>`
 */
export function MarcoApp({ nombreTienda, logoTienda = LOGO_POR_DEFECTO, moduloActivo, children }: MarcoAppProps) {
  return (
    <div className="flex">
      <aside
        className="sticky top-0 flex h-screen w-[240px] flex-none flex-col gap-6 self-start border-r border-borde bg-white px-4 py-6"
        aria-label={textos['menu.etiqueta.menu-principal']}
      >
        <div className="flex items-center gap-3 px-2">
          <img
            className="h-10 w-10 rounded-xl border border-borde bg-white object-contain p-1"
            src={logoTienda}
            alt=""
          />
          <span className="text-[16px] leading-tight font-semibold">{nombreTienda}</span>
        </div>
        <nav className="flex flex-col gap-1">
          {ITEMS_MENU.map(({ modulo, clave, Icono }) => {
            const activo = modulo === moduloActivo;
            return (
              <a
                key={modulo}
                href={`/app/${modulo}`}
                data-testid={`app-enlace-${modulo}`}
                aria-current={activo ? 'page' : undefined}
                className={cn(
                  'flex h-10 items-center gap-3 rounded-[10px] px-3 text-[15px] font-medium text-[#57534e]',
                  'transition-[background-color,color] duration-150 ease-[ease]',
                  'hover:bg-[#f5f1e9] hover:text-tinta',
                  activo && 'bg-acento-suave font-semibold text-acento hover:bg-acento-suave hover:text-acento',
                )}
              >
                <Icono className="h-5 w-5" aria-hidden="true" />
                {textos[clave]}
              </a>
            );
          })}
        </nav>
      </aside>

      <div className="min-w-0 flex-1">
        <main className="w-full max-w-[1600px] px-8 py-10">{children}</main>
      </div>
    </div>
  );
}

/**
 * Deduce el módulo activo a partir de la ruta (`/app/<modulo>/...`). Si no coincide con ninguno, devuelve `undefined`.
 * Ejemplo: `moduloDeRuta('/app/configuracion')` → `'configuracion'`
 */
export function moduloDeRuta(ruta: string): ModuloMenu | undefined {
  const segmento = ruta.replace(/^\/app\/?/, '').split('/')[0];
  return ITEMS_MENU.find((item) => item.modulo === segmento)?.modulo;
}
