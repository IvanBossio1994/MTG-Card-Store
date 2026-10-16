import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    json: async () => cuerpo,
  } as Response;
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

/** Pestañas del Sheet guardado: dos con encabezados de inventario y dos fijas. */
const PESTANAS = {
  sheetGuardado: true,
  pestanas: [
    { nombre: 'Inventario', enUso: true, sugerida: true, fija: false },
    { nombre: 'stock viejo', enUso: false, sugerida: true, fija: false },
    { nombre: 'Hoja 1', enUso: false, sugerida: false, fija: false },
    { nombre: 'Caja', enUso: false, sugerida: false, fija: true },
    { nombre: 'Movimientos', enUso: false, sugerida: false, fija: true },
  ],
};

const RUTA_PESTANAS = '/api/v1/configuracion/sheet/pestanas';

/** Todas las llamadas menos la de listar pestañas (las cuentan los tests de cada bloque). */
const fetchFalso = vi.fn<typeof fetch>();
/** Solo GET /sheet/pestanas: por defecto responde PESTANAS. */
const listarFalso = vi.fn<typeof fetch>();

beforeEach(() => {
  listarFalso.mockImplementation(async () => respuesta(PESTANAS));
  vi.stubGlobal('fetch', (ruta: RequestInfo | URL, opciones?: RequestInit) => {
    const url = ruta instanceof Request ? ruta.url : ruta.toString();
    const esListar = url === RUTA_PESTANAS || url.startsWith(`${RUTA_PESTANAS}?`);
    return esListar ? listarFalso(ruta, opciones) : fetchFalso(ruta, opciones);
  });
});

afterEach(() => {
  fetchFalso.mockReset();
  listarFalso.mockReset();
  vi.unstubAllGlobals();
});

const selectorTraer = () => screen.getByTestId('configuracion-selector-traer-cartas-de');
const selectorRenombrar = () => screen.getByTestId('configuracion-selector-pestana-a-renombrar');

/** Abre la pantalla con la configuración cargada (y, si hay Sheet guardado, las listas de pestañas). */
async function abrirPantalla(configuracion: Configuracion = CONFIGURACION) {
  fetchFalso.mockResolvedValueOnce(respuesta(configuracion));
  const alCambiarTienda = vi.fn();
  render(<ConfiguracionPagina alCambiarTienda={alCambiarTienda} />);
  await screen.findByTestId('configuracion-campo-nombre-tienda');
  if (configuracion.sheet?.sheetId) {
    await waitFor(() => expect(selectorRenombrar()).toBeEnabled());
  }
  return { alCambiarTienda };
}

/** Cuerpo JSON del último pedido a la API. */
function ultimoCuerpo(): unknown {
  const [, opciones] = fetchFalso.mock.calls.at(-1)!;
  return JSON.parse(opciones?.body as string);
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
    expect(screen.getByRole('heading', { name: 'Tienda' })).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: 'Inventario en Google Sheets' }),
    ).toBeInTheDocument();
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

    expect(await screen.findByText('No se pudo cargar la configuración.')).toBeInTheDocument();
    expect(
      screen.getByText('No hay conexión a internet. Se puede reintentar cuando se restablezca.'),
    ).toBeInTheDocument();

    fetchFalso.mockResolvedValueOnce(respuesta(CONFIGURACION));
    fireEvent.click(screen.getByTestId('configuracion-boton-reintentar'));

    expect(await screen.findByTestId('configuracion-campo-nombre-tienda')).toHaveValue(
      'Codex Live',
    );
  });

  it('con la sesión de Google vencida al cargar, ofrece volver a iniciar sesión', async () => {
    fetchFalso.mockResolvedValueOnce(
      problema(401, 'La sesión de Google venció.', {
        urlInicioSesion: '/configuracion/google/oauth/iniciar',
      }),
    );
    render(<ConfiguracionPagina />);

    expect(await screen.findByText(/La sesión de Google venció\./)).toBeInTheDocument();
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

    fireEvent.change(screen.getByTestId('configuracion-campo-nombre-tienda'), {
      target: { value: 'Codex Live Store' },
    });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-tienda'));

    const boton = screen.getByTestId('configuracion-boton-guardar-tienda');
    expect(boton).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Guardando…' })).toBe(boton);
    expect(ultimoCuerpo()).toEqual({ nombre: 'Codex Live Store', quitarLogo: false });

    await act(async () =>
      espera.resolver(respuesta({ nombre: 'Codex Live Store', logoUrl: null })),
    );

    expect(screen.getByTestId('configuracion-mensaje-exito-tienda')).toHaveTextContent(
      'Tienda guardada.',
    );
    expect(screen.getByTestId('configuracion-mensaje-exito-tienda')).toHaveAttribute(
      'role',
      'status',
    );
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
      'El nombre de la tienda es obligatorio.',
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
    expect(screen.getByTestId('configuracion-mensaje-error-logo')).toHaveTextContent(
      'El logo no debe superar los 2 MB.',
    );

    fireEvent.change(selector, {
      target: { files: [new File(['x'], 'logo.gif', { type: 'image/gif' })] },
    });
    expect(screen.getByTestId('configuracion-mensaje-error-logo')).toHaveTextContent(
      'El logo debe ser PNG, JPG o WEBP.',
    );
    expect(logo()).toBeInTheDocument();
    expect(fetchFalso).toHaveBeenCalledTimes(1);
  });

  it('un logo válido se sube al guardar la tienda, antes del nombre', async () => {
    await abrirPantalla();
    const selector = document.querySelector<HTMLInputElement>('input[type="file"]')!;
    fireEvent.change(selector, {
      target: { files: [new File(['png'], 'logo.png', { type: 'image/png' })] },
    });
    fetchFalso
      .mockResolvedValueOnce(respuesta({ logoUrl: '/api/v1/configuracion/tienda/logo?version=2' }))
      .mockResolvedValueOnce(
        respuesta({ nombre: 'Codex Live', logoUrl: '/api/v1/configuracion/tienda/logo?version=2' }),
      );

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-tienda'));

    expect(await screen.findByTestId('configuracion-mensaje-exito-tienda')).toBeInTheDocument();
    expect(fetchFalso.mock.calls[1][0]).toBe('/api/v1/configuracion/tienda/logo');
    expect(fetchFalso.mock.calls[1][1]?.body).toBeInstanceOf(FormData);
    expect(fetchFalso.mock.calls[2][0]).toBe('/api/v1/configuracion/tienda');
    expect(
      document.querySelector('img[src="/api/v1/configuracion/tienda/logo?version=2"]'),
    ).toBeInTheDocument();
  });

  it('"Quitar" muestra el logo por defecto y se aplica al guardar', async () => {
    fetchFalso.mockResolvedValueOnce(
      respuesta({
        ...CONFIGURACION,
        tienda: { nombre: 'Codex Live', logoUrl: '/api/v1/configuracion/tienda/logo?version=1' },
      }),
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
    fireEvent.change(selector, {
      target: { files: [new File(['x'], 'logo.png', { type: 'image/png' })] },
    });
    fetchFalso.mockResolvedValueOnce(
      problema(400, 'Hay datos para corregir.', {
        errores: { archivo: 'El logo debe ser PNG, JPG o WEBP.' },
      }),
    );

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-tienda'));

    await waitFor(() =>
      expect(screen.getByTestId('configuracion-mensaje-error-logo')).toHaveTextContent(
        'El logo debe ser PNG, JPG o WEBP.',
      ),
    );
    expect(screen.getByTestId('configuracion-boton-subir-logo')).toHaveFocus();
    expect(screen.queryByTestId('configuracion-mensaje-error-tienda')).not.toBeInTheDocument();
  });
});

