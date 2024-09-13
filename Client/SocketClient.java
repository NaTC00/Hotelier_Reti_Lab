package Client;


import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class SocketClient {

    private SocketChannel client;
    SocketAddress socketAddress;


    public SocketClient(int port) throws IOException {
        socketAddress = new InetSocketAddress("127.0.0.1", port);
        client = SocketChannel.open(socketAddress);

        if (ClientMain.DEBUG) System.out.println("Connected to " + socketAddress);


    }





    public void closeSocket() throws IOException {
        if(client != null) {
            client.close();
            client = null;
            if (ClientMain.DEBUG) System.out.println("Client socket closed");

        }
    }

    //metodo per inviare una richiesta al server
    public void sendRequest(String request) throws IOException{

        if (client != null) {


            // Ottieni i byte della risposta in UTF-8
            byte[] jsonBytes = request.getBytes(StandardCharsets.UTF_8);

            // Calcola la lunghezza della risposta in byte
            int messageLength = jsonBytes.length;

            // Invia la lunghezza del messaggio come intero (4 byte)
            if (ClientMain.DEBUG) System.out.println("LUNGHEZZA DEL MSG CHE IL CLIENT MANDA: " + messageLength);

            ByteBuffer lengthBuffer = ByteBuffer.allocate(4).putInt(messageLength);
            lengthBuffer.flip();
            while (lengthBuffer.hasRemaining()) {
                client.write(lengthBuffer);
            }

            // Invia il messaggio JSON
            if (ClientMain.DEBUG) System.out.println("MSG CHE IL CLIENT MANDA: " + request);
            ByteBuffer messageBuffer = ByteBuffer.wrap(jsonBytes);
            while (messageBuffer.hasRemaining()) {
                client.write(messageBuffer);
            }

        }
    }


    //metodo per ricevere la risposta dal server
    public String readResponse() throws IOException {
        if (client != null ) {

            // Buffer per leggere la lunghezza del messaggio (4 byte)
            ByteBuffer lengthBuffer = ByteBuffer.allocate(Integer.BYTES);
            int bytesRead = 0;


            // Legge i 4 byte che rappresentano la lunghezza del messaggio
            while (bytesRead < Integer.BYTES) {

                int read = client.read(lengthBuffer);
                if (read == -1) {
                    throw new EOFException("Il server ha chiuso la connessione ");
                }
                bytesRead += read;
            }
            lengthBuffer.flip();
            int messageLength = lengthBuffer.getInt();

            // Buffer per leggere il messaggio di lunghezza specificata
            ByteBuffer messageBuffer = ByteBuffer.allocate(messageLength);
            bytesRead = 0;

            // Legge il messaggio completo
            while (bytesRead < messageLength) {

                int read = client.read(messageBuffer);
                if (read == -1) {
                    throw new EOFException("Il server ha chiuso la connessione ");
                }
                bytesRead += read;
            }
            messageBuffer.flip();

            // Decodifica il messaggio utilizzando UTF-8
            String receivedMessage = StandardCharsets.UTF_8.decode(messageBuffer).toString().trim();

            // Stampa di debug se abilitata
            if (ClientMain.DEBUG) {
                System.out.println("Messaggio ricevuto dal server: " + receivedMessage);
            }

            return receivedMessage;
        }
        return null;
    }



}
