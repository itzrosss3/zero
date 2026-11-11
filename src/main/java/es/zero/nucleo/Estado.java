package es.zero.nucleo;

/** En qué está Zero en cada momento. Un enum es un tipo con un conjunto cerrado de valores. */
public enum Estado {
    CARGANDO,
    CALIBRANDO,
    ESPERA,
    ESCUCHANDO,
    ENTENDIENDO,
    PENSANDO,
    HABLANDO,
    CONFIRMANDO,
    SILENCIADO,
    SIN_MICRO,
    ERROR
}