describe('ConfiguracionPagina · bloque Inventario (CA4, CA5)', () => {
  /** Escribe el Sheet y la pestaña y guarda; la API responde lo que se pase. */
  async function guardarSheet(
    respuestaApi: Response,
    sheet = '1PruebaSheetConPestanaUnica0000000000000000',
  ) {
    await abrirPantalla();
    const espera = pendiente<Response>();
    fetchFalso.mockReturnValueOnce(espera.promesa);
    fireEvent.change(screen.getByTestId('configuracion-campo-sheet'), { target: { value: sheet } });
    fireEvent.change(screen.getByTestId('configuracion-campo-pestana-inventario'), {
      target: { value: 'Inventario' },
    });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-sheet'));
    expect(screen.getByRole('status')).toHaveTextContent('Sincronizando con el Sheet…');
    expect(screen.getByTestId('configuracion-boton-guardar-sheet')).toBeDisabled();
    await act(async () => espera.resolver(respuestaApi));
  }

  it('con la pestaña existente no manda "Traer cartas de" y muestra "Inventario guardado."', async () => {
    await guardarSheet(
      respuesta({
        sheetId: '1PruebaSheetConPestanaUnica0000000000000000',
        link: 'https://docs.google.com/spreadsheets/d/1PruebaSheetConPestanaUnica0000000000000000/edit',
        pestanaInventario: 'Inventario',
        gmail: 'tcgstorehelper@gmail.com',
        aviso: null,
        pestanaCreada: false,
        cartasTraidas: null,
      }),
    );

    expect(ultimoCuerpo()).toEqual({
      sheet: '1PruebaSheetConPestanaUnica0000000000000000',
      pestanaInventario: 'Inventario',
    });
    expect(screen.queryByText('Sincronizando con el Sheet…')).not.toBeInTheDocument();
    expect(screen.getByTestId('configuracion-mensaje-exito-sheet')).toHaveTextContent(
      'Inventario guardado.',
    );
    expect(screen.getByTestId('configuracion-campo-sheet')).toHaveValue(
      'https://docs.google.com/spreadsheets/d/1PruebaSheetConPestanaUnica0000000000000000/edit',
    );
    // Después de guardar se recargan las listas (una sola llamada, del Sheet guardado).
    await waitFor(() => expect(listarFalso).toHaveBeenCalledTimes(2));
    expect(listarFalso.mock.calls[1][0]).toBe(RUTA_PESTANAS);
  });

  it('con un link inválido marca el campo del Sheet', async () => {
    await guardarSheet(
      problema(400, 'Hay datos para corregir.', {
        errores: { sheet: 'Debe ser una URL de Google Sheets o solo el ID.' },
      }),
      'https://mal',
    );

    expect(screen.getByTestId('configuracion-mensaje-error-link-sheet')).toHaveTextContent(
      'Debe ser una URL de Google Sheets o solo el ID.',
    );
    expect(screen.getByTestId('configuracion-campo-sheet')).toHaveFocus();
    expect(screen.queryByTestId('configuracion-mensaje-error-sheet')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('con el Sheet sin compartir muestra el error de bloque con la cuenta de servicio', async () => {
    const detalle = `La app no tiene acceso a este Sheet. Compartirlo con ${CUENTA_SERVICIO} como editor.`;
    await guardarSheet(problema(403, detalle, { cuentaServicio: CUENTA_SERVICIO }));

    const mensaje = screen.getByTestId('configuracion-mensaje-error-sheet');
    expect(mensaje).toHaveTextContent(detalle);
    expect(mensaje).toHaveAttribute('role', 'alert');
  });

  it('con la sesión vencida muestra el error con "Volver a iniciar sesión" y no pierde lo escrito en Tienda', async () => {
    await abrirPantalla();
    fireEvent.change(screen.getByTestId('configuracion-campo-nombre-tienda'), {
      target: { value: 'Sin guardar' },
    });
    fetchFalso.mockResolvedValueOnce(
      problema(401, 'La sesión de Google venció.', {
        accion: 'volver-a-iniciar-sesion',
        urlInicioSesion: '/configuracion/google/oauth/iniciar',
      }),
    );

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-sheet'));

    expect(await screen.findByTestId('configuracion-mensaje-error-sheet')).toHaveTextContent(
      'La sesión de Google venció. Volver a iniciar sesión',
    );
    expect(screen.getByTestId('configuracion-enlace-iniciar-sesion-google')).toHaveAttribute(
      'href',
      '/configuracion/google/oauth/iniciar',
    );
    expect(screen.getByTestId('configuracion-campo-nombre-tienda')).toHaveValue('Sin guardar');
  });

  it('sin conexión con Google muestra su mensaje, no un genérico', async () => {
    const conexion = 'No hay conexión a internet. Se puede reintentar cuando se restablezca.';
    await guardarSheet(problema(503, conexion));
    expect(screen.getByTestId('configuracion-mensaje-error-sheet')).toHaveTextContent(conexion);
  });

  it('con los campos vacíos marca los dos y no llama a la API', async () => {
    await abrirPantalla();
    fireEvent.change(screen.getByTestId('configuracion-campo-sheet'), { target: { value: '' } });
    fireEvent.change(screen.getByTestId('configuracion-campo-pestana-inventario'), {
      target: { value: ' ' },
    });

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-sheet'));

    expect(screen.getByTestId('configuracion-mensaje-error-link-sheet')).toHaveTextContent(
      'El ID o la URL del Sheet es obligatorio.',
    );
    expect(screen.getByTestId('configuracion-mensaje-error-pestana-inventario')).toHaveTextContent(
      'El nombre de la pestaña es obligatorio.',
    );
    expect(screen.getByTestId('configuracion-campo-sheet')).toHaveFocus();
    expect(fetchFalso).toHaveBeenCalledTimes(1);
  });
});

/** Configuración sin Sheet guardado (primera vez). */
const SIN_SHEET: Configuracion = { ...CONFIGURACION, sheet: { pestanaInventario: 'Inventario' } };

/** Lista con una sola pestaña reconocida ("stock viejo") y sin "Stock 2026". */
const CON_STOCK_VIEJO = {
  sheetGuardado: true,
  pestanas: [
    { nombre: 'Ventas', enUso: false, sugerida: false, fija: false },
    { nombre: 'stock viejo', enUso: false, sugerida: true, fija: false },
  ],
};

/** Textos de las opciones de una lista. */
function opciones(lista: HTMLElement): string[] {
  return [...lista.querySelectorAll('option')].map((opcion) => opcion.textContent ?? '');
}

describe('ConfiguracionPagina · Inventario: "Traer cartas de" (HU-007 CA2 a CA4, CA9)', () => {
  it('al abrir llena las dos listas con una sola llamada y preselecciona la sugerida en uso', async () => {
    await abrirPantalla();

    expect(listarFalso).toHaveBeenCalledTimes(1);
    expect(listarFalso.mock.calls[0][0]).toBe(RUTA_PESTANAS);
    expect(selectorTraer()).toBeEnabled();
    expect(selectorTraer()).toHaveValue('Inventario');
    expect(opciones(selectorTraer())).toEqual([
      'No traer cartas',
      'Inventario · sugerida',
      'stock viejo · sugerida',
      'Hoja 1',
      'Caja',
      'Movimientos',
    ]);
    expect(screen.getByTestId('configuracion-texto-ayuda-traer-cartas-de')).toHaveTextContent(
      'Solo si la pestaña del inventario es nueva. La de origen no se toca.',
    );
  });

  it('trae las cartas: capa "Trayendo las cartas…" y el éxito con la cantidad y las columnas extra', async () => {
    listarFalso.mockImplementation(async () => respuesta(CON_STOCK_VIEJO));
    await abrirPantalla();
    expect(selectorTraer()).toHaveValue('stock viejo');
    const espera = pendiente<Response>();
    fetchFalso.mockReturnValueOnce(espera.promesa);

    fireEvent.change(screen.getByTestId('configuracion-campo-pestana-inventario'), {
      target: { value: 'Stock 2026' },
    });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-sheet'));

    expect(screen.getByRole('status')).toHaveTextContent('Trayendo las cartas…');
    expect(ultimoCuerpo()).toEqual({
      sheet: CONFIGURACION.sheet!.link,
      pestanaInventario: 'Stock 2026',
      traerCartasDe: 'stock viejo',
    });

    await act(async () =>
      espera.resolver(
        respuesta({
          ...CONFIGURACION.sheet,
          pestanaInventario: 'Stock 2026',
          aviso: null,
          pestanaCreada: true,
          cartasTraidas: { origen: 'stock viejo', cantidad: 128, hayColumnasExtra: true },
        }),
      ),
    );

    expect(screen.getByTestId('configuracion-mensaje-exito-sheet')).toHaveTextContent(
      'Se creó la pestaña «Stock 2026» con 128 cartas de «stock viejo». Las columnas que la app no usa quedaron al final.',
    );
    await waitFor(() => expect(listarFalso).toHaveBeenCalledTimes(2));
  });

  it('con una sola carta dice "1 carta" y sin columnas extra no suma la segunda frase', async () => {
    listarFalso.mockImplementation(async () => respuesta(CON_STOCK_VIEJO));
    await abrirPantalla();
    fetchFalso.mockResolvedValueOnce(
      respuesta({
        pestanaInventario: 'Stock 2026',
        pestanaCreada: true,
        cartasTraidas: { origen: 'stock viejo', cantidad: 1, hayColumnasExtra: false },
      }),
    );

    fireEvent.change(screen.getByTestId('configuracion-campo-pestana-inventario'), {
      target: { value: 'Stock 2026' },
    });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-sheet'));

    expect(await screen.findByTestId('configuracion-mensaje-exito-sheet')).toHaveTextContent(
      /^Se creó la pestaña «Stock 2026» con 1 carta de «stock viejo»\.$/,
    );
  });

  it('con "No traer cartas" no manda el origen y avisa que se creó la pestaña', async () => {
    await abrirPantalla();
    const espera = pendiente<Response>();
    fetchFalso.mockReturnValueOnce(espera.promesa);

    fireEvent.change(screen.getByTestId('configuracion-campo-pestana-inventario'), {
      target: { value: 'Stock 2026' },
    });
    fireEvent.change(selectorTraer(), { target: { value: '' } });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-sheet'));

    expect(screen.getByRole('status')).toHaveTextContent('Sincronizando con el Sheet…');
    expect(ultimoCuerpo()).toEqual({
      sheet: CONFIGURACION.sheet!.link,
      pestanaInventario: 'Stock 2026',
    });
    await act(async () =>
      espera.resolver(
        respuesta({
          pestanaInventario: 'Stock 2026',
          aviso: 'Se creó la pestaña «Stock 2026».',
          pestanaCreada: true,
        }),
      ),
    );

    expect(screen.getByTestId('configuracion-mensaje-aviso-sheet')).toHaveTextContent(
      'Se creó la pestaña «Stock 2026».',
    );
  });

  it('sin Sheet: las listas quedan deshabilitadas y, al salir de un link válido, lista ese Sheet', async () => {
    await abrirPantalla(SIN_SHEET);

    expect(listarFalso).not.toHaveBeenCalled();
    expect(selectorTraer()).toBeDisabled();
    expect(opciones(selectorTraer())).toEqual(['Sin Sheet']);
    expect(screen.getByTestId('configuracion-texto-ayuda-traer-cartas-de')).toHaveTextContent(
      'Disponible cuando se escribe el link del Sheet.',
    );
    expect(selectorRenombrar()).toBeDisabled();
    expect(opciones(selectorRenombrar())).toEqual(['Sin Sheet guardado']);
    expect(screen.getByText('Disponible cuando haya un Sheet guardado.')).toBeInTheDocument();
    expect(screen.getByTestId('configuracion-campo-nombre-nuevo')).toBeDisabled();
    expect(screen.getByTestId('configuracion-boton-renombrar-pestana')).toBeDisabled();

    const campoLink = screen.getByTestId('configuracion-campo-sheet');
    fireEvent.change(campoLink, { target: { value: 'no es un link' } });
    fireEvent.blur(campoLink);
    expect(listarFalso).not.toHaveBeenCalled();

    const espera = pendiente<Response>();
    listarFalso.mockReturnValueOnce(espera.promesa);
    fireEvent.change(campoLink, {
      target: { value: '1PruebaSheetConStockViejo00000000000000000' },
    });
    fireEvent.blur(campoLink);

    expect(listarFalso.mock.calls[0][0]).toBe(
      `${RUTA_PESTANAS}?idOLink=1PruebaSheetConStockViejo00000000000000000`,
    );
    expect(selectorTraer()).toBeDisabled();
    expect(opciones(selectorTraer())).toEqual(['Cargando pestañas…']);

    await act(async () => espera.resolver(respuesta(CON_STOCK_VIEJO)));
    expect(selectorTraer()).toBeEnabled();
    expect(selectorTraer()).toHaveValue('stock viejo');
    // La lista de renombrar sigue trabajando sobre el Sheet guardado (no hay).
    expect(selectorRenombrar()).toBeDisabled();

    // Salir otra vez del mismo link no vuelve a listar.
    fireEvent.blur(campoLink);
    expect(listarFalso).toHaveBeenCalledTimes(1);
  });

  it('si ninguna pestaña es reconocida, queda en "No traer cartas" y lo dice la ayuda', async () => {
    listarFalso.mockImplementation(async () =>
      respuesta({
        sheetGuardado: true,
        pestanas: [{ nombre: 'Ventas', enUso: false, sugerida: false, fija: false }],
      }),
    );
    await abrirPantalla();

    expect(selectorTraer()).toBeEnabled();
    expect(selectorTraer()).toHaveValue('');
    expect(opciones(selectorTraer())).toEqual(['No traer cartas', 'Ventas']);
    expect(screen.getByTestId('configuracion-texto-ayuda-traer-cartas-de')).toHaveTextContent(
      'Ninguna pestaña tiene encabezados de inventario.',
    );
  });

  it('si falla al listar, las dos listas muestran el error con "Reintentar" y Guardar sigue funcionando', async () => {
    listarFalso.mockImplementation(async () => problema(503, 'No hay conexión a internet.'));
    fetchFalso.mockResolvedValueOnce(respuesta(CONFIGURACION));
    render(<ConfiguracionPagina />);

    expect(
      await screen.findByTestId('configuracion-mensaje-error-traer-cartas-de'),
    ).toHaveTextContent('No se pudieron cargar las pestañas del Sheet. Reintentar');
    expect(screen.getByTestId('configuracion-mensaje-error-pestana-a-renombrar')).toHaveTextContent(
      'No se pudieron cargar las pestañas del Sheet. Reintentar',
    );
    expect(selectorTraer()).toBeDisabled();
    expect(opciones(selectorTraer())).toEqual(['No traer cartas']);
    expect(selectorRenombrar()).toBeDisabled();
    expect(opciones(selectorRenombrar())).toEqual(['Elegir una pestaña']);
    expect(screen.getByTestId('configuracion-boton-renombrar-pestana')).toBeDisabled();

    fetchFalso.mockResolvedValueOnce(
      respuesta({ pestanaInventario: 'Inventario', pestanaCreada: false }),
    );
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-sheet'));
    expect(await screen.findByTestId('configuracion-mensaje-exito-sheet')).toHaveTextContent(
      'Inventario guardado.',
    );
    expect(ultimoCuerpo()).toEqual({
      sheet: CONFIGURACION.sheet!.link,
      pestanaInventario: 'Inventario',
    });

    // Después de guardar se volvió a listar (y volvió a fallar); ahora "Reintentar" funciona.
    const reintentar = await screen.findByTestId('configuracion-boton-reintentar-pestanas-origen');
    listarFalso.mockImplementation(async () => respuesta(PESTANAS));
    fireEvent.click(reintentar);
    await waitFor(() => expect(selectorTraer()).toBeEnabled());
    expect(
      screen.queryByTestId('configuracion-mensaje-error-traer-cartas-de'),
    ).not.toBeInTheDocument();
  });

  it('si la pestaña de origen ya no existe, muestra el error junto a la lista y la recarga', async () => {
    listarFalso.mockImplementation(async () => respuesta(CON_STOCK_VIEJO));
    await abrirPantalla();
    const texto = 'La pestaña de origen ya no existe en el Sheet. Se actualizó la lista.';
    fetchFalso.mockResolvedValueOnce(
      problema(400, 'Hay datos para corregir.', {
        errores: { traerCartasDe: texto },
        campo: 'traer-cartas-de',
        codigo: 'ORIGEN_INEXISTENTE',
      }),
    );

    fireEvent.change(screen.getByTestId('configuracion-campo-pestana-inventario'), {
      target: { value: 'Stock 2026' },
    });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-sheet'));

    expect(
      await screen.findByTestId('configuracion-mensaje-error-traer-cartas-de'),
    ).toHaveTextContent(texto);
    expect(screen.queryByTestId('configuracion-mensaje-error-sheet')).not.toBeInTheDocument();
    await waitFor(() => expect(listarFalso).toHaveBeenCalledTimes(2));
  });
});

describe('ConfiguracionPagina · Inventario: renombrar una pestaña (HU-007 CA5 a CA8)', () => {
  /** Elige una pestaña, escribe el nombre nuevo y aprieta "Renombrar pestaña". */
  function renombrar(actual: string, nuevo: string) {
    fireEvent.change(selectorRenombrar(), { target: { value: actual } });
    fireEvent.change(screen.getByTestId('configuracion-campo-nombre-nuevo'), {
      target: { value: nuevo },
    });
    fireEvent.click(screen.getByTestId('configuracion-boton-renombrar-pestana'));
  }

  it('la lista marca la pestaña en uso y deshabilita las fijas (Caja, Movimientos)', async () => {
    await abrirPantalla();

    expect(opciones(selectorRenombrar())).toEqual([
      'Elegir una pestaña',
      'Inventario · en uso',
      'stock viejo',
      'Hoja 1',
      'Caja',
      'Movimientos',
    ]);
    const lista = within(selectorRenombrar());
    expect(lista.getByRole('option', { name: 'Caja' })).toBeDisabled();
    expect(lista.getByRole('option', { name: 'Movimientos' })).toBeDisabled();
    expect(lista.getByRole('option', { name: 'Hoja 1' })).toBeEnabled();
    expect(screen.getByText('Pestañas del Sheet guardado.')).toBeInTheDocument();
  });

  it('renombra una pestaña común: capa "Renombrando la pestaña…", éxito, nombre limpio y lista nueva', async () => {
    await abrirPantalla();
    const espera = pendiente<Response>();
    fetchFalso.mockReturnValueOnce(espera.promesa);

    renombrar('Hoja 1', 'Stock 2026');

    expect(screen.getByRole('status')).toHaveTextContent('Renombrando la pestaña…');
    expect(fetchFalso.mock.calls.at(-1)![0]).toBe(`${RUTA_PESTANAS}/renombrar`);
    expect(fetchFalso.mock.calls.at(-1)![1]?.method).toBe('PUT');
    expect(ultimoCuerpo()).toEqual({ nombreActual: 'Hoja 1', nombreNuevo: 'Stock 2026' });

    await act(async () =>
      espera.resolver(
        respuesta({
          nombreAnterior: 'Hoja 1',
          nombreNuevo: 'Stock 2026',
          eraPestanaInventario: false,
          nombrePestanaInventario: 'Inventario',
          pestanas: [
            { nombre: 'Inventario', enUso: true, sugerida: true, fija: false },
            { nombre: 'Stock 2026', enUso: false, sugerida: false, fija: false },
          ],
        }),
      ),
    );

    const exito = screen.getByTestId('configuracion-mensaje-exito-renombrar');
    expect(exito).toHaveTextContent('La pestaña «Hoja 1» se renombró a «Stock 2026».');
    expect(exito).toHaveAttribute('role', 'status');
    expect(screen.getByTestId('configuracion-campo-nombre-nuevo')).toHaveValue('');
    expect(selectorRenombrar()).toHaveValue('');
    expect(opciones(selectorRenombrar())).toEqual([
      'Elegir una pestaña',
      'Inventario · en uso',
      'Stock 2026',
    ]);
    expect(screen.getByTestId('configuracion-campo-pestana-inventario')).toHaveValue('Inventario');
    expect(listarFalso).toHaveBeenCalledTimes(1);
  });

  it('renombrar la del inventario actualiza el campo, en verde, sin dejarlo como cambio sin guardar', async () => {
    fetchFalso.mockResolvedValueOnce(respuesta(CONFIGURACION));
    render(
      <>
        <a href="/app/inventario" data-testid="app-enlace-inventario">
          Inventario
        </a>
        <ConfiguracionPagina />
      </>,
    );
    await waitFor(() => expect(selectorRenombrar()).toBeEnabled());
    fetchFalso.mockResolvedValueOnce(
      respuesta({
        nombreAnterior: 'Inventario',
        nombreNuevo: 'Stock 2026',
        eraPestanaInventario: true,
        nombrePestanaInventario: 'Stock 2026',
        pestanas: [{ nombre: 'Stock 2026', enUso: true, sugerida: true, fija: false }],
      }),
    );

    renombrar('Inventario', 'Stock 2026');

    expect(await screen.findByTestId('configuracion-mensaje-exito-renombrar')).toHaveTextContent(
      'La pestaña «Inventario» se renombró a «Stock 2026». También se actualizó la pestaña del inventario.',
    );
    expect(screen.getByTestId('configuracion-campo-pestana-inventario')).toHaveValue('Stock 2026');

    const frenar = (evento: MouseEvent) => evento.preventDefault();
    document.addEventListener('click', frenar);
    fireEvent.click(screen.getByTestId('app-enlace-inventario'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    document.removeEventListener('click', frenar);
  });

  it('sin elegir pestaña ni nombre marca los dos, con el foco en la lista, y no llama a la API', async () => {
    await abrirPantalla();
    const llamadas = fetchFalso.mock.calls.length;

    fireEvent.click(screen.getByTestId('configuracion-boton-renombrar-pestana'));

    expect(screen.getByTestId('configuracion-mensaje-error-pestana-a-renombrar')).toHaveTextContent(
      'La pestaña a renombrar es obligatoria.',
    );
    expect(screen.getByTestId('configuracion-mensaje-error-nombre-nuevo')).toHaveTextContent(
      'El nombre nuevo es obligatorio.',
    );
    expect(selectorRenombrar()).toHaveFocus();
    expect(selectorRenombrar()).toHaveAttribute('aria-invalid', 'true');
    expect(fetchFalso.mock.calls.length).toBe(llamadas);
  });

  it.each([
    [
      'más de 100 caracteres',
      'Hoja 1',
      'x'.repeat(101),
      'El nombre no debe superar los 100 caracteres.',
    ],
    ['igual al actual', 'Hoja 1', ' Hoja 1 ', 'El nombre nuevo es igual al actual.'],
    [
      'repetido sin distinguir mayúsculas',
      'Hoja 1',
      'inventario',
      'Ya existe una pestaña con ese nombre.',
    ],
  ])(
    'con un nombre %s marca el nombre nuevo y no llama a la API',
    async (_caso, actual, nuevo, mensaje) => {
      await abrirPantalla();
      const llamadas = fetchFalso.mock.calls.length;

      renombrar(actual, nuevo);

      expect(screen.getByTestId('configuracion-mensaje-error-nombre-nuevo')).toHaveTextContent(
        mensaje,
      );
      expect(screen.getByTestId('configuracion-campo-nombre-nuevo')).toHaveFocus();
      expect(fetchFalso.mock.calls.length).toBe(llamadas);
    },
  );

  it('cambiar solo mayúsculas sí se permite', async () => {
    await abrirPantalla();
    fetchFalso.mockResolvedValueOnce(
      respuesta({
        nombreAnterior: 'Hoja 1',
        nombreNuevo: 'HOJA 1',
        eraPestanaInventario: false,
        pestanas: [],
      }),
    );

    renombrar('Hoja 1', 'HOJA 1');

    expect(await screen.findByTestId('configuracion-mensaje-exito-renombrar')).toHaveTextContent(
      'La pestaña «Hoja 1» se renombró a «HOJA 1».',
    );
  });

  it('el error de la API va junto a su campo (nombre repetido en el Sheet)', async () => {
    await abrirPantalla();
    fetchFalso.mockResolvedValueOnce(
      problema(400, 'Hay datos para corregir.', {
        errores: { nombreNuevo: 'Ya existe una pestaña con ese nombre.' },
        campo: 'nombre-nuevo',
        codigo: 'NOMBRE_REPETIDO',
      }),
    );

    renombrar('Hoja 1', 'Otra');

    expect(await screen.findByTestId('configuracion-mensaje-error-nombre-nuevo')).toHaveTextContent(
      'Ya existe una pestaña con ese nombre.',
    );
    expect(screen.queryByTestId('configuracion-mensaje-error-renombrar')).not.toBeInTheDocument();
  });

  it('si la pestaña fue borrada en el Sheet, muestra el error de la fila y recarga la lista', async () => {
    await abrirPantalla();
    const texto = 'La pestaña ya no existe en el Sheet. Se actualizó la lista.';
    fetchFalso.mockResolvedValueOnce(
      problema(404, texto, { campo: null, codigo: 'PESTANA_INEXISTENTE' }),
    );

    renombrar('Hoja 1', 'Stock 2026');

    const error = await screen.findByTestId('configuracion-mensaje-error-renombrar');
    expect(error).toHaveTextContent(texto);
    expect(error).toHaveAttribute('role', 'alert');
    await waitFor(() => expect(listarFalso).toHaveBeenCalledTimes(2));
    expect(listarFalso.mock.calls[1][0]).toBe(RUTA_PESTANAS);
  });

  it('si falla al listar, "Reintentar" vuelve a pedir la lista', async () => {
    listarFalso.mockImplementationOnce(async () => problema(500, 'Error'));
    fetchFalso.mockResolvedValueOnce(respuesta(CONFIGURACION));
    render(<ConfiguracionPagina />);

    fireEvent.click(await screen.findByTestId('configuracion-boton-reintentar-pestanas'));

    await waitFor(() => expect(selectorRenombrar()).toBeEnabled());
    expect(
      screen.queryByTestId('configuracion-mensaje-error-pestana-a-renombrar'),
    ).not.toBeInTheDocument();
  });

  it('con el Sheet sin pestañas, la lista queda deshabilitada y lo dice', async () => {
    listarFalso.mockImplementation(async () => respuesta({ sheetGuardado: true, pestanas: [] }));
    fetchFalso.mockResolvedValueOnce(respuesta(CONFIGURACION));
    render(<ConfiguracionPagina />);

    await waitFor(() =>
      expect(opciones(selectorRenombrar())).toEqual(['El Sheet no tiene pestañas.']),
    );
    expect(selectorRenombrar()).toBeDisabled();
    expect(screen.getByTestId('configuracion-boton-renombrar-pestana')).toBeDisabled();
  });
});

describe('ConfiguracionPagina · bloque Regla de precio (CA6)', () => {
  it('guarda los precios: "Guardando…" en el botón y después "Precios guardados. Se recalculan."', async () => {
    await abrirPantalla();
    const espera = pendiente<Response>();
    fetchFalso.mockReturnValueOnce(espera.promesa);

    fireEvent.change(screen.getByTestId('configuracion-campo-cotizacion'), {
      target: { value: '1350,5' },
    });
    fireEvent.change(screen.getByTestId('configuracion-campo-carpeta-cache'), {
      target: { value: '  D:/TCG/cache ' },
    });
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
        respuesta({
          ...CONFIGURACION.precios,
          cotizacion: 1350.5,
          carpetaCache: 'D:/TCG/cache',
          aviso: null,
        }),
      ),
    );

    expect(screen.getByTestId('configuracion-mensaje-exito-precios')).toHaveTextContent(
      'Precios guardados. Se recalculan.',
    );
    expect(boton).toBeEnabled();
  });

  it('con un bulk con centavos marca ese campo, con el error de bloque, y no llama a la API', async () => {
    await abrirPantalla();
    fireEvent.change(screen.getByTestId('configuracion-campo-bulk-rare'), {
      target: { value: '300,50' },
    });
    fireEvent.change(screen.getByTestId('configuracion-campo-cotizacion'), {
      target: { value: '' },
    });
    fireEvent.change(screen.getByTestId('configuracion-campo-bulk-mythic'), {
      target: { value: '0' },
    });

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-precios'));

    expect(screen.getByTestId('configuracion-mensaje-error-bulk-rare')).toHaveTextContent(
      'El precio debe ser un número entero, sin centavos.',
    );
    expect(screen.getByTestId('configuracion-mensaje-error-cotizacion')).toHaveTextContent(
      'La cotización del dólar es obligatoria.',
    );
    expect(screen.getByTestId('configuracion-mensaje-error-bulk-mythic')).toHaveTextContent(
      'Debe ser mayor a 0.',
    );
    expect(screen.getByTestId('configuracion-mensaje-error-precios')).toHaveTextContent(
      'No se guardó. Hay un campo con error, marcado en rojo.',
    );
    expect(screen.getByTestId('configuracion-campo-cotizacion')).toHaveFocus();
    expect(fetchFalso).toHaveBeenCalledTimes(1);
  });

  it('el error de la API va debajo de su campo (carpeta que no existe)', async () => {
    await abrirPantalla();
    fetchFalso.mockResolvedValueOnce(
      problema(400, 'Hay datos para corregir.', {
        errores: {
          carpetaCache: 'La carpeta no existe. Se puede indicar otra o dejar el campo vacío.',
          'bulk.rare': 'El precio debe ser un número entero, sin centavos.',
        },
      }),
    );
    fireEvent.change(screen.getByTestId('configuracion-campo-carpeta-cache'), {
      target: { value: 'Z:/no-existe' },
    });

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-precios'));

    expect(
      await screen.findByTestId('configuracion-mensaje-error-carpeta-cache'),
    ).toHaveTextContent('La carpeta no existe. Se puede indicar otra o dejar el campo vacío.');
    expect(screen.getByTestId('configuracion-mensaje-error-bulk-rare')).toHaveTextContent(
      'El precio debe ser un número entero, sin centavos.',
    );
    expect(screen.getByTestId('configuracion-mensaje-error-precios')).toHaveTextContent(
      'No se guardó. Hay un campo con error, marcado en rojo.',
    );
    expect(screen.getByTestId('configuracion-campo-carpeta-cache')).toHaveFocus();
  });

  it('si falla Card Kingdom muestra el aviso en lugar del éxito', async () => {
    await abrirPantalla();
    const aviso =
      'Guardado, pero no se pudo sincronizar con Card Kingdom. Se puede reintentar más tarde.';
    fetchFalso.mockResolvedValueOnce(respuesta({ ...CONFIGURACION.precios, aviso }));

    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-precios'));

    expect(await screen.findByTestId('configuracion-mensaje-aviso-precios')).toHaveTextContent(
      aviso,
    );
    expect(screen.queryByTestId('configuracion-mensaje-exito-precios')).not.toBeInTheDocument();
  });
});

describe('ConfiguracionPagina · bloque Puntos (CA6)', () => {
  it('el ejemplo se recalcula al escribir y al guardar 500 muestra "Puntos guardados."', async () => {
    await abrirPantalla();
    const campo = screen.getByTestId('configuracion-campo-pesos-por-punto');

    fireEvent.change(campo, { target: { value: '' } });
    expect(screen.getByTestId('configuracion-texto-ejemplo-puntos')).toHaveTextContent(
      'Falta indicar cuántos pesos vale un punto.',
    );
    fireEvent.change(campo, { target: { value: '500' } });
    expect(screen.getByTestId('configuracion-texto-ejemplo-puntos')).toHaveTextContent(
      'Una compra de $25.000 suma 50 puntos.',
    );

    fetchFalso.mockResolvedValueOnce(respuesta({ pesosPorPunto: 500 }));
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-puntos'));

    expect(await screen.findByTestId('configuracion-mensaje-exito-puntos')).toHaveTextContent(
      'Puntos guardados.',
    );
    expect(fetchFalso.mock.calls.at(-1)![0]).toBe('/api/v1/configuracion/puntos');
    expect(ultimoCuerpo()).toEqual({ pesosPorPunto: 500 });
  });

  it('vacío o en 0 marca el campo y no llama a la API', async () => {
    await abrirPantalla();
    const campo = screen.getByTestId('configuracion-campo-pesos-por-punto');

    fireEvent.change(campo, { target: { value: ' ' } });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-puntos'));
    expect(screen.getByTestId('configuracion-mensaje-error-pesos-por-punto')).toHaveTextContent(
      'Los pesos por punto son obligatorios.',
    );
    expect(campo).toHaveFocus();

    fireEvent.change(campo, { target: { value: '0' } });
    fireEvent.click(screen.getByTestId('configuracion-boton-guardar-puntos'));
    expect(screen.getByTestId('configuracion-mensaje-error-pesos-por-punto')).toHaveTextContent(
      'Debe ser mayor a 0.',
    );
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
    fireEvent.change(screen.getByTestId('configuracion-campo-pesos-por-punto'), {
      target: { value: '750' },
    });

    fireEvent.click(screen.getByTestId('app-enlace-inventario'));

    const modal = screen.getByRole('dialog', { name: 'Hay cambios sin guardar' });
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
    fireEvent.change(screen.getByTestId('configuracion-campo-pesos-por-punto'), {
      target: { value: '500' },
    });
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
    fireEvent.change(screen.getByTestId('configuracion-campo-nombre-tienda'), {
      target: { value: 'Otra' },
    });

    fireEvent.click(screen.getByTestId('app-enlace-inventario'));
    expect(navegaciones).toEqual([]);
    fireEvent.click(screen.getByTestId('configuracion-boton-salir-igual'));

    expect(navegaciones).toEqual(['/app/inventario']);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    quitar();
  });
});
