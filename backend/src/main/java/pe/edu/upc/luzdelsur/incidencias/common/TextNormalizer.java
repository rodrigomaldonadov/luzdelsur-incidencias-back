package pe.edu.upc.luzdelsur.incidencias.common;

/**
 * Normaliza textos para comparar sectores sin falsos negativos por tildes,
 * mayusculas o espacios repetidos.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String withoutAccents = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String cleaned = withoutAccents
                .toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Valores que en el prototipo se consideran ubicacion incompleta (caso C5).
     */
    public static boolean isAmbiguousSector(String sector) {
        String key = normalizeKey(sector);
        if (key == null) {
            return true;
        }
        return switch (key) {
            case "SIN SECTOR", "S N", "SN", "NO INFORMADO", "NO ESPECIFICADO",
                 "DESCONOCIDO", "NO APLICA", "N A", "NA", "OTRO", "OTROS" -> true;
            default -> false;
        };
    }
}
