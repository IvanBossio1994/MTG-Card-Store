import { StrictMode, useCallback, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Toaster } from 'sonner';
import '@/compartido/estilos/estilos.css';
import { textos } from '@/compartido/textos/textos';
import { ConfiguracionPagina } from '@/modulos/configuracion/ConfiguracionPagina';
import type { BloqueTienda } from '@/modulos/configuracion/configuracionApi';
import { MarcoApp, moduloDeRuta } from './MarcoApp';

/**
 * Arranque de la app v2: el marco con el módulo activo según la ruta y la pantalla de ese módulo.
 * El nombre y el logo del menú se actualizan cuando Configuración los carga o los guarda.
 * Ejemplo: en `/app/configuracion` muestra el marco con la pantalla Configuración.
 */
function Aplicacion() {
  const moduloActivo = moduloDeRuta(window.location.pathname);
  const [tienda, setTienda] = useState<{ nombre: string; logo?: string }>({
    nombre: textos['general.texto.nombre-tienda-por-defecto'],
  });

  const actualizarTienda = useCallback((datos: BloqueTienda) => {
    setTienda({
      nombre: datos.nombre || textos['general.texto.nombre-tienda-por-defecto'],
      logo: datos.logoUrl ?? undefined,
    });
  }, []);

  return (
    <MarcoApp nombreTienda={tienda.nombre} logoTienda={tienda.logo} moduloActivo={moduloActivo}>
      {moduloActivo === 'configuracion' && <ConfiguracionPagina alCambiarTienda={actualizarTienda} />}
    </MarcoApp>
  );
}

// Mientras Configuración sea la única pantalla de la v2, `/app` abre Configuración.
if (/^\/app\/?$/.test(window.location.pathname)) {
  window.history.replaceState(null, '', '/app/configuracion');
}

createRoot(document.getElementById('raiz')!).render(
  <StrictMode>
    <Aplicacion />
    <Toaster position="bottom-right" duration={3500} />
  </StrictMode>,
);
