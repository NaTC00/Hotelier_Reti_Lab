package Server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ServerMain {
    public static void main(String[] args) {

        // recupero i parametri di configurazione del server dal file di configurazione
        File config = new File(System.getProperty("user.dir"), "config_server.properties");

        FileInputStream fis = null;

        Properties prop = new Properties();

        try {
            fis = new FileInputStream(config);
            prop.load(fis);

            int coreCount = Runtime.getRuntime().availableProcessors();
            System.out.println("numero di core disponibili sulla macchina : " + coreCount);

            HotelierServer server = new HotelierServer(
                    Executors.newFixedThreadPool(coreCount),
                    Integer.parseInt(prop.getProperty("registry_port")),
                    Integer.parseInt(prop.getProperty("registr_obj")),
                    Integer.parseInt(prop.getProperty("notific_obj")),
                    Integer.parseInt(prop.getProperty("server_port_tcp")),
                    null,
                    null,
                    null,
                    prop.getProperty("multicast_address"),
                    null,
                    Integer.parseInt(prop.getProperty("multicast_port")),
                    Integer.parseInt(prop.getProperty("P_number")),
                    Integer.parseInt(prop.getProperty("G_generator")),
                    null,
                    null,
                    null,
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>(),
                    new ConcurrentHashMap<>(),
                    new ConcurrentHashMap<>(),
                    new ConcurrentHashMap<>(),
                    prop.getProperty("filePathUser"),
                    prop.getProperty("filePathHotel"),
                    prop.getProperty("filePathReviews"),
                    Long.parseLong(prop.getProperty("timeoutUser")),
                    Long.parseLong(prop.getProperty("timeoutHotel")),
                    Long.parseLong(prop.getProperty("timeouReviews")),
                    Long.parseLong(prop.getProperty("timeoutRanking")),
                    new ReentrantReadWriteLock(),
                    new ReentrantReadWriteLock(),
                    new ReentrantReadWriteLock(),
                    new ReentrantReadWriteLock(),
                    new ReentrantReadWriteLock(),
                    null,
                    null,
                    null,
                    null
                  );

            server.loadDataFromDisk();
            server.exportRemoteServices();
            server.startFileWriterThreads();
            server.startCalculateRanking();
            server.startServer();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }




    }
}
