package pe.edu.upc.luzdelsur.incidencias.report;

public enum FailureType {

    CORTE_TOTAL(1.00),
    BAJA_TENSION(0.75),
    INTERMITENCIA(0.60),
    MEDIDOR(0.50),
    ALUMBRADO_PUBLICO(0.35),
    OTRO(0.40);

    private final double priorityWeight;

    FailureType(double priorityWeight) {
        this.priorityWeight = priorityWeight;
    }

    public double priorityWeight() {
        return priorityWeight;
    }
}
