package pe.com.krypton.exception;

/** Cuerpo JSON estandar para errores de la API. */
public record ApiError(int status, String error) {
}
