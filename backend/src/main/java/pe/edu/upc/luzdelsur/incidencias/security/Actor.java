package pe.edu.upc.luzdelsur.incidencias.security;

public record Actor(String id, ActorRole role) {

    public static final String ANONYMOUS_ID = "anonimo";

    public boolean isAnonymous() {
        return id == null || ANONYMOUS_ID.equals(id);
    }

    public boolean hasAnyRole(ActorRole... roles) {
        for (ActorRole role : roles) {
            if (this.role == role) {
                return true;
            }
        }
        return false;
    }
}
