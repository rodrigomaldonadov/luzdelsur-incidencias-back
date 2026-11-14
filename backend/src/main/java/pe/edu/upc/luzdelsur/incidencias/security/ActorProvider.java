package pe.edu.upc.luzdelsur.incidencias.security;

/**
 * Punto de extension para la autenticacion.
 *
 * <p>Persona 3 reemplaza {@link HeaderActorProvider} por un proveedor basado en
 * el JWT (claims sub y rol) sin cambiar los controladores ni los servicios.</p>
 */
public interface ActorProvider {

    Actor current();
}
