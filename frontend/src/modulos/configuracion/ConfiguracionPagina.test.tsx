import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ConfiguracionPagina } from './ConfiguracionPagina';
import type { Configuracion } from './configuracionApi';

const CONFIGURACION: Configuracion = {
  tienda: { nombre: 'Codex Live', logoUrl: undefined },
  sheet: {
    sheetId: '1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ',
    link: 'https://docs.google.com/spreadsheets/d/1vSk7YvkaEKOsomY7hnRGpuAK6Fm8T_ObkPKxZGnd5vQ/edit',
    pestanaInventario: 'Inventario',
    gmail: 'tcgstorehelper@gmail.com',
  },
  precios: {
    cotizacion: 1200,
    redondeo: 500,
    bulk: { common: 50, uncommon: 100, rare: 150, mythic: 150, tierraBasica: 200 },
  },
  puntos: { pesosPorPunto: 1000 },
};

const CUENTA_SERVICIO = 'tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com';

/** Respuesta falsa de fetch con un JSON. */
function respuesta(cuerpo: unknown, estado = 200): Response {
  return { ok: estado >= 200 && estado < 300, status: estado, json: async () => cuerpo } as Response;
}

/** Respuesta ProblemDetail de la API. */
function problema(estado: number, detail: string, extra: Record<string, unknown> = {}): Response {
  return respuesta({ type: 'about:blank', status: estado, detail, ...extra }, estado);
}

/** Promesa que se resuelve a mano, para ver el estado "Guardando…" mientras espera. */
function pendiente<T>() {
  let resolver!: (valor: T) => void;
  const promesa = new Promise<T>((r) => (resolver = r));
  return { promesa, resolver };
}

const fetchFalso = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubGlobal('fetch', fetchFalso);
});

afterEach(() => {
  fetchFalso.mockReset();
  vi.unstubAllGlobals();
});

/** Abre la pantalla con la configuración cargada. */
async function abrirPantalla() {
  fetchFalso.mockResolvedValueOnce(respuesta(CONFIGURACION));
  const alCambiarTienda = vi.fn();
  render(<ConfiguracionPagina alCambiarTienda={alCambiarTienda} />);
  await screen.findByTestId('configuracion-campo-nombre-tienda');
  return { alCambiarTienda };
}

/** Cuerpo JSON del último pedido a la API. */
function ultimoCuerpo(): unknown {
  const [, opciones] = fetchFalso.mock.calls.at(-1)!;
  return JSON.parse(String(opciones?.body));
}

