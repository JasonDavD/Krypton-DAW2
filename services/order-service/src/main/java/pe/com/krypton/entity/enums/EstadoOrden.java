package pe.com.krypton.entity.enums;

/** Estados del ciclo de vida de una orden. Persistido como STRING (nunca ORDINAL). */
public enum EstadoOrden {
    PENDIENTE,
    CONFIRMADA,
    ENVIADO,
    ENTREGADO,
    CANCELADA
}
