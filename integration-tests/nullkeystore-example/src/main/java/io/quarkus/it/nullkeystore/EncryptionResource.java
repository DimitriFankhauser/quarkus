package io.quarkus.it.nullkeystore;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkus.logging.Log;

@Path("/crypto")
public class EncryptionResource {

    @Inject
    HsmCryptoService cryptoService;

    @POST
    @Path("/encrypt")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response encrypt(CryptoMessage message) {
        Log.info("Encrypt request: " + message.message);
        try {
            String ciphertext = cryptoService.encrypt(message.message);
            Log.info("Ciphertext: " + ciphertext);
            return Response.ok(ciphertext).build();
        } catch (Exception e) {
            Log.error(e);
            return Response.serverError().build();
        }
    }

    @POST
    @Path("/decrypt")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response decrypt(CryptoMessage ciphertext) {
        Log.info("Decrypt request, ciphertext: " + ciphertext.message);
        try {
            String message = cryptoService.decrypt(ciphertext.message);
            Log.info("Plaintext: " + message);
            return Response.ok(message).build();
        } catch (Exception e) {
            Log.error(e);
            return Response.serverError().build();
        }
    }
}
