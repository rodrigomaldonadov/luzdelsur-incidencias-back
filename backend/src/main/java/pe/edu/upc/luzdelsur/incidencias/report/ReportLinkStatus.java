package pe.edu.upc.luzdelsur.incidencias.report;

public enum ReportLinkStatus {

    /** Vinculado automaticamente, pendiente de confirmacion del operador. */
    SUGERIDO,
    /** El operador confirmo la agrupacion. */
    CONFIRMADO,
    /** El operador separo el reporte; se conserva el registro original. */
    SEPARADO,
    /** El operador movio el reporte a otra incidencia. */
    REASIGNADO
}
