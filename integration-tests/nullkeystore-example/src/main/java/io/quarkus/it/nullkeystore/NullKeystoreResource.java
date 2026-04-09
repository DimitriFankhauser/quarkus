package io.quarkus.it.nullkeystore;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/null-keystore")
public class NullKeystoreResource {

    @GET
    public String ping() {
        return "ok";
    }

}
