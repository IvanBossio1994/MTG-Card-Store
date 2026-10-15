import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Toaster } from 'sonner';
import '@/compartido/estilos/estilos.css';
import { textos } from '@/compartido/textos/textos';
import { MarcoApp, moduloDeRuta } from './MarcoApp';

// Arranque de la app v2: el marco con el módulo activo según la ruta.
// Las pantallas de cada módulo se suman dentro del marco desde HU-004.
createRoot(document.getElementById('raiz')!).render(
  <StrictMode>
    <MarcoApp
      nombreTienda={textos['general.texto.nombre-tienda-por-defecto']}
      moduloActivo={moduloDeRuta(window.location.pathname)}
    />
    <Toaster position="bottom-right" duration={3500} />
  </StrictMode>,
);
