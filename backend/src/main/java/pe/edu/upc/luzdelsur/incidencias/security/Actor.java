package pe.edu.upc.luzdelsur.incidencias.security;

public record Actor(String id, ActorRole role) {

    public boolean hasAnyRole(ActorRole... roles) {
        for (ActorRole role : roles) {
            if (this.role == role) {
                return true;
            }
        }
        return false;
    }
}
