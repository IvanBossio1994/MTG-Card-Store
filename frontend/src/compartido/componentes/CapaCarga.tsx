/**
 * Capa de carga: tapa la pantalla con blur y muestra el gatito (180 px) en una tarjeta de 420 px,
 * con un texto que dice qué está pasando. Es para lo que tarda (cargar, hablar con Google).
 * Ejemplo: `{guardando && <CapaCarga texto={textos['general.carga.sincronizando-sheet']} />}`
 */
export function CapaCarga({ texto }: { texto: string }) {
  return (
    <div
      role="status"
      data-testid="app-capa-carga"
      className="fixed inset-0 z-40 flex items-center justify-center bg-[rgba(250,247,242,0.55)] p-6 backdrop-blur-[6px]"
    >
      <div className="w-full max-w-[420px] animate-entra rounded-2xl border border-borde bg-white p-6 text-center shadow-flotante">
        <img className="mx-auto h-auto w-[180px]" src="/images/cat-card-loader.gif" alt="" />
        <h2 className="mt-2 flex justify-center text-[18px] font-semibold tracking-[-0.01em]">{texto}</h2>
      </div>
    </div>
  );
}
