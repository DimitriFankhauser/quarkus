package io.quarkus.it.exampleendpoint;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkus.security.identity.SecurityIdentity;

@Path("/testdimitri")
public class ExampleEndpoint {
    @Inject
    SecurityIdentity identity;

    @GET
    @Path("test")
    public String listProviders() {
        return "test succeeded";
    }

}