describe('ConfiguracionPagina · carga (CA1, CA2)', () => {
  it('muestra "Cargando la configuración…" y después los bloques con los valores guardados y el Gmail', async () => {
    const espera = pendiente<Response>();
    fetchFalso.mockReturnValueOnce(espera.promesa);
    const alCambiarTienda = vi.fn();
    render(<ConfiguracionPagina alCambiarTienda={alCambiarTienda} />);

    expect(screen.getByRole('status')).toHaveTextContent('Cargando la configuración…');

    await act(async () => espera.resolver(respuesta(CONFIGURACION)));

    expect(screen.queryByText('Cargando la configuración…')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Tu tienda' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Inventario en Google Sheets' })).toBeInTheDocument();
    expect(screen.getByTestId('configuracion-campo-nombre-tienda')).toHaveValue('Codex Live');
    expect(screen.getByTestId('configuracion-campo-nombre-tienda')).toHaveFocus();
    expect(screen.getByTestId('configuracion-campo-sheet')).toHaveValue(CONFIGURACION.sheet!.link);
    expect(screen.getByTestId('configuracion-campo-pestana-inventario')).toHaveValue('Inventario');
    expect(screen.getByRole('heading', { name: 'Regla de precio' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Puntos de clientes' })).toBeInTheDocument();
    expect(screen.getByTestId('configuracion-campo-cotizacion')).toHaveValue('1200');
    expect(screen.getByTestId('configuracion-campo-redondeo')).toHaveValue('500');
    expect(screen.getByTestId('configuracion-campo-carpeta-cache')).toHaveValue('');
    expect(screen.getByTestId('configuracion-campo-bulk-common')).toHaveValue('50');
    expect(screen.getByTestId('configuracion-campo-bulk-tierra-basica')).toHaveValue('200');
    expect(screen.getByTestId('configuracion-campo-pesos-por-punto')).toHaveValue('1000');
    expect(screen.getByTestId('configuracion-texto-ejemplo-puntos')).toHaveTextContent(
      'Una compra de $25.000 suma 25 puntos.',
    );
    expect(screen.getByTestId('configuracion-texto-gmail-conectado')).toHaveTextContent(
      'Conectado como tcgstorehelper@gmail.com',
    );
    expect(fetchFalso).toHaveBeenCalledWith('/api/v1/configuracion', expect.anything());
    expect(alCambiarTienda).toHaveBeenCalledWith(CONFIGURACION.tienda);
  });

  it('si la API no responde muestra la falla con "Reintentar", y al reintentar carga', async () => {
    fetchFalso.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    render(<ConfiguracionPagina />);

    expect(await screen.findByText('No pudimos cargar la configuración')).toBeInTheDocument();
    expect(screen.getByText('Revisá tu conexión a internet e intentá de nuevo.')).toBeInTheDocument();

    fetchFalso.mockResolvedValueOnce(respuesta(CONFIGURACION));
    fireEvent.click(screen.getByTestId('configuracion-boton-reintentar'));

    expect(await screen.findByTestId('configuracion-campo-nombre-tienda')).toHaveValue('Codex Live');
  });

  it('con la sesión de Google vencida al cargar, ofrece volver a iniciar sesión', async () => {
    fetchFalso.mockResolvedValueOnce(
      problema(401, 'Tu sesión de Google venció.', { urlInicioSesion: '/configuracion/google/oauth/iniciar' }),
    );
    render(<ConfiguracionPagina />);

    expect(await screen.findByText(/Tu sesión de Google venció\./)).toBeInTheDocument();
    expect(screen.getByTestId('configuracion-enlace-iniciar-sesion-google')).toHaveAttribute(
      'href',
      '/configuracion/google/oauth/iniciar',
    );
  });
});

describe('ConfiguracionPagina · bloque Tienda (CA3)', () => {
  it('guarda la tienda: "Guardando…" hasta que responde y después "Tienda guardada."', async () => {
    const { alCambiarTienda } = await abrirPantalla();
    const espera = pendiente<Response>();
    fetchFalso.mockReturnValueOnce(espera.promesa);

    fireEvent.change(screen.getByTestId('configuracion-campo-nombre-tienda'), { target: { value: 'Codex Live Store' } });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-tienda'));

    const boton = screen.getByTestId('configuracion-boton-guardar-tienda');
    expect(boton).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Guardando…' })).toBe(boton);
    expect(ultimoCuerpo()).toEqual({ nombre: 'Codex Live Store', quitarLogo: false });

    await act(async () => espera.resolver(respuesta({ nombre: 'Codex Live Store', logoUrl: null })));

    expect(screen.getByTestId('configuracion-mensaje-exito-tienda')).toHaveTextContent('Tienda guardada.');
    expect(screen.getByTestId('configuracion-mensaje-exito-tienda')).toHaveAttribute('role', 'status');
    expect(boton).toBeEnabled();
    expect(alCambiarTienda).toHaveBeenLastCalledWith({ nombre: 'Codex Live Store', logoUrl: null });
  });

  it('con Enter en el nombre guarda el bloque', async () => {
    await abrirPantalla();
    fetchFalso.mockResolvedValueOnce(respuesta({ nombre: 'Codex Live', logoUrl: null }));

    fireEvent.submit(screen.getByTestId('configuracion-campo-nombre-tienda').closest('form')!);

    expect(await screen.findByTestId('configuracion-mensaje-exito-tienda')).toBeInTheDocument();
  });

  it('con el nombre vacío marca el campo, pone el foco y no llama a la API', async () => {
    await abrirPantalla();
    const campo = screen.getByTestId('configuracion-campo-nombre-tienda');
    campo.blur();

    fireEvent.change(campo, { target: { value: '   ' } });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-tienda'));

    expect(screen.getByTestId('configuracion-mensaje-error-nombre-tienda')).toHaveTextContent(
      'Ingresá el nombre de la tienda.',
    );
    expect(campo).toHaveFocus();
    expect(campo).toHaveAttribute('aria-invalid', 'true');
    expect(fetchFalso).toHaveBeenCalledTimes(1);
  });

  it('con un logo de más de 2 MB o que no es PNG, JPG ni WEBP muestra el error y el logo no cambia', async () => {
    await abrirPantalla();
    const selector = document.querySelector<HTMLInputElement>('input[type="file"]')!;
    const logo = () => document.querySelector('img[src="/images/tcg-default-logo.png"]');
    expect(logo()).toBeInTheDocument();

    const pesado = new File(['x'], 'logo.png', { type: 'image/png' });
    Object.defineProperty(pesado, 'size', { value: 2 * 1024 * 1024 + 1 });
    fireEvent.change(selector, { target: { files: [pesado] } });
    expect(screen.getByTestId('configuracion-mensaje-error-logo')).toHaveTextContent('Usá un logo de hasta 2 MB.');

    fireEvent.change(selector, { target: { files: [new File(['x'], 'logo.gif', { type: 'image/gif' })] } });
    expect(screen.getByTestId('configuracion-mensaje-error-logo')).toHaveTextContent('Usá un PNG, JPG o WEBP.');
    expect(logo()).toBeInTheDocument();
    expect(fetchFalso).toHaveBeenCalledTimes(1);
  });

  it('un logo válido se sube al guardar la tienda, antes del nombre', async () => {
    await abrirPantalla();
    const selector = document.querySelector<HTMLInputElement>('input[type="file"]')!;
    fireEvent.change(selector, { target: { files: [new File(['png'], 'logo.png', { type: 'image/png' })] } });
    fetchFalso
      .mockResolvedValueOnce(respuesta({ logoUrl: '/api/v1/configuracion/tienda/logo?version=2' }))
      .mockResolvedValueOnce(respuesta({ nombre: 'Codex Live', logoUrl: '/api/v1/configuracion/tienda/logo?version=2' }));

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-tienda'));

    expect(await screen.findByTestId('configuracion-mensaje-exito-tienda')).toBeInTheDocument();
    expect(fetchFalso.mock.calls[1][0]).toBe('/api/v1/configuracion/tienda/logo');
    expect(fetchFalso.mock.calls[1][1]?.body).toBeInstanceOf(FormData);
    expect(fetchFalso.mock.calls[2][0]).toBe('/api/v1/configuracion/tienda');
    expect(document.querySelector('img[src="/api/v1/configuracion/tienda/logo?version=2"]')).toBeInTheDocument();
  });

  it('"Quitar" muestra el logo por defecto y se aplica al guardar', async () => {
    fetchFalso.mockResolvedValueOnce(
      respuesta({ ...CONFIGURACION, tienda: { nombre: 'Codex Live', logoUrl: '/api/v1/configuracion/tienda/logo?version=1' } }),
    );
    render(<ConfiguracionPagina />);
    await screen.findByTestId('configuracion-campo-nombre-tienda');

    fireEvent.click(screen.getByTestId('configuracion-boton-quitar-logo'));
    expect(document.querySelector('img[src="/images/tcg-default-logo.png"]')).toBeInTheDocument();

    fetchFalso.mockResolvedValueOnce(respuesta({ nombre: 'Codex Live', logoUrl: null }));
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-tienda'));
    await screen.findByTestId('configuracion-mensaje-exito-tienda');
    expect(ultimoCuerpo()).toEqual({ nombre: 'Codex Live', quitarLogo: true });
  });

  it('el error de la API sobre el logo va debajo del logo', async () => {
    await abrirPantalla();
    const selector = document.querySelector<HTMLInputElement>('input[type="file"]')!;
    fireEvent.change(selector, { target: { files: [new File(['x'], 'logo.png', { type: 'image/png' })] } });
    fetchFalso.mockResolvedValueOnce(
      problema(400, 'Hay datos para corregir.', { errores: { archivo: 'Usá un PNG, JPG o WEBP.' } }),
    );

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-tienda'));

    await waitFor(() =>
      expect(screen.getByTestId('configuracion-mensaje-error-logo')).toHaveTextContent('Usá un PNG, JPG o WEBP.'),
    );
    expect(screen.getByTestId('configuracion-boton-subir-logo')).toHaveFocus();
    expect(screen.queryByTestId('configuracion-mensaje-error-tienda')).not.toBeInTheDocument();
  });
});

describe('ConfiguracionPagina · bloque Inventario (CA4, CA5)', () => {
  /** Escribe el Sheet y la pestaña y guarda; la API responde lo que se pase. */
  async function guardarSheet(respuestaApi: Response, sheet = '1PruebaSheetConPestanaUnica0000000000000000') {
    await abrirPantalla();
    const espera = pendiente<Response>();
    fetchFalso.mockReturnValueOnce(espera.promesa);
    fireEvent.change(screen.getByTestId('configuracion-campo-sheet'), { target: { value: sheet } });
    fireEvent.change(screen.getByTestId('configuracion-campo-pestana-inventario'), { target: { value: 'Inventario' } });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-sheet'));
    expect(screen.getByRole('status')).toHaveTextContent('Sincronizando con tu Sheet…');
    expect(screen.getByTestId('configuracion-boton-guardar-sheet')).toBeDisabled();
    await act(async () => espera.resolver(respuestaApi));
  }

  it('avisa que la pestaña se renombró', async () => {
    await guardarSheet(
      respuesta({
        sheetId: '1PruebaSheetConPestanaUnica0000000000000000',
        link: 'https://docs.google.com/spreadsheets/d/1PruebaSheetConPestanaUnica0000000000000000/edit',
        pestanaInventario: 'Inventario',
        gmail: 'tcgstorehelper@gmail.com',
        aviso: 'La pestaña «Hoja 1» se renombró a «Inventario».',
      }),
    );

    expect(ultimoCuerpo()).toEqual({ sheet: '1PruebaSheetConPestanaUnica0000000000000000', pestanaInventario: 'Inventario' });
    expect(screen.queryByText('Sincronizando con tu Sheet…')).not.toBeInTheDocument();
    expect(screen.getByTestId('configuracion-mensaje-aviso-sheet')).toHaveTextContent(
      'La pestaña «Hoja 1» se renombró a «Inventario».',
    );
    expect(screen.getByTestId('configuracion-campo-sheet')).toHaveValue(
      'https://docs.google.com/spreadsheets/d/1PruebaSheetConPestanaUnica0000000000000000/edit',
    );
  });

  it('avisa que se creó la pestaña, y sin aviso muestra "Inventario guardado."', async () => {
    await guardarSheet(respuesta({ pestanaInventario: 'Inventario', aviso: 'Se creó la pestaña «Inventario».' }));
    expect(screen.getByTestId('configuracion-mensaje-aviso-sheet')).toHaveTextContent('Se creó la pestaña «Inventario».');

    fetchFalso.mockResolvedValueOnce(respuesta({ pestanaInventario: 'Inventario', aviso: null }));
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-sheet'));
    expect(await screen.findByTestId('configuracion-mensaje-exito-sheet')).toHaveTextContent('Inventario guardado.');
  });

  it('con un link inválido marca el campo del Sheet', async () => {
    await guardarSheet(
      problema(400, 'Hay datos para corregir.', { errores: { sheet: 'Pegá una URL de Google Sheets o solo el ID.' } }),
      'https://mal',
    );

    expect(screen.getByTestId('configuracion-mensaje-error-link-sheet')).toHaveTextContent(
      'Pegá una URL de Google Sheets o solo el ID.',
    );
    expect(screen.getByTestId('configuracion-campo-sheet')).toHaveFocus();
    expect(screen.queryByTestId('configuracion-mensaje-error-sheet')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('con el Sheet sin compartir muestra el error de bloque con la cuenta de servicio', async () => {
    const detalle = `La app no tiene acceso a este Sheet. Compartilo con ${CUENTA_SERVICIO} como editor.`;
    await guardarSheet(problema(403, detalle, { cuentaServicio: CUENTA_SERVICIO }));

    const mensaje = screen.getByTestId('configuracion-mensaje-error-sheet');
    expect(mensaje).toHaveTextContent(detalle);
    expect(mensaje).toHaveAttribute('role', 'alert');
  });

  it('con la sesión vencida muestra el error con "Volver a iniciar sesión" y no pierde lo escrito en Tienda', async () => {
    await abrirPantalla();
    fireEvent.change(screen.getByTestId('configuracion-campo-nombre-tienda'), { target: { value: 'Sin guardar' } });
    fetchFalso.mockResolvedValueOnce(
      problema(401, 'Tu sesión de Google venció.', {
        accion: 'volver-a-iniciar-sesion',
        urlInicioSesion: '/configuracion/google/oauth/iniciar',
      }),
    );

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-sheet'));

    expect(await screen.findByTestId('configuracion-mensaje-error-sheet')).toHaveTextContent(
      'Tu sesión de Google venció. Volver a iniciar sesión',
    );
    expect(screen.getByTestId('configuracion-enlace-iniciar-sesion-google')).toHaveAttribute(
      'href',
      '/configuracion/google/oauth/iniciar',
    );
    expect(screen.getByTestId('configuracion-campo-nombre-tienda')).toHaveValue('Sin guardar');
  });

  it('sin conexión con Google muestra su mensaje, no un genérico', async () => {
    await guardarSheet(problema(503, 'Revisá tu conexión a internet e intentá de nuevo.'));
    expect(screen.getByTestId('configuracion-mensaje-error-sheet')).toHaveTextContent(
      'Revisá tu conexión a internet e intentá de nuevo.',
    );
  });

  it('con los campos vacíos marca los dos y no llama a la API', async () => {
    await abrirPantalla();
    fireEvent.change(screen.getByTestId('configuracion-campo-sheet'), { target: { value: '' } });
    fireEvent.change(screen.getByTestId('configuracion-campo-pestana-inventario'), { target: { value: ' ' } });

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-sheet'));

    expect(screen.getByTestId('configuracion-mensaje-error-link-sheet')).toHaveTextContent(
      'Pegá el ID o la URL del Sheet.',
    );
    expect(screen.getByTestId('configuracion-mensaje-error-pestana-inventario')).toHaveTextContent(
      'Ingresá el nombre de la pestaña.',
    );
    expect(screen.getByTestId('configuracion-campo-sheet')).toHaveFocus();
    expect(fetchFalso).toHaveBeenCalledTimes(1);
  });
});

describe('ConfiguracionPagina · bloque Regla de precio (CA6)', () => {
  it('guarda los precios: "Guardando…" en el botón y después "Precios guardados. Se recalculan."', async () => {
    await abrirPantalla();
    const espera = pendiente<Response>();
    fetchFalso.mockReturnValueOnce(espera.promesa);

    fireEvent.change(screen.getByTestId('configuracion-campo-cotizacion'), { target: { value: '1350,5' } });
    fireEvent.change(screen.getByTestId('configuracion-campo-carpeta-cache'), { target: { value: '  D:/TCG/cache ' } });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-precios'));

    const boton = screen.getByTestId('configuracion-boton-guardar-precios');
    expect(boton).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Guardando…' })).toBe(boton);
    expect(fetchFalso.mock.calls.at(-1)![0]).toBe('/api/v1/configuracion/precios');
    expect(ultimoCuerpo()).toEqual({
      cotizacion: 1350.5,
      redondeo: 500,
      bulk: { common: 50, uncommon: 100, rare: 150, mythic: 150, tierraBasica: 200 },
      carpetaCache: 'D:/TCG/cache',
    });

    await act(async () =>
      espera.resolver(
        respuesta({ ...CONFIGURACION.precios, cotizacion: 1350.5, carpetaCache: 'D:/TCG/cache', aviso: null }),
      ),
    );

    expect(screen.getByTestId('configuracion-mensaje-exito-precios')).toHaveTextContent(
      'Precios guardados. Se recalculan.',
    );
    expect(boton).toBeEnabled();
  });

  it('con un bulk con centavos marca ese campo, con el error de bloque, y no llama a la API', async () => {
    await abrirPantalla();
    fireEvent.change(screen.getByTestId('configuracion-campo-bulk-rare'), { target: { value: '300,50' } });
    fireEvent.change(screen.getByTestId('configuracion-campo-cotizacion'), { target: { value: '' } });
    fireEvent.change(screen.getByTestId('configuracion-campo-bulk-mythic'), { target: { value: '0' } });

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-precios'));

    expect(screen.getByTestId('configuracion-mensaje-error-bulk-rare')).toHaveTextContent('Usá un entero, sin centavos.');
    expect(screen.getByTestId('configuracion-mensaje-error-cotizacion')).toHaveTextContent(
      'Ingresá la cotización del dólar.',
    );
    expect(screen.getByTestId('configuracion-mensaje-error-bulk-mythic')).toHaveTextContent('Tiene que ser mayor a 0.');
    expect(screen.getByTestId('configuracion-mensaje-error-precios')).toHaveTextContent(
      'No se guardó. Corregí el campo marcado en rojo.',
    );
    expect(screen.getByTestId('configuracion-campo-cotizacion')).toHaveFocus();
    expect(fetchFalso).toHaveBeenCalledTimes(1);
  });

  it('el error de la API va debajo de su campo (carpeta que no existe)', async () => {
    await abrirPantalla();
    fetchFalso.mockResolvedValueOnce(
      problema(400, 'Hay datos para corregir.', {
        errores: { carpetaCache: 'Esa carpeta no existe. Elegí otra o dejala vacía.', 'bulk.rare': 'Usá un entero, sin centavos.' },
      }),
    );
    fireEvent.change(screen.getByTestId('configuracion-campo-carpeta-cache'), { target: { value: 'Z:/no-existe' } });

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-precios'));

    expect(await screen.findByTestId('configuracion-mensaje-error-carpeta-cache')).toHaveTextContent(
      'Esa carpeta no existe. Elegí otra o dejala vacía.',
    );
    expect(screen.getByTestId('configuracion-mensaje-error-bulk-rare')).toHaveTextContent('Usá un entero, sin centavos.');
    expect(screen.getByTestId('configuracion-mensaje-error-precios')).toHaveTextContent(
      'No se guardó. Corregí el campo marcado en rojo.',
    );
    expect(screen.getByTestId('configuracion-campo-carpeta-cache')).toHaveFocus();
  });

  it('si falla Card Kingdom muestra el aviso en lugar del éxito', async () => {
    await abrirPantalla();
    const aviso = 'Guardado, pero no se pudo sincronizar con Card Kingdom. Probá más tarde.';
    fetchFalso.mockResolvedValueOnce(respuesta({ ...CONFIGURACION.precios, aviso }));

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-precios'));

    expect(await screen.findByTestId('configuracion-mensaje-aviso-precios')).toHaveTextContent(aviso);
    expect(screen.queryByTestId('configuracion-mensaje-exito-precios')).not.toBeInTheDocument();
  });
});

describe('ConfiguracionPagina · bloque Puntos (CA6)', () => {
  it('el ejemplo se recalcula al escribir y al guardar 500 muestra "Puntos guardados."', async () => {
    await abrirPantalla();
    const campo = screen.getByTestId('configuracion-campo-pesos-por-punto');

    fireEvent.change(campo, { target: { value: '' } });
    expect(screen.getByTestId('configuracion-texto-ejemplo-puntos')).toHaveTextContent(
      'Escribí cuántos pesos vale un punto.',
    );
    fireEvent.change(campo, { target: { value: '500' } });
    expect(screen.getByTestId('configuracion-texto-ejemplo-puntos')).toHaveTextContent(
      'Una compra de $25.000 suma 50 puntos.',
    );

    fetchFalso.mockResolvedValueOnce(respuesta({ pesosPorPunto: 500 }));
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-puntos'));

    expect(await screen.findByTestId('configuracion-mensaje-exito-puntos')).toHaveTextContent('Puntos guardados.');
    expect(fetchFalso.mock.calls.at(-1)![0]).toBe('/api/v1/configuracion/puntos');
    expect(ultimoCuerpo()).toEqual({ pesosPorPunto: 500 });
  });

  it('vacío o en 0 marca el campo y no llama a la API', async () => {
    await abrirPantalla();
    const campo = screen.getByTestId('configuracion-campo-pesos-por-punto');

    fireEvent.change(campo, { target: { value: ' ' } });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-puntos'));
    expect(screen.getByTestId('configuracion-mensaje-error-pesos-por-punto')).toHaveTextContent(
      'Ingresá cuántos pesos vale un punto.',
    );
    expect(campo).toHaveFocus();

    fireEvent.change(campo, { target: { value: '0' } });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-puntos'));
    expect(screen.getByTestId('configuracion-mensaje-error-pesos-por-punto')).toHaveTextContent('Tiene que ser mayor a 0.');
    expect(fetchFalso).toHaveBeenCalledTimes(1);
  });
});

describe('ConfiguracionPagina · salir con cambios sin guardar (CA7)', () => {
  /** Abre la pantalla con un enlace de menú al lado; registra las navegaciones que dejan pasar. */
  async function abrirConEnlace() {
    fetchFalso.mockResolvedValueOnce(respuesta(CONFIGURACION));
    render(
      <>
        <a href="/app/inventario" data-testid="app-enlace-inventario">
          Inventario
        </a>
        <ConfiguracionPagina />
      </>,
    );
    await screen.findByTestId('configuracion-campo-nombre-tienda');
    const navegaciones: string[] = [];
    // Burbujeo: corre después del aviso. Frena la navegación real de jsdom y anota si pasó.
    const anotar = (evento: MouseEvent) => {
      const enlace = (evento.target as Element).closest('a[href]');
      if (!enlace) {
        return;
      }
      if (!evento.defaultPrevented) {
        navegaciones.push(enlace.getAttribute('href') ?? '');
      }
      evento.preventDefault();
    };
    document.addEventListener('click', anotar);
    return { navegaciones, quitar: () => document.removeEventListener('click', anotar) };
  }

  it('sin cambios sale directo, sin modal', async () => {
    const { navegaciones, quitar } = await abrirConEnlace();
    fireEvent.click(screen.getByTestId('app-enlace-inventario'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(navegaciones).toEqual(['/app/inventario']);
    quitar();
  });

  it('con un cambio muestra el modal; "Seguir editando" y Esc se quedan con lo escrito', async () => {
    const { navegaciones, quitar } = await abrirConEnlace();
    fireEvent.change(screen.getByTestId('configuracion-campo-pesos-por-punto'), { target: { value: '750' } });

    fireEvent.click(screen.getByTestId('app-enlace-inventario'));

    const modal = screen.getByRole('dialog', { name: 'Tenés cambios sin guardar' });
    expect(modal).toHaveTextContent('¿Salir igual?');
    expect(screen.getByTestId('configuracion-boton-seguir-editando')).toHaveFocus();
    expect(screen.getByTestId('configuracion-boton-salir-igual')).toHaveTextContent('Salir igual');

    fireEvent.click(screen.getByTestId('configuracion-boton-seguir-editando'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByTestId('configuracion-campo-pesos-por-punto')).toHaveValue('750');

    fireEvent.click(screen.getByTestId('app-enlace-inventario'));
    fireEvent.keyDown(document.activeElement!, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByTestId('configuracion-campo-pesos-por-punto')).toHaveValue('750');
    expect(navegaciones).toEqual([]);
    quitar();
  });

  it('un bloque guardado deja de contar como sin guardar', async () => {
    const { navegaciones, quitar } = await abrirConEnlace();
    fireEvent.change(screen.getByTestId('configuracion-campo-pesos-por-punto'), { target: { value: '500' } });
    fetchFalso.mockResolvedValueOnce(respuesta({ pesosPorPunto: 500 }));
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-puntos'));
    await screen.findByTestId('configuracion-mensaje-exito-puntos');

    fireEvent.click(screen.getByTestId('app-enlace-inventario'));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(navegaciones).toEqual(['/app/inventario']);
    quitar();
  });

  it('"Salir igual" sigue al enlace sin volver a preguntar', async () => {
    const { navegaciones, quitar } = await abrirConEnlace();
    fireEvent.change(screen.getByTestId('configuracion-campo-nombre-tienda'), { target: { value: 'Otra' } });

    fireEvent.click(screen.getByTestId('app-enlace-inventario'));
    expect(navegaciones).toEqual([]);
    fireEvent.click(screen.getByTestId('configuracion-boton-salir-igual'));

    expect(navegaciones).toEqual(['/app/inventario']);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    quitar();
  });
});
