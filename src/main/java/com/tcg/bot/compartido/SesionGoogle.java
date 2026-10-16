package com.tcg.bot.compartido;

import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/**
 * Sesión de Google del Gmail con el que el comercio entró a la app (OAuth).
 * Implementación real: {@code google.GoogleOAuthService}; en modo prueba la arma {@link ModoPruebaConfig}.
 */
@FunctionalInterface
public interface SesionGoogle {

    /** Dónde se vuelve a iniciar sesión con Google (pantalla de la v1, mientras no exista la de la v2). */
    String URL_INICIO_SESION = "/configuracion/google/oauth/iniciar";

    /**
     * Verifica que la sesión siga vigente y devuelve el Gmail conectado, si Google lo informa.
     * Ejemplo: sesión vigente → {@code Optional.of("tienda@gmail.com")}; Google responde {@code invalid_grant} → lanza {@link #sesionVencida()} (401).
     */
    Optional<String> gmailConectado();

    /**
     * Error 401: la sesión de Google venció o no hay sesión iniciada.
     * Ejemplo: {@code throw SesionGoogle.sesionVencida()} → 401 "La sesión de Google venció." con {@code accion: "volver-a-iniciar-sesion"}.
     */
    static ErrorDeNegocioException sesionVencida() {
        return new ErrorDeNegocioException(
                HttpStatus.UNAUTHORIZED,
                "Sesión de Google vencida",
                "La sesión de Google venció.",
                Map.of("accion", "volver-a-iniciar-sesion", "urlInicioSesion", URL_INICIO_SESION));
    }

    /**
     * Error 500: Windows no deja leer la carpeta donde se guarda la sesión (por ejemplo, quedó a nombre de otro usuario
     * porque la app se abrió como administrador). No es un problema de internet ni de la sesión.
     * Ejemplo: {@code throw SesionGoogle.sinPermisoEnCarpeta()} → 500 "La app no puede leer la sesión de Google en esta PC. …".
     */
    static ErrorDeNegocioException sinPermisoEnCarpeta() {
        return new ErrorDeNegocioException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Sesión de Google ilegible",
                "La app no puede leer la sesión de Google en esta PC. Abrirla sin permisos de administrador e iniciar sesión de nuevo.");
    }
}
