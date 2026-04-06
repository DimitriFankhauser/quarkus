package io.quarkus.it.exampleendpoint;

public class EncryptableMessage {

    public int id;
    public String message;

    public EncryptableMessage() {
    }

    public EncryptableMessage(int id, String message) {
        this.id = id;
        this.message = message;
    }
}
