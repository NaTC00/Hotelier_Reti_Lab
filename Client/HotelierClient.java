package Client;

import Shared.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HotelierClient {



    //Indirizzo multicast per ricevere notifiche di aggiornamento.
    private String multicastAddress;

    //Porta per la connessione multicast.
    private int port_multicast;

    //Porta TCP del server a cui il client si connetterà.
    private int server_port_tcp;

    //Oggetto per gestire la comunicazione via socket con il server.
    private SocketClient socketClient;

    //Nome utente del client, utilizzato per l'autenticazione.
    private String username;

    // Numero molto grande utilizzato per il calcolo della chiave pubblica per la genereazione della chiave per la cifratura e decifratura  nello scambio di chiavi (Diffie-Hellman)
    private int P_number;

    // Generatore utilizzato per calcolare le chiavi intermedie per la genereazione della chiave per la cifratura e decifratura nello scambio di chiavi (Diffie-Hellman)
    private int g;

    //Chiave utilizzata per la cifratura e decifratura delle comunicazioni.
    private static String securityKey;

    //UUID univoco del client, associato alla chiave di cifratura/decifratura nella struttura dati del server.
    private String uuidclient;

    //Porta del registro RMI per la connessione ai servizi remoti.
    private int registryPort;

    //Porta per l'oggetto callback RMI per ricevere notifiche dal server.
    private int callbackObjPort;

    //Registro RMI per il lookup e il binding degli oggetti remoti.
    private Registry rmiRegistry;

    //Stub per l'interfaccia di notifica degli eventi, esportato come oggetto remoto.
    private NotifyEventInterface clientNotificationStub;

    // Oggetto che gestisce la notifica dei ranking locali, esportato tramite RMI
    private NotifyEventImpl notifyEventImpl;

    //Oggetto remoto per la registrazione e deregistrazione al servizio di notifica.
    private NotificationService notificationServer;

    //Oggetto remoto del servizio di registrazione di un nuovo utente.
    private RegistrationService registrationServer;

    //Gestore del multicast per ricevere aggiornamenti relativi agli hotel.
    private MulticastManager multicastManager;

    /**
     * Costruttore della classe HotelierClient.
     *
     * @param multicastAddress      Indirizzo multicast per ricevere notifiche di aggiornamento.
     * @param port_multicast        Porta per la connessione multicast.
     * @param registryPort          Porta del registro RMI per la connessione ai servizi remoti.
     * @param callbackObjPort       Porta per l'oggetto callback RMI per ricevere notifiche dal server.
     * @param server_port_tcp       Porta TCP del server a cui il client si connetterà.
     * @param P_number              Primo parametro della chiave pubblica Diffie-Hellman per la cifratura.
     * @param g                     Secondo parametro della chiave pubblica Diffie-Hellman per la cifratura.
     * @param username              Nome utente del client, utilizzato per l'autenticazione.
     * @param uuidclient            UUID univoco del client, associato alla chiave di decifratura nella struttura dati del server.
     * @param socketClient          Oggetto per gestire la comunicazione via socket con il server.
     * @param rmiRegistry           Registro RMI per il lookup e il binding degli oggetti remoti.
     * @param clientNotificationStub Stub per l'interfaccia di notifica degli eventi, esportato come oggetto remoto.
     * @param notifyEventImpl       Oggetto che gestisce la notifica dei ranking locali, esportato tramite RMI
     * @param notificationServer    Oggetto remoto del servizio di notifica.
     * @param registrationServer    Oggetto remoto del servizio di registrazione.
     * @param multicastManager      Gestore del multicast per ricevere aggiornamenti relativi agli hotel.
     */
    public HotelierClient(String multicastAddress, int port_multicast, int registryPort, int callbackObjPort,
                          int server_port_tcp, int P_number, int g,
                          String username, String uuidclient, SocketClient socketClient, Registry rmiRegistry,
                          NotifyEventInterface clientNotificationStub,
                          NotifyEventImpl notifyEventImpl,
                          NotificationService notificationServer, RegistrationService registrationServer,
                          MulticastManager multicastManager) {
        this.multicastAddress = multicastAddress;
        this.port_multicast = port_multicast;
        this.registryPort = registryPort;
        this.callbackObjPort = callbackObjPort;
        this.server_port_tcp = server_port_tcp;
        this.P_number = P_number;
        this.g = g;
        this.username = username;
        this.uuidclient = uuidclient;
        this.socketClient = socketClient;
        this.rmiRegistry = rmiRegistry;
        this.clientNotificationStub = clientNotificationStub;
        this.notifyEventImpl = notifyEventImpl;
        this.notificationServer = notificationServer;
        this.registrationServer = registrationServer;
        this.multicastManager = multicastManager;
    }

    public NotifyEventImpl getNotifyEventImpl(){
        return notifyEventImpl;
    }

    /**
     * Registra e esporta gli oggetti remoti utilizzati dal client.
     *
     * Questo metodo recupera i riferimenti agli oggetti remoti registrati nel registro RMI
     * e esporta l'oggetto di callback del client come stub remoto per ricevere notifiche dal server.
     *
     * @throws RemoteException    Se si verifica un errore durante l'esportazione o la ricerca degli oggetti remoti.
     * @throws NotBoundException  Se non è possibile trovare un oggetto remoto associato al nome specificato nel registro RMI.
     */
    public void registerAndExportRemoteObjects() throws RemoteException, NotBoundException {
        if (ClientMain.DEBUG) System.out.println("Inizio della registrazione degli oggetti remoti...");

        // Ottiene il riferimento al registro RMI utilizzando la porta specificata
        try {
            rmiRegistry = LocateRegistry.getRegistry(registryPort);
            if (ClientMain.DEBUG) System.out.println("Collegamento al registro RMI sulla porta: " + registryPort);
        } catch (RemoteException e) {
            if (ClientMain.DEBUG) System.err.println("Errore durante la connessione al registro RMI: " + e.getMessage());
            throw e;
        }

        // Registrazione dell'oggetto remoto per il servizio di registrazione
        try {
            if (ClientMain.DEBUG) System.out.println("Ricerca del servizio di registrazione nel registro RMI...");
            Remote remoteObject = rmiRegistry.lookup("REGISTRATION-SERVICE");
            registrationServer = (RegistrationService) remoteObject;
            if (ClientMain.DEBUG) System.out.println("Servizio di registrazione recuperato con successo.");
        } catch (NotBoundException e) {
            if (ClientMain.DEBUG) System.err.println("Servizio di registrazione non trovato nel registro RMI: " + e.getMessage());
            throw e;
        }

        // Registrazione dell'oggetto remoto per il servizio di notifica
        try {
            if (ClientMain.DEBUG) System.out.println("Ricerca del servizio di notifica nel registro RMI...");
            notificationServer = (NotificationService) rmiRegistry.lookup("NOTIFICATION-SERVICE");
            if (ClientMain.DEBUG) System.out.println("Servizio di notifica recuperato con successo.");
        } catch (NotBoundException e) {
            if (ClientMain.DEBUG) System.err.println("Servizio di notifica non trovato nel registro RMI: " + e.getMessage());
            throw e;
        }

        // Esportazione dell'oggetto di notifica del client
        try {
            if (ClientMain.DEBUG) System.out.println("Preparazione per l'esportazione dell'oggetto di notifica del client...");
            notifyEventImpl = new NotifyEventImpl(new ConcurrentHashMap<>());
            if (ClientMain.DEBUG) System.out.println("Istanza NotifyEventImpl ottenuta.");

            // Esporta l'oggetto NotifyEventImpl come stub RMI
            clientNotificationStub = (NotifyEventInterface) UnicastRemoteObject.exportObject(notifyEventImpl, callbackObjPort);
            if (ClientMain.DEBUG) System.out.println("Stub di notifica del client esportato con successo sulla porta: " + callbackObjPort);
        } catch (RemoteException e) {
            if (ClientMain.DEBUG) System.err.println("Errore durante l'esportazione dello stub di notifica del client: " + e.getMessage());
            throw e;
        }

        if (ClientMain.DEBUG) System.out.println("Registrazione degli oggetti remoti completata con successo.");
    }


    /**
     * Deregistra il servizio di notifica del client.
     *
     * Questo metodo esegue la deregistrazione dell'oggetto di notifica del client dallo stub RMI,
     * in modo che il client non riceva più notifiche dal server.
     *
     * @throws RemoteException    Se si verifica un errore durante la deregistrazione dell'oggetto remoto.
     */
    public void unregisterNotificationService() throws RemoteException{
        if (ClientMain.DEBUG) System.out.println("Inizio della deregistrazione del servizio di notifica...");
        // Verifica se l'oggetto di notifica del client è stato inizializzato
        if (notifyEventImpl == null) {
            if (ClientMain.DEBUG)System.err.println("Errore: L'oggetto di notifica del client non è stato inizializzato.");
            return;
        }

        // Deregistra l'oggetto di notifica del client dallo stub RMI
        UnicastRemoteObject.unexportObject(notifyEventImpl, true);


        if (ClientMain.DEBUG) System.out.println("Deregistrazione del servizio di notifica completata.");
    }

    /**
     * Registra un nuovo utente presso il servizio di registrazione.
     * Questo metodo invia una richiesta di registrazione al server e gestisce la risposta.
     *
     * @param username Il nome utente da registrare.
     * @param password La password dell'utente da registrare.
     * @throws RemoteException Se si verifica un errore durante la comunicazione con il server RMI.
     */
    public void registerUser(String username, String password) throws RemoteException {
        // Crea un'istanza di Gson per la manipolazione dei dati JSON
        Gson gson = new Gson();

        // Cifra la password dell'utente utilizzando la chiave di sicurezza
        byte[] encryptedPassword = SecurityClass.encrypt(password, securityKey);

        if(ClientMain.DEBUG) System.out.println("Invio della richiesta di registrazione per l'utente: " + username);

        // Invio della richiesta di registrazione al server
        String registrationResult = registrationServer.addRegistration(username, encryptedPassword, uuidclient);

        // Parsing della risposta JSON dal server
        Response response = gson.fromJson(registrationResult, Response.class);

        // Verifica dello stato della risposta
        if (response.getStatusCode() == 200) {
            // Registrazione avvenuta con successo
            System.out.println("Registrazione completata con successo.");
            System.out.println("Messaggio del server: " + response.getMessage().getBody());
        } else {
            // Errore durante la registrazione
            System.err.println("Errore durante la registrazione dell'utente.");
            System.err.println("Messaggio del server: " + response.getMessage().getBody());
        }
    }

    /**
     * Esegue il processo di login per un utente.
     * crea una richiesta di login,
     * invia la richiesta al server, e gestisce la risposta del server.
     *
     * @param username Nome utente per il login
     * @param password Password dell'utente
     * @throws IOException Se si verifica un errore durante la comunicazione con il server
     */
    public void login(String username, String password, Scanner scanner) throws  RemoteException, IOException{

        // Crea una nuova richiesta di login
        Request request = new Request();
        request.setOperation("Login");
        request.addParam("username", username);
        request.addParam("password", SecurityClass.encrypt(password, securityKey));
        request.addParam("uuidClient", uuidclient);

        // Converti la richiesta in formato JSON
        Gson gson = new Gson();
        String jsonRequest = gson.toJson(request);

        // Invia la richiesta JSON al server
        socketClient.sendRequest(jsonRequest);

        // Legge la risposta JSON dal server
        String jsonResponse = socketClient.readResponse();

        // Trasforma la risposta JSON in un oggetto Response
        Response response = gson.fromJson(jsonResponse, Response.class);

        // Controlla lo stato della risposta
        if (response.getStatusCode() == 200) {
            // Se il login ha successo, aggiorna il nome utente e stampa un messaggio di successo
            this.username = username;
            System.out.println("Successo: " + response.getMessage().getBody());

            // avvio il multicastManager
            multicastManager.startMulticast();

            // Registra per il servizio di notifica
            ClientMain.handleLocalRankingRegistration(scanner);
        } else {
            // Se il login fallisce, stampa un messaggio di errore
            System.out.println("Errore: " + response.getMessage().getBody());
        }


    }

    /**
     * Richiede all'utente di inserire le città per il ranking locale e
     * registra l'utente per ricevere notifiche su ciascuna città.
     * L'inserimento delle città termina quando l'utente inserisce "fine".
     *
     * @throws RemoteException Se si verifica un errore durante la comunicazione con il server RMI.
     */
    public void registerForLocalRanking(String city) throws RemoteException{

        if (username == null){
            System.out.println("Prima di registrarti al servizio di notifica devi loggarti");
            return;
        }
        // Registrati per il servizio di notifica per la città specificata
        String jsonResponse = notificationServer.registerForCallback(city.toLowerCase(), clientNotificationStub);

        // Trasforma la risposta JSON in un oggetto Response
        Gson gson = new Gson();
        Response response = gson.fromJson(jsonResponse, Response.class);

        // Controlla lo stato della risposta
        if (response.getStatusCode() == 200){
            notifyEventImpl.addNewCity(city.toLowerCase());
        }
        System.out.println(response.getMessage().getBody());
    }



    // Deregistra l'utente da tutte le città
    public  void  deregisterAllCities(List<String> registeredCities) throws RemoteException {
        for (String city : registeredCities) {
           deregisterCity(city);
        }
        System.out.println("Sei stato deregistrato da tutte le città.");
    }

    /**
     * Deregistra l'utente dal servizio di notifica per una singola città
     * e gestisce la risposta del server.
     *
     * @param city La città da cui l'utente desidera deregistrarsi.
     * @throws RemoteException Se si verifica un errore durante la comunicazione con il server RMI.
     */
    public void deregisterCity(String city) throws RemoteException {
        Gson gson = new Gson();
        // Invoca il metodo del server per deregistrare l'utente dal servizio di notifica della città specificata
        String jsonResponse = notificationServer.unregisterForCallback(city, clientNotificationStub);

        // Converte la risposta JSON del server in un oggetto Response per gestirne i dati
        Response response = gson.fromJson(jsonResponse, Response.class);

        // Controlla lo stato della risposta ricevuta dal server
        if (response.getStatusCode() == 200) {
            // Se la deregistrazione ha avuto successo, rimuove la classifica degli hotel della città a cui si è deregistrato
            notifyEventImpl.removeHotelsInCity(city);
        }

        // Stampa il messaggio contenuto nella risposta del server
        System.out.println(response.getMessage().getBody());
    }





    /**
     * Esegue il processo di logout per un utente.
     * crea una richiesta di logout,
     * invia la richiesta al server, e gestisce la risposta del server.
     *
     * @param username Nome utente per il login
     * @throws IOException Se si verifica un errore durante la comunicazione con il server
     */
    public void logout(String username) throws IOException{

        // Crea una nuova richiesta di logout
        Request request = new Request();
        request.setOperation("Logout");
        request.addParam("username", username);


        Gson gson = new Gson();

        // Invia la richiesta JSON al server
        String jsonRequest = gson.toJson(request);
        socketClient.sendRequest(jsonRequest);

        // Legge la risposta JSON dal server
        String jsonResponse;
        jsonResponse = socketClient.readResponse();

        // Trasforma la risposta JSON in un oggetto Response
        Response response = gson.fromJson(jsonResponse, Response.class);

        // Controlla lo stato della risposta
        if (response.getStatusCode() == 200) {


            System.out.println("Successo: " + response.getMessage().getBody());

            // Lascia il gruppo multicast per interrompere la ricezione dei pacchetti multicast
            multicastManager.stopMulticast();

            List<String> registeredCities = new ArrayList<>(notifyEventImpl.getHotelList().keySet());

            // Dereregistrati al servizio di notifica dei ranking locale
            if (!registeredCities.isEmpty())deregisterAllCities(registeredCities);

            this.username = null;

        } else {

            System.out.println("Errore: " + response.getMessage().getBody());

        }

    }



    /**
     * Cerca un hotel specificato in una città e visualizza le informazioni dell'hotel.
     *
     * Questo metodo invia una richiesta al server per cercare un hotel basato sul nome e sulla città.
     * Dopo aver ricevuto la risposta dal server, elabora la risposta JSON per ottenere i dettagli dell'hotel
     * e li visualizza utilizzando il metodo printInfoHotel.
     *
     * @param nameHotel Nome dell'hotel da cercare.
     * @param cityHotel Città in cui cercare l'hotel.
     * @throws IOException Se si verifica un errore durante la comunicazione con il server.
     */
    public void searchHotel(String nameHotel, String cityHotel) throws IOException {
        // Crea una nuova richiesta per cercare l'hotel
        Request request = new Request();
        request.setOperation("SearchHotel");
        request.addParam("name_hotel", nameHotel.toLowerCase());
        request.addParam("city_hotel", cityHotel.toLowerCase());

        // Converti la richiesta in formato JSON
        Gson gson = new Gson();
        String jsonRequest = gson.toJson(request);

        // Invia la richiesta al server
        socketClient.sendRequest(jsonRequest);

        // Leggi la risposta dal server
        String jsonResponse = socketClient.readResponse();

        // Trasforma la risposta JSON in un oggetto Response
        Response response = gson.fromJson(jsonResponse, Response.class);

        // Controlla lo stato della risposta
        if (response.getStatusCode() == 200) {
            // Stampa il messaggio di successo
            System.out.println("Successo: " + response.getMessage().getBody());

            // Estrai il risultato dalla risposta
            Object result = response.getMessage().getResult();

            // Converti il risultato in un oggetto Hotel
            Hotel hotel = gson.fromJson(gson.toJson(result), Hotel.class);

            // Stampa le informazioni dell'hotel
            printInfoHotel(hotel);

        } else {
            // Stampa il messaggio di errore
            System.out.println("Errore: " + response.getMessage().getBody());
        }
    }

    /**
     * Stampa le informazioni dettagliate di un hotel.
     *
     * Questo metodo visualizza le informazioni dell'hotel come l'id, il nome, la descrizione,
     * la città, il numero di telefono, i servizi offerti, il tasso e le valutazioni.
     *
     * @param hotel L'oggetto Hotel contenente le informazioni dell'hotel da stampare.
     */
    public void printInfoHotel(Hotel hotel) {
        if (hotel != null) {
            // Stampa le informazioni dell'hotel
            System.out.println("ID: " + hotel.getId());
            System.out.println("Nome: " + hotel.getName());
            System.out.println("Descrizione: " + hotel.getDescription());
            System.out.println("Città: " + hotel.getCity());
            System.out.println("Telefono: " + hotel.getPhone());
            System.out.println("Servizi: " + String.join(", ", hotel.getServices()));
            System.out.println("Rate: " + hotel.getRate());

            // Ottieni le categorie di ratings
            Categories ratings = hotel.getRatings();

            // Stampa i ratings per ogni categoria
            System.out.println("Ratings:");
            System.out.println("  Cleaning: " + ratings.getCleaning());
            System.out.println("  Position: " + ratings.getPosition());
            System.out.println("  Services: " + ratings.getServices());
            System.out.println("  Quality: " + ratings.getQuality());
            System.out.println("------------------------");
        } else {
            // Gestione del caso in cui l'hotel è nullo
            System.out.println("Nessuna informazione disponibile per l'hotel.");
        }
    }

    /**
     * Metodo per cercare tutti gli hotel in una città specifica ordinati per ranking.
     * Invia una richiesta al server per recuperare la lista degli hotel,
     * la elabora e la stampa in modo leggibile.
     *
     * @param cityHotel La città per cui cercare gli hotel.
     * @throws IOException Se si verifica un errore durante la comunicazione con il server.
     */
    public void searchAllHotels(String cityHotel) throws IOException {
        // Crea una nuova richiesta con l'operazione "SearchAllHotels"
        Request request = new Request();
        request.setOperation("SearchAllHotels");
        request.addParam("city_hotel", cityHotel.toLowerCase());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonRequest = gson.toJson(request);

        // Invia la richiesta JSON al server tramite il socket client
        socketClient.sendRequest(jsonRequest);

        // Legge la risposta JSON dal server
        String jsonResponse = socketClient.readResponse();

        // Trasforma la risposta JSON in un oggetto Response
        Response response = gson.fromJson(jsonResponse, Response.class);

        // Controlla lo stato della risposta
        if (response.getStatusCode() == 200) {
            // Successo, stampa il messaggio di successo
            System.out.println("Successo: " + response.getMessage().getBody());

            // Recupera la lista di hotel dalla risposta
            Object result = response.getMessage().getResult();


            List<Hotel> hotelList = new ArrayList<>();
            for (Object item : (ArrayList<?>) result) {

                String itemJson = gson.toJson(item);
                Hotel hotel = gson.fromJson(itemJson, Hotel.class);
                hotelList.add(hotel);
            }

            // Stampa le informazioni di tutti gli hotel nella lista
            printAllHotels(hotelList);

        } else {
            // Errore, stampa il messaggio di errore
            System.out.println("Errore: " + response.getMessage().getBody());
        }
    }


    /**
     * Metodo per stampare le informazioni di tutti gli hotel in una lista.
     *
     * @param hotelList La lista degli hotel da stampare.
     */
    public void printAllHotels(List<Hotel> hotelList) {
        // Verifica se la lista degli hotel è vuota
        if (hotelList.isEmpty()) {
            System.out.println("Nessun hotel trovato.");
            return;
        }

        // Itera su ogni hotel nella lista e stampa le sue informazioni
        for (Hotel hotel : hotelList) {
            printInfoHotel(hotel);
        }
    }


    /**
     * Mostra il distintivo associato all'utente attualmente autenticato.
     *
     * Questo metodo invia una richiesta al server per ottenere il distintivo dell'utente
     * basato sul nome utente. Dopo aver ricevuto la risposta, stampa il distintivo se la
     * richiesta ha avuto successo o un messaggio di errore in caso contrario.
     *
     * @throws IOException Se si verifica un errore durante la comunicazione con il server.
     */
    public void showMyBadge() throws IOException {
        // Crea una nuova richiesta per mostrare i distintivi dell'utente
        Request request = new Request();
        request.setOperation("ShowMyBadges");
        request.addParam("username", username);

        // Converti la richiesta in formato JSON
        Gson gson = new Gson();
        String jsonRequest = gson.toJson(request);

        // Invia la richiesta al server
        socketClient.sendRequest(jsonRequest);

        // Leggi la risposta dal server
        String jsonResponse = socketClient.readResponse();

        // Trasforma la risposta JSON in un oggetto Response
        Response response = gson.fromJson(jsonResponse, Response.class);

        // Controlla lo stato della risposta
        if (response.getStatusCode() == 200) {
            // Successo: Stampa i distintivi ottenuti dal server
            System.out.println("Successo: " + response.getMessage().getResult());
        } else {
            // Errore: Stampa il messaggio di errore ricevuto dal server
            System.out.println("Errore: " + response.getMessage().getBody());
        }
    }

    /**
     * Questo metodo permette di inviare una recensione su un hotel al server.
     * Viene creato un oggetto `Request` che include il nome dell'hotel, la città,
     * il punteggio globale e i punteggi singoli. La richiesta viene poi convertita in formato JSON
     * e inviata al server. Successivamente, la risposta del server viene letta e interpretata.
     *
     * Se il server risponde con un codice di stato 200, la recensione è stata inserita con successo.
     * In caso contrario, viene visualizzato un messaggio di errore con la descrizione fornita dal server.
     *
     * @param nameHotel    Il nome dell'hotel da recensire.
     * @param cityHotel    La città in cui si trova l'hotel.
     * @param globalScore  Il punteggio globale assegnato all'hotel.
     * @param singlesScores Un array di punteggi per diversi aspetti dell'hotel (es. pulizia, posizione, ecc.).
     * @throws IOException Se si verifica un errore durante la comunicazione con il server.
     */
    public void insertReview(String nameHotel, String cityHotel, Integer globalScore, int[] singlesScores) throws IOException {
        // Creazione di un nuovo oggetto Request per rappresentare la richiesta al server

        Request request = new Request();
        request.setOperation("InsertReview");
        request.addParam("name_hotel", nameHotel.toLowerCase());
        request.addParam("city_hotel", cityHotel.toLowerCase());
        request.addParam("global_score", globalScore);
        request.addParam("singles_scores", singlesScores);


        Gson gson = new Gson();
        String jsonRequest = gson.toJson(request);

        // Invio della richiesta JSON al server
        socketClient.sendRequest(jsonRequest);

        // Lettura della risposta JSON inviata dal server
        String jsonResponse = socketClient.readResponse();

        // Trasformazione della risposta JSON in un oggetto Response
        Response response = gson.fromJson(jsonResponse, Response.class);

        // Controllo dello stato della risposta e gestione dell'esito
        if (response.getStatusCode() == 200) {

            System.out.println("Successo: " + response.getMessage().getBody());
        } else {
            System.out.println("Errore: " + response.getMessage().getBody());
        }
    }



    public void closeFullConnection() throws IOException,  NotBoundException  {


        List<String> registeredCities = new ArrayList<>(notifyEventImpl.getHotelList().keySet());

        // Dereregistrati al servizio di notifica dei ranking locale
        if (!registeredCities.isEmpty())deregisterAllCities(registeredCities);

        // Ferma il multicast e chiude il socket
        stopAndCloseSocket();
    }

    public void stopAndCloseSocket() throws IOException,  NotBoundException  {
        unregisterNotificationService();
        multicastManager.stopMulticast();  // Ferma il multicast
        socketClient.closeSocket();        // Chiude il socket
    }
    /**
     * Gestisce il processo di scambio della chiave di cifratura/decifratura utilizzando l'algoritmo di Diffie-Hellman.
     * Questo metodo invia una richiesta al server con la chiave pubblica del client, per avviare il processo di scambio delle chiavi,
     * il server risponde con la propria chiave pubblica così può calcolare la chiave per la cifratura/decifratura delle password.
     *
     * @throws Exception Se si verifica un errore durante la comunicazione con il server o il calcolo della chiave.
     */
    public void sendAndReceiveSecurityData() throws Exception {
        // Variabili per le chiavi e i dati necessari per l'algoritmo di Diffie-Hellman
        BigInteger c; // segreto client
        BigInteger P = BigInteger.valueOf(P_number); //parametro in comune con il server
        BigInteger C = SecurityClass.computeC(g, P_number); // Calcola la propria chiave pubblica che invia al server
        BigInteger S = null; // Chiave pubblica ricevuta dal server

        // Creazione della richiesta per inviare la chiave pubblica del client al server
        Request request = new Request();
        request.setOperation("SendKey");
        request.addParam("public_key", C.toString());
        Gson gson = new Gson();

        // Invio della richiesta al server
        if(ClientMain.DEBUG) System.out.println("Invio della chiave pubblica al server...");
        socketClient.sendRequest(gson.toJson(request));

        // Lettura della risposta del server
        String jsonResponse = socketClient.readResponse();
        if(ClientMain.DEBUG) System.out.println("Risposta ricevuta dal server.");

        // Trasformazione della risposta JSON in un oggetto Response
        Response response = gson.fromJson(jsonResponse, Response.class);

        // Verifica dello stato della risposta
        if (response.getStatusCode() == 200) {
            // Elaborazione della risposta in caso di successo
            if(ClientMain.DEBUG) System.out.println("Chiave pubblica ricevuta correttamente dal server.");

            // Estrazione dei dati dalla risposta
            Pair pair = gson.fromJson(gson.toJson(response.getMessage().getResult()), Pair.class);
            uuidclient = (String) pair.getSecond(); // Recupero dell'UUID del client
            S = new BigInteger(String.valueOf(pair.getFirst())); // Recupero della chiave pubblica del server

            if(ClientMain.DEBUG) System.out.println("UUID del client: " + uuidclient);
            if(ClientMain.DEBUG) System.out.println("Chiave condivisa ricevuta: " + S);
        } else {
            // Gestione degli errori in caso di risposta non positiva
            if(ClientMain.DEBUG) System.err.println("Errore durante l'invio della chiave: " + response.getMessage().getBody());
            throw new Exception("Errore nella ricezione dei dati di sicurezza.");
        }

        // Recupero della chiave segreta dal SecurityClass
        c = SecurityClass.getSecret();

        // Calcolo della chiave di sessione come S^c mod P
        securityKey = S.modPow(c, P).toString(2);
        if(ClientMain.DEBUG) System.out.println("Chiave di sessione calcolata: " + securityKey);

        // Padding della chiave di sessione per assicurare una lunghezza minima di 128 bit
        while (securityKey.length() < 16) {
            securityKey += '0'; // Aggiunge zeri per il padding
        }





    }


    /**
     * Inizializza e avvia il client Hotelier.
     *
     * Questa operazione include la creazione del client socket, la gestione dei dati di sicurezza,
     * la registrazione e l'esportazione degli oggetti remoti e l'avvio del manager per la comunicazione multicast.
     *
     * @throws Exception Se si verifica un errore durante l'inizializzazione del client o la configurazione dei servizi.
     */
    public void start() throws Exception {
        // Inizializza il client socket con la porta TCP del server e il timeout per le risposte
        if (ClientMain.DEBUG) System.out.println("Inizializzazione del client socket...");
        socketClient = new SocketClient(server_port_tcp);
        System.out.println("Client socket inizializzato con successo.");

        // Gestisce la generazione e la ricezione dei dati di sicurezza
        if (ClientMain.DEBUG) System.out.println("Gestione dei dati di sicurezza...");
        sendAndReceiveSecurityData();
        if (ClientMain.DEBUG) System.out.println("Dati di sicurezza gestiti con successo.");

        // Registra ed esportagli oggetti remoti necessari presso il registro RMI
        if (ClientMain.DEBUG) System.out.println("Registrazione ed esportazione degli oggetti remoti...");
        registerAndExportRemoteObjects();
        if (ClientMain.DEBUG) System.out.println("Oggetti remoti registrati con successo.");



        // Inizializza e avvia il manager per la comunicazione multicast
        if (ClientMain.DEBUG) System.out.println("Inizializzazione del manager multicast...");
        multicastManager = new MulticastManager(this.multicastAddress, this.port_multicast);
        if (ClientMain.DEBUG) System.out.println("Manager multicast inizializzato e avviato con successo.");
    }

}
