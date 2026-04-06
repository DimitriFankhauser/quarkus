package io.quarkus.it.exampleendpoint;

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
    RsaUtil rsaUtil;

    @POST
    @Path("/encryptRSA")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response encrypt(EncryptableMessage encryptableMessage) {
        Log.info("Encrypt request: " + encryptableMessage.message);
        try {
            String ciphertext = rsaUtil.encrypt(encryptableMessage.message);
            Log.info("Ciphertext: " + ciphertext);
            return Response.ok(ciphertext).build();
        } catch (Exception e) {
            Log.error(e);
            return Response.serverError().build();
        }
    }

    @POST
    @Path("/decryptRSA")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response decrypt(EncryptableMessage ciphertext) {
        Log.info("Decrypt request, ciphertext: " + ciphertext.message);
        try {
            String message = rsaUtil.decrypt(ciphertext.message);
            Log.info("Plaintext: " + message);
            return Response.ok(message).build();
        } catch (Exception e) {
            Log.error(e);
            return Response.serverError().build();
        }
    }
}
