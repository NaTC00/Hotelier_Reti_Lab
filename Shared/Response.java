package Shared;// La classe Shared.Response rappresenta una risposta da inviare al client
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class Response {

    // Il codice di stato della risposta
    private int statusCode;

    // Il messaggio della risposta
    private Message message;

    public Response(int statusCode, Message message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public int getStatusCode() {
        return statusCode;
    }
    public Message getMessage() {
        return message;
    }

    // Metodo per scrivere una risposta sul canale
    public void writeResponse(SelectionKey key) {
        SocketChannel c_channel = (SocketChannel) key.channel();
        Gson gson = new Gson();
        String jsonResponse = gson.toJson(this);
        System.out.println("Messaggio che invia al client: " + jsonResponse);

        try {
            // Ottieni i byte della risposta in UTF-8
            byte[] jsonBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

            // Calcola la lunghezza della risposta in byte
            int messageLength = jsonBytes.length;

            // Invia la lunghezza del messaggio come intero (4 byte)
            ByteBuffer lengthBuffer = ByteBuffer.allocate(4).putInt(messageLength);
            lengthBuffer.flip();
            while (lengthBuffer.hasRemaining()) {
                c_channel.write(lengthBuffer);
            }

            // Invia il messaggio JSON
            ByteBuffer messageBuffer = ByteBuffer.wrap(jsonBytes);
            while (messageBuffer.hasRemaining()) {
                c_channel.write(messageBuffer);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // La classe Message rappresenta un messaggio da inviare come parte della risposta
    public  static class Message {

        // Il risultato del messaggio
        private Object result;

        // Il corpo del messaggio
        private String body;

        public Message(Object result, String body) {
            this.result = result;
            this.body = body;
        }

        public Object getResult() {
            return result;
        }

        public String getBody() {
            return body;
        }
    }



}

