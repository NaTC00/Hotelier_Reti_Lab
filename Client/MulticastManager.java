package Client;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;


public class MulticastManager implements Runnable {


    private String multicastAddress;


    private int port_multicast;

    private MulticastSocket multicastWelcome;

    private Thread thread;

    InetAddress welcomeGroup;


    public MulticastManager(String multicastAddress, int port_multicast) {

        this.multicastAddress = multicastAddress;

        this.port_multicast = port_multicast;



    }
    public void startMulticast() {
        joinMulticastGroup();
        thread = new Thread(this);
        thread.start();
    }


    // Metodo per interrompere il thread specifico
    public void stopMulticast() {
        if (thread != null) {
            thread.interrupt();
            leaveMulticastGroup();
        }
    }

    @SuppressWarnings("deprecation")
    private void joinMulticastGroup() {

        try {
            // Determina l'indirizzo IP del gruppo di multicast, dato il nome dell'host
            welcomeGroup = InetAddress.getByName(multicastAddress);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return;
        }

        // Verifica che l'indirizzo passato come argomento sia valido per il multicast
        if (!welcomeGroup.isMulticastAddress()) {
            throw new IllegalArgumentException("indirizzo non valido come indirizzo di multicast");
        }

        // Unirsi al gruppo di multicast
        try {
            // Costruisce un socket multicast e lo associa alla porta specificata sulla macchina locale
            multicastWelcome = new MulticastSocket(port_multicast);
            // Si unisce a un gruppo di multicast
            multicastWelcome.joinGroup(welcomeGroup);

            System.out.println("Unito al gruppo di multicast");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // @override, metodo attraverso cui il client sta in ascolto sul gruppo di
    // multicast per ricevere aggiornamenti sui cambiamenti di posizione del primo hotel classificato nei ranking locali
    @SuppressWarnings("deprecation")
    public void run() {
        while (!Thread.interrupted()) {
            try {

                byte[] buf = new byte[2048];

                DatagramPacket dp = new DatagramPacket(buf, buf.length);


                multicastWelcome.receive(dp);


                String str = new String(buf, StandardCharsets.UTF_8);


                if(!(str.trim().equals("EXIT_MULTICAST_GROUP"))){

                    System.out.println("Arrivato aggiornamento sulla prima posizione del ranking - ecco il primo hotel in classifica: " + str.trim());
                }



            } catch (SocketException e) {
                // Gestisce l'eccezione del socket chiuso
                System.err.println("MulticastSocket closed");
                break;
            }catch (IOException e) {
                // Gestisce le eccezioni di input/output
                e.printStackTrace();
                break;
            }
        }

        try {
            // Si dissocia dal gruppo di multicast
            multicastWelcome.leaveGroup(welcomeGroup);
            System.out.println("Dissociato dal gruppo di multicast");

            // Chiude il socket multicast
            multicastWelcome.close();
            System.out.println("Socket multicast chiuso");
        }catch (IOException ex){
            ex.printStackTrace();
        }


    }

    private void leaveMulticastGroup() {


        try (DatagramSocket sock = new DatagramSocket()){
            // Verifica se il socket multicast è stato inizializzato
            if (multicastWelcome != null && welcomeGroup != null) {
                // preparo il DatagramPacket da inviare

                byte[] data;
                String infoHotel = "EXIT_MULTICAST_GROUP";
                data = infoHotel.getBytes();

                DatagramPacket datagramPacket = new DatagramPacket(data, data.length, this.welcomeGroup, this.port_multicast);
                // invio il datagramma sul socket
                sock.send(datagramPacket);


            } else {
                System.err.println("Socket multicast o gruppo non inizializzati");
            }
        } catch (IOException e) {
            // Gestisce le eccezioni di input/output
            e.printStackTrace();
        }
    }



}

