import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MarcoApp, moduloDeRuta } from './MarcoApp';

describe('MarcoApp', () => {
  it('muestra el nombre de la tienda y los cinco enlaces del menú, en orden', () => {
    render(<MarcoApp nombreTienda="Mazo Mágico" />);

    expect(screen.getByText('Mazo Mágico')).toBeInTheDocument();
    const enlaces = screen.getAllByRole('link');
    expect(enlaces.map((e) => e.textContent)).toEqual([
      'Inventario',
      'Reservas',
      'Caja',
      'Movimientos',
      'Configuración',
    ]);
    expect(screen.getByTestId('app-enlace-inventario')).toHaveAttribute('href', '/app/inventario');
    expect(screen.getByTestId('app-enlace-configuracion')).toHaveAttribute(
      'href',
      '/app/configuracion',
    );
  });

  it('marca solo el módulo activo con aria-current="page"', () => {
    render(<MarcoApp nombreTienda="Mazo Mágico" moduloActivo="configuracion" />);

    expect(screen.getByTestId('app-enlace-configuracion')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('app-enlace-inventario')).not.toHaveAttribute('aria-current');
  });

  it('usa el logo por defecto si la tienda no tiene logo', () => {
    const { container } = render(<MarcoApp nombreTienda="Mazo Mágico" />);

    expect(container.querySelector('img')).toHaveAttribute('src', '/images/tcg-default-logo.png');
  });
});

describe('moduloDeRuta', () => {
  it('deduce el módulo de la ruta y no marca nada en rutas desconocidas', () => {
    expect(moduloDeRuta('/app/configuracion')).toBe('configuracion');
    expect(moduloDeRuta('/app/reservas/12')).toBe('reservas');
    expect(moduloDeRuta('/app')).toBeUndefined();
    expect(moduloDeRuta('/app/otra')).toBeUndefined();
  });
});
