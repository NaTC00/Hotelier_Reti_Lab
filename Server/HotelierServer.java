package Server;

import Shared.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;

public class HotelierServer {

    // Thread pool per gestire le richieste in parallelo
    private ExecutorService executor;

    // Porta associata al registry RMI
    private int registry_port;

    // Numero associato all'oggetto di registrazione nel registry RMI
    private int registr_obj;

    // Numero associato all'oggetto di notifica nel registry RMI
    private int notific_obj;

    // Porta per la comunicazione TCP del server
    private int server_port_tcp;

    // socket per accettare connessioni TCP in ingresso
    private ServerSocketChannel serverSocketChannel;

    // Selettore per gestire più canali di rete non bloccanti
    private Selector selector;

    // Socket per la comunicazione tramite pacchetti UDP, utilizzato per inviare e ricevere messaggi su un gruppo di multicast
    private DatagramSocket datagramSocket;

    // Indirizzo del gruppo multicast per l'invio di messaggi a più client contemporaneamente
    private String multicastAddress;

    // InetAddress associato all'indirizzo del gruppo multicast
    private InetAddress multicastGroup;

    // Porta associata all'indirizzo multicast
    private int multicastPort;

    // Numero molto grande utilizzato per il calcolo della chiave pubblica (Diffie-Hellman)
    private int P_number;

    // Generatore utilizzato per calcolare le chiavi intermedie nello scambio di chiavi (Diffie-Hellman)
    private int g;

    // Oggetto che gestisce la registrazione di nuovi utenti, esportato tramite RMI
    private RegistrationServiceImpl registrationService;

    // Oggetto che gestisce la registrazione e deregistrazione dei client per il servizio di callback, esportato tramite RMI
    private NotificationServiceImpl notificationService;

    // Registry RMI utilizzato per esportare gli oggetti RMI
    private Registry registryRMI;

    // Database degli hotel, organizzato per città e nome dell'hotel
    private HashMap<String, HashMap<String, Hotel>> hotelDB;

    // HashMap contenente le recensioni associate agli ID degli hotel
    private HashMap<Integer, List<Review>> reviewsHotel;

    // Mappa contenente i client registrati per il servizio di callback per i ranking locali
    private HashMap<String, List<NotifyEventInterface>> hotelCallbacks;

    // Classifica degli hotel, organizzata per città, basata sul punteggio (rate) calcolato
    private HashMap<String, List<Hotel>> rankingList;

    // Mappa contenente le chiavi di decrittazione utilizzate per la comunicazione sicura
    private ConcurrentHashMap<String, String> decryptionKeys;

    // Mappa contenente gli utenti registrati nel sistema
    private ConcurrentHashMap<String, User> registredUserDB;

    // Mappa che associa i canali (SocketChannel) ai client loggati, identificati dal loro nome utente
    private ConcurrentHashMap<SocketChannel, String> socketUserMap;

    // Percorso del file dove vengono salvati gli utenti registrati
    private String filePathUser;

    // Percorso del file dove vengono salvati gli hotel
    private String filePathHotel;

    // Percorso del file dove vengono salvate le recensioni
    private String filePathReviews;

    // Timeout per la scrittura degli utenti sul file (in millisecondi)
    private long timeoutUser;

    // Timeout per la scrittura degli hotel sul file (in millisecondi)
    private long timeoutHotel;

    // Timeout per la scrittura delle recensioni sul file (in millisecondi)
    private long timeouReviews;

    // Timeout per la scrittura della classifica degli hotel (in millisecondi)
    private long timeoutRanking;

    // Lock per gestire l'accesso concorrente alla mappa degli utenti registrati
    private ReadWriteLock lockUser;

    // Lock per gestire l'accesso concorrente alla mappa degli hotel
    private ReadWriteLock lockHotel;

    // Lock per gestire l'accesso concorrente alla mappa delle recensioni degli hotel
    private ReadWriteLock lockReviews;

    // Lock per gestire l'accesso concorrente alla mappa delle callbacks degli hotel
    private ReadWriteLock lockHotelCallback;

    // Lock per gestire l'accesso concorrente alla classifica degli hotel
    private ReadWriteLock lockRanking;

    // Thread responsabile della scrittura degli utenti registrati su file
    private Thread userFileWriterThread;

    // Thread responsabile della scrittura delle recensioni su file
    private Thread reviewFileWriterThread;

    // Thread responsabile della scrittura degli hotel su file
    private Thread hotelFileWriterThread;

    //Thread responsabile del calcolo del ranking
    private Thread calculateRankingThread;

    //Chiave dell'ultimo canale pronto per un evento I/0
    private SelectionKey keyCurrentChannel = null;
    /**
     * Costruttore della classe HotelierServer.
     *
     * @param executor               Thread pool per gestire le richieste in parallelo.
     * @param registry_port          Porta associata al registry RMI.
     * @param registr_obj            Numero associato all'oggetto di registrazione nel registry RMI.
     * @param notific_obj            Numero associato all'oggetto di notifica nel registry RMI.
     * @param server_port_tcp        Porta per la comunicazione TCP del server.
     * @param serverSocketChannel    Socket per accettare connessioni TCP in ingresso
     * @param selector               Indirizzo del gruppo multicast per l'invio di messaggi a più client contemporaneamente
     * @param datagramSocket         Socket per la comunicazione tramite pacchetti UDP, utilizzato per inviare e ricevere messaggi su un gruppo di multicast
     * @param multicastAddress       Indirizzo del gruppo multicast.
     * @param multicastGroup         InetAddress associato al gruppo multicast.
     * @param multicastPort          Porta associata all'indirizzo multicast.
     * @param P_number               Numero utilizzato per il calcolo della chiave pubblica (Diffie-Hellman).
     * @param g                      Generatore per il calcolo delle chiavi intermedie (Diffie-Hellman).
     * @param registrationService    Oggetto per la registrazione di nuovi utenti tramite RMI.
     * @param notificationService    Oggetto per la gestione delle callback tramite RMI.
     * @param registryRMI            Registry RMI utilizzato per esportare gli oggetti RMI.
     * @param hotelDB                Database degli hotel, organizzato per città e nome dell'hotel.
     * @param reviewsHotel           HashMap contenente le recensioni associate agli ID degli hotel.
     * @param hotelCallbacks         Mappa contenente i client registrati per il servizio di callback per i ranking locali
     * @param rankingList            Classifica degli hotel, organizzata per città.
     * @param decryptionKeys         Mappa contenente le chiavi di decrittazione.
     * @param registredUserDB        Mappa contenente gli utenti registrati nel sistema.
     * @param socketUserMap          Mappa che associa i canali (SocketChannel) ai client loggati.
     * @param filePathUser           Percorso del file dove vengono salvati gli utenti registrati.
     * @param filePathHotel          Percorso del file dove vengono salvati gli hotel.
     * @param filePathReviews        Percorso del file dove vengono salvate le recensioni.
     * @param timeoutUser            Timeout per la scrittura degli utenti su file (in millisecondi).
     * @param timeoutHotel           Timeout per la scrittura degli hotel su file (in millisecondi).
     * @param timeouReviews          Timeout per la scrittura delle recensioni su file (in millisecondi).
     * @param timeoutRanking         Timeout per la scrittura della classifica degli hotel (in millisecondi).
     * @param lockUser               Lock per gestire l'accesso concorrente alla mappa degli utenti registrati.
     * @param lockHotel              Lock per gestire l'accesso concorrente alla mappa degli hotel.
     * @param lockReviews            Lock per gestire l'accesso concorrente alla mappa delle recensioni degli hotel.
     * @param lockHotelCallback      Lock per gestire l'accesso concorrente alla mappa delle callbacks degli hotel.
     * @param lockRanking            Lock per gestire l'accesso concorrente alla classifica degli hotel
     * @param userFileWriterThread   Thread responsabile della scrittura degli utenti registrati su file.
     * @param reviewFileWriterThread Thread responsabile della scrittura delle recensioni su file.
     * @param hotelFileWriterThread  Thread responsabile della scrittura degli hotel su file.
     * @param calculateRankingThread Thread responsabile del calcolo del ranking locale.
     */
    public HotelierServer(
            ExecutorService executor,
            int registry_port,
            int registr_obj,
            int notific_obj,
            int server_port_tcp,
            ServerSocketChannel serverSocketChannel,
            Selector selector,
            DatagramSocket datagramSocket,
            String multicastAddress,
            InetAddress multicastGroup,
            int multicastPort,
            int P_number,
            int g,
            RegistrationServiceImpl registrationService,
            NotificationServiceImpl notificationService,
            Registry registryRMI,
            HashMap<String, HashMap<String, Hotel>> hotelDB,
            HashMap<Integer, List<Review>> reviewsHotel,
            HashMap<String, List<NotifyEventInterface>> hotelCallbacks,
            HashMap<String, List<Hotel>> rankingList,
            ConcurrentHashMap<String, String> decryptionKeys,
            ConcurrentHashMap<String, User> registredUserDB,
            ConcurrentHashMap<SocketChannel, String> socketUserMap,
            String filePathUser,
            String filePathHotel,
            String filePathReviews,
            long timeoutUser,
            long timeoutHotel,
            long timeouReviews,
            long timeoutRanking,
            ReadWriteLock lockUser,
            ReadWriteLock lockHotel,
            ReadWriteLock lockReviews,
            ReadWriteLock lockHotelCallback,
            ReadWriteLock lockRanking,
            Thread userFileWriterThread,
            Thread reviewFileWriterThread,
            Thread hotelFileWriterThread,
            Thread calculateRankingThread
    ) {
        this.executor = executor;

        this.registry_port = registry_port;
        this.registr_obj = registr_obj;
        this.notific_obj = notific_obj;

        this.server_port_tcp = server_port_tcp;
        this.serverSocketChannel = serverSocketChannel;
        this.selector = selector;

        this.datagramSocket = datagramSocket;
        this.multicastAddress = multicastAddress;
        this.multicastGroup = multicastGroup;
        this.multicastPort = multicastPort;

        this.P_number = P_number;
        this.g = g;

        this.registrationService = registrationService;
        this.notificationService = notificationService;
        this.registryRMI = registryRMI;

        this.hotelDB = hotelDB;
        this.reviewsHotel = reviewsHotel;
        this.hotelCallbacks = hotelCallbacks;
        this.rankingList = rankingList;
        this.decryptionKeys = decryptionKeys;
        this.registredUserDB = registredUserDB;
        this.socketUserMap = socketUserMap;

        this.filePathUser = filePathUser;
        this.filePathHotel = filePathHotel;
        this.filePathReviews = filePathReviews;

        this.timeoutUser = timeoutUser;
        this.timeoutHotel = timeoutHotel;
        this.timeouReviews = timeouReviews;
        this.timeoutRanking = timeoutRanking;

        this.lockUser = lockUser;
        this.lockHotel = lockHotel;
        this.lockReviews = lockReviews;
        this.lockHotelCallback = lockHotelCallback;
        this.lockRanking = lockRanking;


        this.userFileWriterThread = userFileWriterThread;
        this.reviewFileWriterThread = reviewFileWriterThread;
        this.hotelFileWriterThread = hotelFileWriterThread;
        this.calculateRankingThread = calculateRankingThread;
    }

    /**
     * Avvia il caricamento dei dati dal disco utilizzando thread separati.
     * 1. Caricamento degli utenti registrati.
     * 2. Caricamento degli hotel.
     * 3. Caricamento delle recensioni degli hotel.
     */
    public void loadDataFromDisk(){
        executor.execute(new LoadUsersTask(registredUserDB, filePathUser));
        System.out.println("Database utenti caricato con successo.");

        executor.execute(new LoadHotelsTask(hotelDB, filePathHotel));
        System.out.println("Database hotel caricato con successo.");

        executor.execute(new LoadReviewsTask(reviewsHotel, filePathReviews));
        System.out.println("Database recensioni caricato con successo.");
    }

    /**
     * Esporta gli oggetti remoti RegistrationService e NotificationService tramite RMI
     * e li associa ai nomi specificati nel registro RMI.
     *
     * @throws RemoteException Se si verifica un errore durante l'esportazione degli oggetti remoti.
     */
    public void exportRemoteServices() throws RemoteException {
        // Crea e esporta l'oggetto RegistrationService
        registrationService = new RegistrationServiceImpl(registredUserDB, decryptionKeys, lockUser);
        System.out.println("Esportazione del servizio di registrazione...");
        RegistrationService stubRegistration = (RegistrationService) UnicastRemoteObject.exportObject(registrationService, registr_obj);
        System.out.println("Servizio di registrazione esportato con successo");

        // Crea e esporta l'oggetto NotificationService
        notificationService = new NotificationServiceImpl(hotelDB, hotelCallbacks, lockHotelCallback);
        System.out.println("Esportazione del servizio di notifica...");
        NotificationService stubNotification = (NotificationService) UnicastRemoteObject.exportObject(notificationService, notific_obj);
        System.out.println("Servizio di notifica esportato con successo");

        // Crea un registro RMI sulla porta specificata, se non esiste già
        System.out.println("Creazione del registro RMI sulla porta: " + registry_port);
        LocateRegistry.createRegistry(registry_port);

        // Ottiene il registro RMI
        registryRMI = LocateRegistry.getRegistry(registry_port);

        // Associa i nomi agli oggetti remoti nel registro
        System.out.println("Associazione degli oggetti remoti nel registro...");
        registryRMI.rebind("REGISTRATION-SERVICE", stubRegistration);
        registryRMI.rebind("NOTIFICATION-SERVICE", stubNotification);
        System.out.println("Associazione completata con successo");
    }

    /**
     * Avvia tre thread responsabili della scrittura periodica delle strutture dati su file.
     * - userFileWriterThread: scrive gli utenti registrati su file.
     * - reviewFileWriterThread: scrive le recensioni degli hotel su file.
     * - hotelFileWriterThread: scrive le informazioni degli hotel su file.
     *
     * Ogni thread esegue il proprio task in modo concorrente, salvando le informazioni
     * aggiornate a intervalli regolari, utilizzando i rispettivi lock per gestire l'accesso concorrente.
     */
    public void startFileWriterThreads() {
        // Thread responsabile della scrittura degli utenti registrati su file
        userFileWriterThread = new Thread(new WriteRegistrationToFileTask(registredUserDB, timeoutUser, System.getProperty("user.dir") + filePathUser, lockUser));

        // Thread responsabile della scrittura delle recensioni su file
        reviewFileWriterThread = new Thread(new WriteReviewsToFileTask(reviewsHotel, lockReviews, System.getProperty("user.dir") + filePathReviews, timeouReviews));

        // Thread responsabile della scrittura degli hotel su file
        hotelFileWriterThread = new Thread(new WriteHotelsToFileTask(hotelDB, System.getProperty("user.dir") + filePathHotel, lockHotel, timeoutHotel));

        // Avvia i thread
        userFileWriterThread.start();
        reviewFileWriterThread.start();
        hotelFileWriterThread.start();
    }

    /**
     * Avvia il thread per calcolare il ranking locale degli hotel.
     * Questo metodo crea un nuovo thread che esegue l'operazione di calcolo del ranking
     * basata sui puntegi degli hotel dati nelle recensioni. Una volta avviato, il thread eseguirà
     * il compito di calcolare periodicamente il ranking.
     */
    public void startCalculateRanking() throws SocketException, UnknownHostException {
        //Configurazione del DatagramSocket
        initializeDatagramSocket();

        calculateRankingThread = new Thread(new CalculateLocalRankingTask(hotelDB,  reviewsHotel, hotelCallbacks, rankingList, lockHotel, lockReviews, lockHotelCallback, lockRanking, timeoutRanking, datagramSocket, multicastGroup, multicastPort));

        calculateRankingThread.start();
    }


    /**
     * Avvia il server per gestire le connesioni socket, configurando il ServerSocketChannel e il Selector per monitorare gli eventi di I/O.
     * Entra in un ciclo infinito in cui esamina uno o più NIO SelectableChannels,
     * e determinare se sono pronti per accettare nuove connessioni oppure per leggere dati dal canale
     * In caso di errori di I/O, registra un avviso e termina il server rilasciando tutte le risorse.
     */
    public void startServer() {


        try {
            // Inizializza il ServerSocketChannel, lo configura in modalità non bloccante e lo associa al Selector
            initializeServer();
            while (true){
                try {

                    // Attende che una o più operazioni I/O siano pronte; il metodo `select()` blocca fino a quando un evento si verifica
                    selector.select();

                    // Itera sulle chiavi pronte per l'I/O, processando gli eventi di accettazione e lettura
                    processSelectionKeys();
                }catch (Exception e) {
                    e.printStackTrace();

                    //Qusta eccezione viene lanciata quando la connessione viene interrotta bruscamente lato client
                    if (e.getMessage().trim().equals("Connection reset")){

                        //gestisco la disconnessione del client
                        handleClientDisconnect(keyCurrentChannel);
                    }else break;
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }

        // Termina il server e rilascia le risorse
        terminateServer();
        
    }

    /**
     * Inizializza il ServerSocketChannel per gestire le connessioni TCP in entrata.
     * Lo configura per la modalità non bloccante e lo registra con un Selector per monitorare eventi di accettazione.
     * L'indirizzo e la porta per il server sono specificati nel bind.
     */
    private void initializeServer() throws IOException {
        // Apre il ServerSocketChannel, che gestisce le connessioni TCP in ingresso
        serverSocketChannel = ServerSocketChannel.open();

        // Collega il ServerSocketChannel a un indirizzo e una porta specificati
        serverSocketChannel.bind(new InetSocketAddress(server_port_tcp));

        // Configura il ServerSocketChannel in modalità non bloccante
        serverSocketChannel.configureBlocking(false);

        // Apre un Selector, che è responsabile per monitorare uno o più canali per eventi di I/O
        selector = Selector.open();

        // Registra il ServerSocketChannel con il Selector per monitorare gli eventi di accettazione connessioni
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

    }

    /**
     * Inizializza un DatagramSocket per l'invio di pacchetti UDP.
     * Configura l'indirizzo del gruppo di multicast, consentendo l'invio di dati a più destinatari simultaneamente.
     */
    private void initializeDatagramSocket() throws SocketException, UnknownHostException {
        // Configura l'indirizzo del gruppo di multicast con l'indirizzo specificato
        multicastGroup = InetAddress.getByName(multicastAddress);

        // Crea un socket UDP (DatagramSocket)
        datagramSocket = new DatagramSocket();
    }

    /**
     * Processa le chiavi di selezione pronte per I/O, determinate dal Selector.
     * Gestisce gli eventi di accettazione di nuove connessioni e di lettura dei dati dai canali di comunicazione.
     * Ogni chiave viene processata individualmente e rimossa dall'insieme delle chiavi pronte.
     */
    private void processSelectionKeys() throws IOException {
        // Recupera tutte le chiavi di selezione che rappresentano canali pronti per I/O
        Set<SelectionKey> selectionKeys = selector.selectedKeys();

        // Crea un iteratore per attraversare le chiavi pronte
        Iterator<SelectionKey> iterator = selectionKeys.iterator();

        while (iterator.hasNext()) {
            // Prende la prossima chiave disponibile
            SelectionKey key = iterator.next();
            keyCurrentChannel = key;
            // Rimuove la chiave processata dall'insieme, evitando di riprocessarla
            iterator.remove();

            if (key.isAcceptable()) {
                // Se la chiave è pronta per accettare una nuova connessione, gestisce l'evento di accettazione
                handleAccept(key);
            }

            if (key.isReadable()) {
                // Se la chiave è pronta per la lettura, gestisce l'evento di lettura dal canale associato
                handleRead(key);
            }
        }
    }

    /**
     * Gestisce l'evento di accettazione di una connessione da un client.
     *
     * @param key La chiave di selezione associata al canale che ha generato l'evento.
     * @throws IOException Se si verifica un errore durante la gestione della connessione.
     */
    private void handleAccept(SelectionKey key) throws IOException {
        // Recupera il canale del server associato alla chiave
        ServerSocketChannel server = (ServerSocketChannel) key.channel();

        // Accetta la connessione in arrivo e ottiene il canale del client
        SocketChannel client = server.accept();

        if (client != null) {
            // Stampa un messaggio di log per indicare che una nuova connessione è stata accettata
            System.out.println("Nuova connessione accettata: " + client.getRemoteAddress());

            // Configura il canale del client come non bloccante
            client.configureBlocking(false);

            // Alloca un bytebuffer per la lunghezza del messaggio (4 byte per un int)
            ByteBuffer msgLength = ByteBuffer.allocate(Integer.BYTES);

            // Alloca un bytebuffer per il messaggio stesso (fino a 2048 byte)
            ByteBuffer msgTxt = ByteBuffer.allocate(2048);

            // Crea un array di bytebuffer per contenere la lunghezza del messaggio e il messaggio
            ByteBuffer[] msg = {msgLength, msgTxt};

            // Registra il canale del client nel selettore per l'operazione di lettura
            // e associa il buffer come attachment
            client.register(selector, SelectionKey.OP_READ, msg);


        } else {
            // Se il SocketChannel è null, stampa un messaggio di errore
            System.err.println("Errore: impossibile accettare la connessione.");
        }
    }


    /**
     * Gestisce l'evento di lettura di dati da un client.
     *
     * Questo metodo viene chiamato quando il canale del client è pronto per la lettura.
     * I dati vengono letti dai buffer e, una volta che un messaggio completo è stato ricevuto,
     * viene processato. Se il client chiude la connessione, la chiave viene cancellata.
     *
     * @param key La chiave di selezione associata al canale che ha generato l'evento.
     * @throws IOException Se si verifica un errore durante la lettura o l'elaborazione dei dati.
     */
    private void handleRead(SelectionKey key) throws IOException {

        // Recupera il canale del client associato alla chiave
        SocketChannel client = (SocketChannel) key.channel();

        // Recupera i buffer allegati alla chiave (msgLength e msgTxt)
        ByteBuffer[] buffers = (ByteBuffer[]) key.attachment();

        // Legge i dati dal canale del client nei buffer

        long bytesRead = 0;
        
        // Legge i 4 byte che rappresentano la lunghezza del messaggio
        while (bytesRead < Integer.BYTES) {
            
            int read = client.read(buffers[0]);

            if (read == -1) {

                handleClientDisconnect(key);
                return;
            }
            bytesRead += read;
        }

        buffers[0].flip();
        int messageLength = buffers[0].getInt();


        bytesRead = 0;

        // Legge il messaggio completo
        while (bytesRead < messageLength) {
            int read = client.read(buffers[1]);
            if (read == -1) {
                handleClientDisconnect(key);
                return;
            }
            bytesRead += read;
        }
        buffers[1].flip();

        // Decodifica il messaggio utilizzando UTF-8
        String receivedMessage = StandardCharsets.UTF_8.decode(buffers[1]).toString().trim();

        System.out.println("Richiesta ricevuta dal client: " + receivedMessage);


        // Resetta i buffer per ricevere nuovi messaggi
        resetBuffers(key);

        // Processa il messaggio ricevuto
        processMessage(receivedMessage, key);


    }

    /**
     * Resetta i buffer per leggere nuovi messaggi.
     *
     * @param key La chiave di selezione associata al canale del client.
     */
    private void resetBuffers(SelectionKey key) {
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        ByteBuffer messagenew = ByteBuffer.allocate(2048);
        ByteBuffer[] bfsnew = { length, messagenew };
        key.attach(bfsnew);
        System.out.println("Buffer resettati per la prossima lettura.");
    }



    /**
     * Elabora il messaggio ricevuto dal client e invoca il metodo appropriato in base all'operazione richiesta.
     *
     * @param msg La stringa JSON contenente il messaggio ricevuto.
     * @param key La chiave di selezione associata al canale del client.
     */
    private void processMessage(String msg, SelectionKey key) {

        Gson gson = new Gson();

        // Converte il messaggio JSON in un oggetto JsonObject
        JsonObject jsonMessage = gson.fromJson(msg, JsonObject.class);

        // Estrae l'operazione richiesta dal messaggio
        String operation = jsonMessage.get("operation").getAsString();

        // Estrae i parametri associati all'operazione
        JsonObject paramData = jsonMessage.get("param").getAsJsonObject();
        System.out.println("Parametri ricevuti per l'operazione " + operation + ": " + paramData.toString());

        // Gestisce l'operazione in base al tipo richiesto
        switch (operation) {
            case "Login":
                handleLogin(paramData, key); // Gestisce l'operazione di login
                break;
            case "Logout":
                handleLogout(paramData, key); // Gestisce l'operazione di logout
                break;
            case "SearchHotel":
                handleSearchHotel(paramData, key); // Gestisce l'operazione di ricerca di un hotel specifico
                break;
            case "InsertReview":
                handleInsertReview(paramData, key); // Gestisce l'inserimento di una recensione
                break;
            case "ShowMyBadges":
                handleShowMyBadges(key); // Gestisce la visualizzazione del badge dell'utente
                break;
            case "SearchAllHotels":
                handleSearchAllHotels(paramData, key); // Gestisce la ricerca di tutti gli hotel disponibili in una città
                break;
            case "SendKey":
                handleSendKey(paramData, key); // Gestisce lo scambio della chiave di cifratura con l'algoritmo Diffie-Hellman
                break;
            default:

                System.out.println("Operazione sconosciuta ricevuta: " + operation);
                break;
        }
    }



    private void handleLogin(JsonObject paramData, SelectionKey key) {
        // Estrae l'username dai parametri
        String username = paramData.get("username").getAsString();

        // Estrae la password dai parametri e la converte in un array di byte
        byte[] passwordBytes = extractPassword(paramData);

        // Estrae l'UUID del client dai parametri
        String uuidClient = paramData.get("uuidClient").getAsString();

        System.out.println("Avvio del task di login per l'utente: " + username);

        // Esegue il task di login utilizzando un esecutore (thread pool)
        executor.execute(new LoginTask(registredUserDB, socketUserMap, decryptionKeys, username, passwordBytes, uuidClient, key));

    }


    private void handleLogout(JsonObject paramData, SelectionKey key) {

        String username = paramData.get("username").getAsString();

        System.out.println("Inizio Logout task");
        executor.execute(new LogoutTask(socketUserMap, username, key));
    }

    private void handleSearchHotel(JsonObject paramData, SelectionKey key) {
        String nameHotel = paramData.get("name_hotel").getAsString();
        String cityHotel = paramData.get("city_hotel").getAsString();

        System.out.println("Inizio SearchHotel task");
        executor.execute(new SearchHotelTask(hotelDB, nameHotel, cityHotel, key, lockHotel));
    }

    private void handleInsertReview(JsonObject paramData, SelectionKey key) {
        String nameHotel = paramData.get("name_hotel").getAsString();
        String cityHotel = paramData.get("city_hotel").getAsString();
        int globalScore = paramData.get("global_score").getAsInt();
        int[] singleScores = extractSingleScores(paramData);


        System.out.println("Inizio InsertReview task");
        executor.execute(new InsertReviewTask(reviewsHotel, socketUserMap, hotelDB, registredUserDB, nameHotel, cityHotel, globalScore, singleScores, key, lockReviews, lockUser));

    }

    private void handleShowMyBadges(SelectionKey key) {

        System.out.println("Inizio ShowMyBadges task");
        executor.execute(new ShowMyBadgeTask(registredUserDB, socketUserMap, key, lockUser));
    }

    private void handleSearchAllHotels(JsonObject paramData, SelectionKey key) {
        String cityHotel = paramData.get("city_hotel").getAsString();

        System.out.println("Inizio SearchAllHotels task");
        executor.execute(new SearchAllHotelInCity(rankingList, cityHotel, lockRanking, key));

    }

    private void handleSendKey(JsonObject paramData, SelectionKey key) {
        String publicKey = paramData.get("public_key").getAsString();


        System.out.println("Inizio SendKey task");
        executor.execute(new DHKeyExchangeTask(decryptionKeys,publicKey, P_number, g, key));
    }

    // Metodo che gestisce la chiusura della connessione
    private void handleClientDisconnect(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();

        System.out.println("Connessione chiusa da " + client);

        // Cancellazione della chiave di selezione e chiusura del canale
        key.cancel();
        try {
            client.close();
        } catch (IOException e) {
            System.err.println("Errore durante la chiusura del canale: " + e.getMessage());
        }

        // Rimozione del canale dalla socketUserMap
        executor.execute(new RemoveChannelTask(socketUserMap, client));
    }

    /**
     * Estrae la password dai parametri JSON e la converte in un array di byte.
     *
     */
    private byte[] extractPassword(JsonObject paramData) {
        JsonArray jsonArray = paramData.get("password").getAsJsonArray();
        byte[] passwordBytes = new byte[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            passwordBytes[i] = jsonArray.get(i).getAsByte();
        }
        return passwordBytes;
    }

    /**
     * Estrae i punteggi sintetici di una recensione dati alle varie categorie .
     *
     * @param paramData L'oggetto JsonObject che contiene l'array di punteggi sintetici sotto la chiave "singles_scores".
     * @return Un array di interi contenente i punteggi sintetici estratti dall'array JSON.
     */
    private int[] extractSingleScores(JsonObject paramData) {
        JsonArray jsonArray = paramData.get("singles_scores").getAsJsonArray();
        int[] singleScores = new int[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            singleScores[i] = jsonArray.get(i).getAsInt();
        }
        return singleScores;
    }


    /**
     * Termina il server chiudendo le connessioni di rete, interrompendo i thread in esecuzione e
     * liberando tutte le risorse utilizzate dal server.
     */
    private void terminateServer() {
        // Indica l'inizio della terminazione del server
        System.out.println("Terminazione server in corso");

        try {
            // Deregistra i servizi RMI
            unregisterServices();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (NotBoundException ex) {
            ex.printStackTrace();
        }

        // Chiude il canale del server socket, se aperto
        if (serverSocketChannel != null) {
            try {
                serverSocketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Chiude il selettore di rete, se aperto
        if (selector != null) {
            try {
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Interrompe i thread dedicati alla scrittura dei file e al calcolo del ranking
        userFileWriterThread.interrupt();
        hotelFileWriterThread.interrupt();
        reviewFileWriterThread.interrupt();
        calculateRankingThread.interrupt();

        // Arresta immediatamente l'executor che gestisce i thread
        executor.shutdownNow();
        try {
            // Attende la terminazione dei thread gestiti dall'executor entro 5 secondi
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Chiude il socket datagram
        datagramSocket.close();
    }


    /**
     * Deregistra i servizi RMI (Remote Method Invocation) e libera le risorse associate.
     *
     * @throws RemoteException se si verifica un errore di comunicazione durante la deregistrazione o la disesportazione degli oggetti remoti.
     * @throws NotBoundException se si tenta di deregistrare un servizio che non è associato a un nome nel registro RMI.
     */
    private void unregisterServices() throws RemoteException, NotBoundException {
        // Deregistra il servizio di registrazione utente dal registro RMI
        registryRMI.unbind("REGISTRATION-SERVICE");

        // Disesporta l'oggetto remoto associato al servizio di registrazione
        UnicastRemoteObject.unexportObject(registrationService, true);
        System.out.println("Servizio di registrazione deregistrato con successo.");

        // Deregistra il servizio di registrazione notifica dal registro RMI
        registryRMI.unbind("NOTIFICATION-SERVICE");

        // Disesporta l'oggetto remoto associato al servizio di notifica
        UnicastRemoteObject.unexportObject(notificationService, true);
        System.out.println("Servizio di notifica deregistrato con successo.");
    }



    /**
     * Questo metodo acquisisce la lock di lettura per implementare l'accesso concorrente in modalità di sola lettura.
     * Finché la lock di lettura è acquisita, altre letture possono essere eseguite simultaneamente,
     * ma nessuna scrittura può essere eseguita.
     */
    public static void acquireReadLock(ReadWriteLock lock){
        lock.readLock().lock();
    }

    /**
     * Questo metodo rilascia la lock di lettura, consentendo eventualmente ad altri thread di acquisire
     * la lock di lettura o la lock di scrittura se non ci sono lettori attivi.
     */
    public static void releaseReadLock(ReadWriteLock lock){
        lock.readLock().unlock();
    }

    /**
     * Questo metodo acquisice la lock di scrittura per garantire che solo un thread possa eseguire operazioni di scrittura
     * mentre la lock è acquisita. Nessuna lettura o scrittura concorrente può essere eseguita finché la lock di scrittura è acquisito.
     */
    public static void acquireWriteLock(ReadWriteLock lock){
        lock.writeLock().lock();
    }

    /**
     * Questo metodo rilascia la lock di scrittura, consentendo ad altri thread di acquisire la lock di lettura o scrittura.
     */
    public static void releaseWriteLock(ReadWriteLock lock){
        lock.writeLock().unlock();
    }


    /**
     * Salva il JSON nel file specificato dal filepath.
     *
     * @param json Il contenuto da salvare
     * @param filePath percorso dove salvare il file
     */
    public static void saveJsonToFile(String json, String filePath) {
        try (FileWriter writer = new FileWriter(filePath, false)) {  // false per sovrascrivere il file
            writer.write(json + System.lineSeparator());
        } catch (IOException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * La classe LoadUsersTask implementa Runnable e si occupa di caricare gli utenti registrati
     * dal file dei regisrati in una ConcurrentHashMap. Questa classe è utile per inizializzare il sistema
     * con i dati degli utenti salvati precedentemente.
     */
    public class LoadUsersTask implements Runnable {

        // Mappa contenente gli utenti registrati
        private ConcurrentHashMap<String, User> registredUserDB;

        // Percorso del file JSON da cui caricare gli utenti
        private String filePath;

        /**
         * Costruttore della classe LoadUsersTask.
         *
         * @param registredUserDB ConcurrentHashMap contenente gli utenti registrati.
         * @param filePath        Percorso del file JSON da cui caricare gli utenti.
         */
        public LoadUsersTask(ConcurrentHashMap<String, User> registredUserDB, String filePath) {
            this.registredUserDB = registredUserDB;
            this.filePath = filePath;
        }

        /**
         * Si occupa di verificare l'esistenza del file specificato,
         * di caricare i dati degli utenti registrati dal file e di popolare la mappa registredUserDB.
         * Se il file non esiste, viene creato un nuovo file vuoto.
         */
        @Override
        public void run() {
            // Crea un'istanza di Gson per la conversione da e verso JSON
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            // Crea un oggetto File per il percorso specificato
            File file = new File(System.getProperty("user.dir"), filePath);

            // Se il file non esiste, lo crea
            if (!file.exists()) {
                try {
                    file.createNewFile(); // Creazione del file se non esiste
                } catch (IOException e) {
                    e.printStackTrace(); // Stampa l'errore di creazione del file
                }
            } else {
                // Se il file esiste, legge gli utenti già registrati
                try (BufferedReader reader = new BufferedReader(new FileReader(Paths.get(System.getProperty("user.dir"), filePath).toString()))) {

                    // Definisce il tipo della mappa deserializzata
                    Type type = new TypeToken<ConcurrentHashMap<String, User>>() {}.getType();

                    // Converte il contenuto del file JSON in una mappa temporanea
                    ConcurrentHashMap<String, User> temp = gson.fromJson(reader, type);

                    // Se la mappa temporanea non è null, sovrascrive registredUserDB
                    if (temp != null) {
                        registredUserDB.putAll(temp);
                    }

                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }
        }
    }

    /**
     * La classe LoadHotelsTask implementa Runnable e si occupa di caricare gli hotel
     * da il file degli hotel in una mappa HashMap organizzata per città e nome dell'hotel.
     * Questa classe è utile per inizializzare il sistema con i dati degli hotel salvati precedentemente.
     */
    public class LoadHotelsTask implements Runnable {

        // Mappa contenente le città e gli hotel associati
        private HashMap<String, HashMap<String, Hotel>> hotelDB;

        // Percorso del file JSON da cui caricare gli hotel
        private String filePath;

        /**
         * Costruttore della classe LoadHotelsTask.
         *
         * @param hotelDB  HashMap contenente le città e gli hotel associati.
         * @param filePath Percorso del file JSON da cui caricare gli hotel.
         */
        public LoadHotelsTask(HashMap<String, HashMap<String, Hotel>> hotelDB, String filePath) {
            this.hotelDB = hotelDB;
            this.filePath = filePath;
        }

        /**
         * Questo metodo si occupa di verificare l'esistenza del file specificato,
         * di caricare i dati degli hotel dal file e di popolare la mappa hotelDB.
         * Se il file non esiste, viene creato un nuovo file vuoto.
         */
        @Override
        public void run() {

            File file = new File(System.getProperty("user.dir"), filePath);

            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                // Lettura del file JSON
                try (BufferedReader reader = new BufferedReader(new FileReader(Paths.get(System.getProperty("user.dir"), filePath).toString()))) {

                    // Definizione del tipo di dato per la lista di hotel
                    Type hotelListType = new TypeToken<List<Hotel>>() {
                    }.getType();

                    // Creazione di un'istanza Gson per la conversione da JSON a oggetti Java
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();

                    // Conversione del contenuto JSON in una lista di oggetti Hotel
                    List<Hotel> hotels = gson.fromJson(reader, hotelListType);

                    // Popolazione della mappa hotelDB
                    for (Hotel hotel : hotels) {
                        // Per ogni hotel, aggiungilo alla mappa interna associata alla città
                        hotelDB.computeIfAbsent(hotel.getCity().toLowerCase(), k -> new HashMap<>())
                                .put(hotel.getName().toLowerCase(), hotel);
                    }

                } catch (IOException e) {
                   System.err.println(e.getMessage());
                }
            }
        }
    }

    /**
     * La classe LoadReviewsTask implementa Runnable e si occupa di caricare le recensioni degli hotel
     * dal file delle recensioni in una HashMap, dove ogni chiave rappresenta l'ID di un hotel e il valore è una lista di recensioni.
     * Questa classe è utile per inizializzare il sistema con le recensioni salvate precedentemente.
     */
    public class LoadReviewsTask implements Runnable {

        // HashMap contenente le recensioni associate agli ID degli hotel
        private HashMap<Integer, List<Review>> reviewsHotel;

        // Percorso del file JSON delle recensioni
        private String filePath;

        /**
         * Costruttore della classe LoadReviewsTask.
         *
         * @param reviewsHotel HashMap contenente le recensioni associate agli ID degli hotel
         * @param filePath     Percorso del file JSON delle recensioni
         */
        public LoadReviewsTask(HashMap<Integer, List<Review>> reviewsHotel, String filePath) {
            this.reviewsHotel = reviewsHotel;
            this.filePath = filePath;
        }

        /**
         * Questo metodo si occupa di verificare l'esistenza del file specificato,
         * di caricare i dati delle recensioni dal file e di popolare la mappa reviewsHotel.
         * Se il file non esiste, viene creato un nuovo file vuoto.
         */
        @Override
        public void run() {
            File file = new File(System.getProperty("user.dir"), filePath);

            // Se il file non esiste, lo crea
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                   System.err.println(e.getMessage());
                }
            } else {
                // Se il file esiste, legge le recensioni dal file JSON
                try (BufferedReader reader = new BufferedReader(new FileReader(Paths.get(System.getProperty("user.dir"), filePath).toString()))) {

                    Gson gson = new GsonBuilder().setPrettyPrinting().create();

                    // Definisce il tipo della HashMap
                    Type type = new TypeToken<HashMap<Integer, List<Review>>>() {}.getType();

                    // Legge il contenuto del file e lo deserializza nella HashMap temporanea
                    HashMap<Integer, List<Review>> temp = gson.fromJson(reader, type);

                    // Se la mappa temporanea non è vuota, la copia nella mappa reviewsHotel
                    if (temp != null) {
                        reviewsHotel.putAll(temp);
                    }

                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }
        }
    }

    /**
     * Classe LoginTask che implementa Runnable per gestire l'accesso degli utenti
     */

    public class LoginTask implements Runnable {

        // Database degli utenti registrati, con mappatura username -> oggetto User
        private ConcurrentHashMap<String, User> registredUserDB;

        // Mappa che associa un canale di comunicazione SocketChannel a un nome utente con una sessione di login attiva
        private ConcurrentHashMap<SocketChannel, String> socketUserMap;

        // Mappa che contiene le chiavi di decifratura, associate a ogni client tramite un UUID
        private ConcurrentHashMap<String, String> decryptionKeys;

        // Nome utente fornito per il login
        private String userName;

        // Password fornita per il login (cifrata)
        private byte[] password;

        // UUID del client, utilizzato per recuperare la chiave di decifratura
        private String uuid;

        // Chiave di selezione associata al canale di comunicazione del client
        private SelectionKey key;



        /**
         * Costruttore della classe LoginTask
         *
         * @param registredUserDB   ConcurrentHashMap contenente gli utenti registrati
         * @param socketUserMap     ConcurrentHashMap contenente i canali di connessione associati ai client con una sessione di login attiva
         * @param decryptionKeys    ConcurrentHashMap contenente le chiavi associate ad ogni client per la decifratura delle password
         * @param userName          username relativo
         * @param password          la password relativa
         * @param uuid              chiave per accedere alla ConcurrentHashMap delle chiavi di decifratura
         * @param key               la SelectionKey associata al canale di comunicazione
         */
        public LoginTask(ConcurrentHashMap<String, User> registredUserDB, ConcurrentHashMap<SocketChannel, String> socketUserMap, ConcurrentHashMap<String, String> decryptionKeys, String userName, byte[] password, String uuid, SelectionKey key) {
            this.registredUserDB = registredUserDB;
            this.socketUserMap = socketUserMap;
            this.decryptionKeys = decryptionKeys;
            this.userName = userName;
            this.password = password;
            this.uuid = uuid;
            this.key = key;
        }

        // Metodo per verificare le credenziali di un utente
        public boolean checkCredentials() {
            // Recupera la chiave per decifrare la password
            String keydecryption = decryptionKeys.get(uuid);

            // Decifra la password
            String passwordDecryption = SecurityClass.decrypt(password, keydecryption);

            // Ottiene la password registrata per l'utente
            User user = registredUserDB.get(userName);
            String registeredPassword = user != null ? user.getPassword() : null;

            // Confronta la password fornita con quella registrata
            return passwordDecryption.equals(registeredPassword);
        }

        @Override
        public void run() {

            if (userName == null || password == null || userName.isEmpty() || password.length < 1) {
                new Response(400, new Response.Message(null, "Input nullo")).writeResponse(key);
                return;
            }


            // Verifica se l'utente è registrato
            if (!registredUserDB.containsKey(userName)) {
                new Response(401, new Response.Message("", "Prima di effettuare il login devi registrarti")).writeResponse(key);
                return;
            }

            // Verifica se le credenziali sono corrette
            if (!checkCredentials()) {
                new Response(401, new Response.Message(null, "Le credenziali sono errate")).writeResponse(key);
                return;
            }

            // Ottiene il canale associato alla chiave
            SocketChannel channel = (SocketChannel) key.channel();

            // Verifica se l'utente è già loggato
            if ((socketUserMap.putIfAbsent(channel, userName)) != null) {
                new Response(403, new Response.Message(null, "Hai già effettuato il login")).writeResponse(key);
            } else {
                // Manda risposta di login effettuato al client
                new Response(200, new Response.Message(null, "Login effettuato")).writeResponse(key);
            }
        }
    }



    /**
     * Classe LogoutTask che implementa Runnable per gestire il logout degli utenti.
     */
    public class LogoutTask implements Runnable {

        //ConcurrentHashMap contenente i canali di connessione associati agli utenti con una sessione di login attiva
        private ConcurrentHashMap<SocketChannel, String> socketUserMap;

        //Nome utente che effettua il logout
        private String userName;

        //Chiave di selezione associata al canale di comunicazione
        private SelectionKey key;

        /**
         * Costruttore della classe LogoutTask.
         *
         * @param socketUserMap ConcurrentHashMap contenente i canali di connessione associati agli utenti con una sessione di login attiva
         * @param userName      Nome utente che effettua il logout
         * @param key           Chiave di selezione associata al canale di comunicazione
         */
        public LogoutTask(ConcurrentHashMap<SocketChannel, String> socketUserMap, String userName, SelectionKey key) {
            this.socketUserMap = socketUserMap;
            this.userName = userName;
            this.key = key;
        }

        /**
         * Gestisce il logout dell'utente rimuovendo i canale di comunicazione dalla mappa delle sessioni attive.
         */
        @Override
        public void run() {
            // Ottiene il canale di comunicazione associato alla chiave di selezione
            SocketChannel channel = (SocketChannel) key.channel();

            // Rimuove il canale di comunicazione dalla mappa delle sessioni attive
            if (socketUserMap.remove(channel, userName)) {
                // Invia una risposta di successo al client
                new Response(200, new Response.Message("", "Logout effettuato")).writeResponse(key);
            } else {
                // Invia una risposta di errore al client se l'utente non è loggato
                new Response(401, new Response.Message("", "Utente non loggato")).writeResponse(key);
            }
        }
    }

    // Task per rimuovere il canale dalla mappa
    private class RemoveChannelTask implements Runnable {
        private final ConcurrentHashMap<SocketChannel, String> socketUserMap;
        private final SocketChannel channel;

        public RemoveChannelTask(ConcurrentHashMap<SocketChannel, String> socketUserMap, SocketChannel channel) {
            this.socketUserMap = socketUserMap;
            this.channel = channel;
        }

        @Override
        public void run() {
            String userName = socketUserMap.remove(channel);

            if (userName != null) {
                System.out.println("Rimosso il canale per l'utente: " + userName);
            } else {
                System.out.println("Canale rimosso, ma nessun utente era associato.");
            }
        }
    }


    /**
     * Classe ShowMyBadgesTask che implementa Runnable per gestire la visualizzazione dei distintivi dell'utente.
     */
    public class ShowMyBadgeTask implements Runnable {
        // Mappa contenente gli utenti registrati
        private ConcurrentHashMap<String, User> registeredUserDB;

        // Mappa contenente i canali di connessione associati agli utenti con una sessione di login attiva
        private ConcurrentHashMap<SocketChannel, String> socketUserMap;

        // Chiave di selezione associata al canale di comunicazione
        private SelectionKey key;

        // Lock per gestire l'accesso concorrente agli utenti registrati
        private ReadWriteLock lockUser;

        /**
         * Costruttore della classe ShowMyBadgesTask.
         *
         * @param registeredUserDB ConcurrentHashMap contenente gli utenti registrati
         * @param socketUserMap    ConcurrentHashMap contenente i canali di connessione associati agli utenti con una sessione di login attiva
         * @param key              Chiave di selezione associata al canale di comunicazione
         * @param lockUser   ReadWriteLock per gestire l'accesso concorrente agli utenti registrati
         */
        public ShowMyBadgeTask(ConcurrentHashMap<String, User> registeredUserDB, ConcurrentHashMap<SocketChannel, String> socketUserMap, SelectionKey key, ReadWriteLock lockUser) {
            this.registeredUserDB = registeredUserDB;
            this.socketUserMap = socketUserMap;
            this.key = key;
            this.lockUser = lockUser;
        }

        /**
         * Metodo run che viene eseguito quando il task viene avviato.
         * Gestisce la visualizzazione del distintivo dell'utente.
         */
        @Override
        public void run() {

            // Ottiene il canale di comunicazione associato alla chiave di selezione
            SocketChannel channel = (SocketChannel) key.channel();

            // Verifica se l'utente è loggato
            String userName = socketUserMap.get(channel);
            if (userName == null) {
                new Response(401, new Response.Message(null, "Utente non loggato, per visulizzare il distintivo devi loggarti")).writeResponse(key);
            } else {


                // Ottiene l'utente dalla mappa degli utenti registrati
                User user = registeredUserDB.get(userName);
                try {
                    // Acquisisce il lock in lettura per accedere in mutua esclusione alla lettura del badge dell'utente
                    System.out.println("ShowMyBadgesTask: tento di acquisire lock in lettura sugli utenti");
                    acquireReadLock(lockUser);
                    System.out.println("ShowMyBadgesTask: acquisita la lock in lettura sugli utenti");
                    if (user != null) {

                        // Ottiene i distintivi dell'utente
                        String distintivo = user.getBadges();

                        if (distintivo == null || distintivo.isEmpty()) {
                            distintivo = "Ancora nessun distintivo rilasciato, devi effettuare almeno una recensione.";
                        }

                        // Invia una risposta di successo al client con i distintivi dell'utente
                        new Response(200, new Response.Message(distintivo, "")).writeResponse(key);


                    }else {
                        new Response(401, new Response.Message(null, "Utente non registrato, per visulizzare il distintivo devi registrarti")).writeResponse(key);
                    }
                }finally {
                    // Rilascia la lock in lettura
                    System.out.println("ShowMyBadgesTask: rilasciata lock in lettura sugli utenti");
                    releaseReadLock(lockUser);
                }

            }
        }
    }



    /**
     * Classe DHKeyExchangeTask che implementa Runnable per gestire lo scambio di chiavi Diffie-Hellman.
     */
    public class DHKeyExchangeTask implements Runnable {
        // Mappa contenente le chiavi di decrittazione
        private ConcurrentHashMap<String, String> decryptionKeys;

        // Chiave pubblica del client
        private String publicKeyC;

        // Numero primo P utilizzato nell'algoritmo Diffie-Hellman
        private int P_number;

        // Base g utilizzata nell'algoritmo Diffie-Hellman
        private int g;

        // Chiave di selezione associata al canale di comunicazione
        private SelectionKey key;

        /**
         * Costruttore della classe DHKeyExchangeTask.
         *
         * @param decryptionKeys ConcurrentHashMap contenente le chiavi di decrittazione
         * @param publicKeyC     Chiave pubblica del client
         * @param P_number       Numero primo P utilizzato nell'algoritmo Diffie-Hellman
         * @param g              Base g utilizzata nell'algoritmo Diffie-Hellman
         * @param key            Chiave di selezione associata al canale di comunicazione
         */
        public DHKeyExchangeTask(ConcurrentHashMap<String, String> decryptionKeys, String publicKeyC, int P_number, int g, SelectionKey key) {
            this.decryptionKeys = decryptionKeys;
            this.publicKeyC = publicKeyC;
            this.P_number = P_number;
            this.g = g;
            this.key = key;
        }

        /**
         * Metodo DHAlghoritm che esegue l'algoritmo Diffie-Hellman per calcolare la chiave di sessione.
         *
         * @return Pair contenente la chiave di sessione e il valore S
         */
        public Pair<String, String> DHAlghoritm() {
            BigInteger s;
            BigInteger P = BigInteger.valueOf(P_number);
            BigInteger S = SecurityClass.computeC(g, P_number);
            BigInteger C = new BigInteger(publicKeyC);

            try {
                s = SecurityClass.getSecret();
            } catch (IllegalStateException e) {
                System.err.println(e.getMessage());
                return null;
            }

            // Calcolo della chiave di sessione come C^segreto mod P
            String securityKey = C.modPow(s, P).toString(2);
            System.out.println("Chiave di sessione calcolata: " + securityKey);
            while (securityKey.length() < 16) {
                securityKey += '0'; // Se la chiave è < 128 bit faccio padding
            }

            return new Pair<>(securityKey, S.toString());
        }

        /**
         * Metodo run che viene eseguito quando il task viene avviato.
         * Gestisce lo scambio di chiavi Diffie-Hellman.
         */
        @Override
        public void run() {
            Pair<String, String> keys = DHAlghoritm();
            if (keys == null || keys.getSecond() == null || keys.getFirst() == null) {
                new Response(500, new Response.Message(null, "Scambio chiave non andato a buon fine")).writeResponse(key);
                return;
            }

            String clientId = null;
            do {
                clientId = UUID.randomUUID().toString();
            } while (decryptionKeys.putIfAbsent(clientId, keys.getFirst()) != null);

            new Response(200, new Response.Message(new Pair<>(keys.getSecond(), clientId), "UUID associato alla tua chiave di cifratura")).writeResponse(key);

            System.out.println("Registrata nuova chiave di decifratura");
        }
    }


    /**
     * WriteRegistrationToFileTask è una classe che implementa Runnable e si occupa della scrittura periodica
     * dei dati degli utenti registrati su un file. La classe garantisce che l'accesso concorrente ai dati
     * degli utenti sia sicuro utilizzando una lock in lettura. Gli utenti vengono salvati in formato JSON,
     * facilitando la loro persistenza e successivo recupero.
     */
    public class WriteRegistrationToFileTask implements Runnable {
        // Mappa contenente gli utenti registrati
        private ConcurrentHashMap<String, User> registredUserDB;

        // Timeout per il thread di scrittura
        private long timeout;

        // Percorso del file su cui scrivere gli utenti
        private String filePath;

        // Lock per gestire l'accesso concorrente agli utenti registrati
        private ReadWriteLock lockUser;

        /**
         * Costruttore della classe WriteRegistrationToFileTask.
         *
         * @param registredUserDB   ConcurrentHashMap contenente gli utenti registrati
         * @param timeout           Timeout per il thread di scrittura
         * @param filePath          Percorso del file su cui scrivere gli utenti
         * @param lockUser          ReadWriteLock per gestire l'accesso concorrente agli utenti registrati
         */
        public WriteRegistrationToFileTask(ConcurrentHashMap<String, User> registredUserDB, long timeout, String filePath, ReadWriteLock lockUser) {
            this.registredUserDB = registredUserDB;
            this.timeout = timeout;
            this.filePath = filePath;
            this.lockUser = lockUser;
        }


        /**
         * Metodo run che viene eseguito quando il thread viene avviato.
         * Questo metodo periodicamente acquisisce una lock in lettura sugli utenti registrati,
         * converte la mappa degli utenti in formato JSON e la salva sul file degli utenti registrati.
         * Dopo ogni scrittura, il thread attende per un intervallo di tempo specificato
         * prima di ripetere l'operazione.
         */
        @Override
        public void run() {

            boolean lastTime = false;
            while (true) {

                try {
                    if (Thread.interrupted()){
                        if (!lastTime){
                            System.out.println("Prima di chiudere il programma eseguo l'ultima scrittura sul file dei registrati");
                            lastTime= true;
                        }
                    }
                    System.out.println("WriteRegistrationToFileTask: tento di acquisire lock in lettura sui registrati");
                    acquireReadLock(lockUser);
                    System.out.println("WriteRegistrationToFileTask: acquisita lock in lettura sui registrati");
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();

                    // Converte l'oggetto JSON aggiornato in una stringa
                    String json = gson.toJson(registredUserDB);

                    // Scrive l'oggetto JSON aggiornato nel file
                    saveJsonToFile(json, filePath);
                    System.out.println("Aggiornato file utenti");
                } finally {
                    releaseReadLock(lockUser);
                    System.out.println("WriteRegistrationToFileTask: rilasciata lock in lettura sui registrati");

                }
                if (lastTime) break;
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;

                }
            }

            System.out.println("Thread che scrive i registrati sul disco terminato");
        }
    }




    /**
     * Classe SearchHotelTask che implementa Runnable per gestire la ricerca di un hotel.
     */
    public class SearchHotelTask implements Runnable {

        // Mappa contenente le città e gli hotel associati
        private HashMap<String, HashMap<String, Hotel>> hotelDB;

        // Nome dell'hotel da cercare
        private String nameHotel;

        // Città in cui cercare l'hotel
        private String city;

        // Chiave di selezione associata al canale di comunicazione
        private SelectionKey key;

        // Lock per gestire l'accesso concorrente agli hotel
        private ReadWriteLock lockHotel;



        /**
         * Costruttore della classe SearchHotelTask.
         *
         * @param hotelDB   HashMap contenente le città e gli hotel associati
         * @param nameHotel Nome dell'hotel da cercare
         * @param city      Città in cui cercare l'hotel
         * @param lockHotel Lock per gestire l'accesso concorrente agli hotel
         */
        public SearchHotelTask(HashMap<String, HashMap<String, Hotel>> hotelDB, String nameHotel, String city, SelectionKey key, ReadWriteLock lockHotel) {
            this.hotelDB = hotelDB;
            this.nameHotel = nameHotel;
            this.city = city;
            this.key = key;
            this.lockHotel = lockHotel;
        }

        /**
         * Metodo run che viene eseguito quando il task viene avviato.
         * Gestisce la ricerca di un hotel.
         */
        @Override
        public void run() {

            // Input validation
            if (nameHotel == null || city == null || nameHotel.isEmpty() || city.isEmpty()) {
                new Response(400, new Response.Message(null, "Input nullo")).writeResponse(key);
                return;
            }

                // Verifica se la città esiste nel database
                HashMap<String, Hotel> hotelsInCity = hotelDB.get(city);
                if (hotelsInCity != null) {
                    // Verifica se l'hotel esiste nella città specificata


                    try {
                        Hotel hotel = hotelsInCity.get(nameHotel);

                        // Acquisisco un lock in lettura per garantire la mutua esclusione sull'hotel,
                        // in modo che nessun altro thread possa modificare le variabili d'istanza dell'oggetto quando viene inviato al client.

                        System.out.println("SearchHotelTask: tento di acquisire la lock in lettura sugli hotel");
                        acquireReadLock(lockHotel);
                        System.out.println("SearchHotelTask: acquisita la lock in lettura sugli hotel");


                        if (hotel != null) {
                            // Hotel trovato, invia le info sull'hotel al client
                            new Response(200, new Response.Message(hotel, "Hotel trovato")).writeResponse(key);
                        } else {
                            // Hotel non trovato
                            new Response(404, new Response.Message(null, "Hotel non trovato")).writeResponse(key);
                        }
                    }finally {
                        releaseReadLock(lockHotel);
                        System.out.println("SearchHotelTask: rilasciata la lock in lettura sugli hotel");
                    }
                } else {
                    // Città non trovata
                    new Response(404, new Response.Message(null, "Non ci sono hotel nella citta cercata")).writeResponse(key);
                }


        }
    }


    /**
     * Questa classe implementa un task Runnable che ricerca tutti gli hotel
     * in una città specifica e invia una risposta al client contenente la lista
     * degli hotel ordinati in base al punteggio globale (rate).
     */
    public class SearchAllHotelInCity implements Runnable {

        // Classifica degli hotel, organizzata per città, basata sul punteggio (rate) calcolato
        private HashMap<String, List<Hotel>> rankingList;

        // Nome della città per cui effettuare la ricerca
        private String city;

        // Lock per gestire l'accesso concorrente alla classifica degli hotel
        private ReadWriteLock lockRanking;

        // Chiave di selezione per il canale di comunicazione con il client
        private SelectionKey key;

        /**
         * Costruttore della classe SearchAllHotelInCity
         *
         * @param rankingList HashMap contenente la classifica degli hotel organizzata per città
         * @param city        La città per cui effettuare la ricerca degli hotel
         * @param lockRanking Lock per garantire accesso concorrente sicuro alla classifica degli hotel
         * @param key         SelectionKey associata al canale di comunicazione con il client
         */
        public SearchAllHotelInCity(HashMap<String, List<Hotel>> rankingList, String city, ReadWriteLock lockRanking, SelectionKey key) {
            this.rankingList = rankingList;
            this.city = city;
            this.lockRanking = lockRanking;
            this.key = key;
        }

        /**
         * Questo task gestisce la ricerca degli hotel in una città specifica,
         * e invia la risposta al client con la lista degli hotel trovati,
         * ordinati in base al punteggio globale (rate).
         */
        @Override
        public void run() {
            // Verifica se il nome della città è valido
            if(city == null || city.isEmpty()){
                new Response(400, new Response.Message(null, "Input non valido")).writeResponse(key);
                return;
            }

            try {
                // Acquisisce la lock in lettura per garantire un accesso thread-safe alla classifica degli hotel
                System.out.println("SearchAllHotelInCity: tento di acquisire lock in lettura sulla classifica");
                acquireReadLock(lockRanking);
                System.out.println("SearchAllHotelInCity: acquisita lock in lettura sulla classifica");

                // Recupera la lista degli hotel per la città specificata dalla classifica
                List<Hotel> rankingCity = rankingList.get(city);

                // Se la lista degli hotel esiste (città trovata nella classifica)
                if (rankingCity != null){
                    // Invia una risposta al client con la lista degli hotel trovati e un messaggio di successo
                    new Response(200, new Response.Message(rankingCity, "Classifica degli hotel per la città di " + city + " recuperata con successo")).writeResponse(key);
                } else {
                    // Se la città non è presente nella classifica, invia una risposta con un messaggio di errore
                    new Response(404, new Response.Message(null, "Non ci sono hotel nella città cercata")).writeResponse(key);
                }
            } finally {
                // Rilascia la lock in lettura dopo aver completato l'operazione
                releaseReadLock(lockRanking);
                System.out.println("SearchAllHotelInCity: rilasciata lock in lettura sulla classifica");
            }
        }

    }


    /**
     * Classe InsertReviewTask che implementa Runnable per gestire l'inserimento di una recensione.
     */
    public class InsertReviewTask implements Runnable {

        // HashMap contenente le recensioni associate agli ID degli hotel
        private HashMap<Integer, List<Review>> reviewsHotel;

        // ConcurrentHashMap per tenere traccia degli utenti collegati ai loro canali Socket
        private ConcurrentHashMap<SocketChannel, String> socketUserMap;

        // Database degli hotel, organizzato per città e nome dell'hotel
        private HashMap<String, HashMap<String, Hotel>> hotelDB;

        // ConcurrentHashMap contenente gli utenti registrati
        private ConcurrentHashMap<String, User> registredUserDB;

        // Nome dell'hotel per cui inserire la recensione
        private String nameHotel;

        // Città in cui si trova l'hotel
        private String city;

        // Punteggio globale assegnato dall'utente
        private int globalScore;

        // Array dei punteggi specifici assegnati dall'utente per varie categorie
        private int[] singleScores;

        // Chiave di selezione associata al canale di comunicazione
        private SelectionKey key;

        // Lock per gestire l'accesso concorrente alle recensioni  degli hotel
        private ReadWriteLock lockReviews;

        // Lock per gestire l'accesso concorrente agli utenti registrati
        private ReadWriteLock lockUser;

        /**
         * Costruttore della classe InsertReviewTask.
         *
         * @param reviewsHotel      HashMap contenente le recensioni associate agli ID degli hotel
         * @param socketUserMap     ConcurrentHashMap concorrente per tenere traccia degli utenti collegati ai loro canali Socket
         * @param hotelDB           Database degli hotel, organizzato per città e nome dell'hotel
         * @param registredUserDB   ConcurrentHashMap contenente gli utenti registrati
         * @param nameHotel         Nome dell'hotel per cui inserire la recensione
         * @param city              Città in cui si trova l'hotel
         * @param globalScore       Punteggio globale assegnato dall'utente
         * @param singleScores      Array dei punteggi specifici assegnati dall'utente per varie categorie
         * @param key               Chiave di selezione associata al canale di comunicazione
         * @param lockReviews       Lock per gestire l'accesso concorrente alle recensioni degli hotel
         * @param lockUser          Lock per gestire l'accesso concorrente agli utenti registrati
         */
        public InsertReviewTask(HashMap<Integer, List<Review>> reviewsHotel, ConcurrentHashMap<SocketChannel, String> socketUserMap, HashMap<String, HashMap<String, Hotel>> hotelDB, ConcurrentHashMap<String, User> registredUserDB, String nameHotel, String city, int globalScore, int[] singleScores, SelectionKey key, ReadWriteLock lockReviews, ReadWriteLock lockUser) {
            this.reviewsHotel = reviewsHotel;
            this.socketUserMap = socketUserMap;
            this.hotelDB = hotelDB;
            this.registredUserDB = registredUserDB;
            this.nameHotel = nameHotel;
            this.city = city;
            this.globalScore = globalScore;
            this.singleScores = singleScores;
            this.key = key;
            this.lockReviews = lockReviews;
            this.lockUser = lockUser;
        }

        /**
         * Metodo run che viene eseguito quando il thread viene avviato.
         * Questo metodo gestisce l'inserimento della recensione in modo concorrente.
         */
        @Override
        public void run() {
            // Validazione dell'input
            if (nameHotel == null || nameHotel.isEmpty() || city == null || city.isEmpty() || singleScores == null)  {
                new Response(400, new Response.Message(null, "Input non valido")).writeResponse(key);
                return;
            }
            if (globalScore < 0 || globalScore > 5) {
                new Response(400, new Response.Message(null, "GlobalScore deve essere tra 0 e 5")).writeResponse(key);
                return;
            }
            for (int score : singleScores) {
                if (score < 0 || score > 5) {
                    new Response(400, new Response.Message(null, "SingleScores deve essere tra 0 e 5")).writeResponse(key);
                    return;
                }
            }

            // Recupera il canale di comunicazione e il nome utente associato
            SocketChannel channel = (SocketChannel) key.channel();
            String username = socketUserMap.get(channel);
            if (username == null) {
                new Response(401, new Response.Message(null, "Prima di inserire una recensione devi loggarti")).writeResponse(key);
            } else {
                // Verifica se la città esiste nel database
                HashMap<String, Hotel> hotelsInCity = hotelDB.get(city);
                if (hotelsInCity != null) {
                    // Verifica se l'hotel esiste nella città specificata
                    Hotel hotel = hotelsInCity.get(nameHotel);

                    if (hotel != null) {
                        // Ottieni la data odierna nel formato gg/mm/aaaa
                        String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

                        // Crea una nuova recensione con i dettagli forniti
                        Review review = new Review(username, hotel.getId(), nameHotel, globalScore, todayDate, new Categories(singleScores[0], singleScores[1], singleScores[2], singleScores[3]));

                        try {
                            // Acquisisce il lock in scrittura per garantire un aggiornamento consistenete
                            System.out.println("InsertReviewTask: tento di acquisire la lock in scrittura sulle recensioni");
                            acquireWriteLock(lockReviews);
                            System.out.println("InsertReviewTask: acquisita la lock in scrittura sulle recensioni");

                            // Recupera la lista delle recensioni associate all'hotel specificato
                            List<Review> reviewsOfHotel = reviewsHotel.computeIfAbsent(hotel.getId(), k -> new ArrayList<>());

                            // Aggiunge la nuova recensione alla lista delle recensioni dell'hotel
                            reviewsOfHotel.add(review);
                        }finally {
                            // Rilascia il lock in scrittura
                            releaseWriteLock(lockReviews);
                            System.out.println("InsertReviewTask: rilascio la lock in scrittura sulle recensioni");

                        }


                       try {
                           User user = registredUserDB.get(username);

                           acquireWriteLock(lockUser);
                           user.incrementNumReview();
                       }finally {
                           releaseWriteLock(lockUser);
                       }



                        new Response(200, new Response.Message(null, "Recensione aggiunta con successo")).writeResponse(key);
                    } else {
                        // Hotel non trovato
                        new Response(404, new Response.Message(null, "Hotel non trovato")).writeResponse(key);
                    }
                } else {
                    // Città non trovata
                    new Response(404, new Response.Message(null, "Non ci sono hotel nella città cercata")).writeResponse(key);
                }
            }
        }
    }

    /**
     * WriteHotelsToFileTask è una classe che implementa Runnable e si occupa della scrittura periodica
     * dei dati degli hotel su un file. La classe garantisce che l'accesso concorrente ai dati degli hotel
     * sia sicuro utilizzando una lock in lettura. Gli hotel vengono salvati in formato JSON, facilitando
     * la loro persistenza e successivo recupero.
     */
    public class WriteHotelsToFileTask implements Runnable {

        // Database degli hotel, organizzato per città e nome dell'hotel
        private HashMap<String, HashMap<String, Hotel>> hotelDB;

        // Percorso del file su cui scrivere gli hotel
        private String filePath;

        // Lock per gestire l'accesso concorrente agli hotel
        private ReadWriteLock lockHotel;

        // Intervallo tra le scritture degli hotel sul file e il calcolo del ranking
        private long timeout;

        /**
         * Costruttore della classe WriteHotelsToFileTask.
         *
         * @param hotelDB           Database degli hotel, organizzato per città e nome dell'hotel
         * @param filePath          Percorso del file su cui scrivere gli hotel
         * @param lockHotel         Lock per gestire l'accesso concorrente agli hotel
         * @param timeout           Intervallo tra le scritture degli hotel sul file e il calcolo del ranking
         */
        public WriteHotelsToFileTask(HashMap<String, HashMap<String, Hotel>> hotelDB, String filePath, ReadWriteLock lockHotel, long timeout) {
            this.hotelDB = hotelDB;
            this.filePath = filePath;
            this.lockHotel = lockHotel;
            this.timeout = timeout;
        }

        /**
         * Converte l'hotelDB in un JsonArray.
         *
         * @return JsonArray rappresentante gli hotel
         */
        private String convertHotelDBToJsonArray() {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonArray jsonArray = new JsonArray();

            for (HashMap<String, Hotel> hotelsInCity : hotelDB.values()) {
                for (Hotel hotel : hotelsInCity.values()) {
                    // Converti l'oggetto Hotel in JsonObject
                    JsonObject jsonObject = gson.toJsonTree(hotel).getAsJsonObject();
                    jsonArray.add(jsonObject);
                }
            }

            return gson.toJson(jsonArray);
        }

        /**
         * Metodo run che viene eseguito quando il thread viene avviato.
         * Questo metodo periodicamente acquisisce una lock in lettura sugli hotel,
         * converte il database degli hotel in formato JSON e lo salva sul file degli hotel.
         * Dopo ogni scrittura, il thread attende per un intervallo di tempo specificato
         * prima di ripetere l'operazione.
         */
        @Override
        public void run() {

            boolean lastTime = false;
            while (true) {
                try {
                    if (Thread.interrupted()){
                        if (!lastTime){
                            System.out.println("Prima di chiudere il programma eseguo l'ultima scrittura sul file degli hotel");
                            lastTime = true;
                        }

                    }
                    System.out.println("WriteHotelsToFileTask: tento di acquisire lock in lettura sugli hotel");
                    acquireReadLock(lockHotel);
                    System.out.println("WriteHotelsToFileTask: acquisita lock in lettura sugli hotel");

                    String hotelArray = convertHotelDBToJsonArray();
                    saveJsonToFile(hotelArray, filePath);
                    System.out.println("Aggiornato file hotel");

                } finally {
                    releaseReadLock(lockHotel);
                    System.out.println("WriteHotelsToFileTask: rilasciata lock in lettura sugli hotel");


                }
                if (lastTime) break;
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;

                }

            }
            System.out.println("Thread che scrive gli hotel sul disco terminato");
        }
    }

    /**
     * WriteReviewsToFileTask è una classe che implementa Runnable e si occupa della scrittura periodica
     * delle recensioni degli hotel su un file. La classe garantisce che l'accesso concorrente ai dati
     * delle recensioni sia sicuro utilizzando una lock in lettura. Le recensioni vengono salvate in formato
     * JSON, facilitando la loro persistenza e successivo recupero.
     */
    public class WriteReviewsToFileTask implements Runnable {

        // HashMap contenente le recensioni associate agli ID degli hotel
        private HashMap<Integer, List<Review>> reviewsHotel;

        // Lock per gestire l'accesso concorrente alle recensioni degli hotel
        private ReadWriteLock lockReviews;

        // Percorso del file su cui scrivere le recensioni
        private String filePath;

        // Intervallo tra le scritture delle recensioni sul disco in millisecondi
        private long timeout;

        /**
         * Costruttore della classe WriteReviewsToFileTask.
         *
         * @param reviewsHotel Mappa delle recensioni degli hotel da scrivere
         * @param lockReviews  Lock per gestire l'accesso concorrente alle recensioni
         * @param filePath     Percorso del file su cui scrivere le recensioni
         * @param timeout      Intervallo tra le scritture sul disco
         */
        public WriteReviewsToFileTask(HashMap<Integer, List<Review>> reviewsHotel,
                                      ReadWriteLock lockReviews,
                                      String filePath,
                                      long timeout) {
            this.reviewsHotel = reviewsHotel;
            this.lockReviews = lockReviews;
            this.filePath = filePath;
            this.timeout = timeout;
        }

        /**
         * Periodicamente acquisisce una lock in lettura sulle recensioni degli hotel,
         * converte le recensioni in formato JSON e le salva su disco. Dopo ogni scrittura, il thread
         * attende per un intervallo di tempo specificato prima di ripetere l'operazione.
         */
        @Override
        public void run() {

            boolean lastTime = false;
            while (true) {
                try {
                    if (Thread.interrupted()){
                        if (!lastTime){
                            System.out.println("Prima di chiudere il programma eseguo l'ultima scrittura sul file delle recensione");
                            lastTime = true;
                        }
                    }
                    System.out.println("WriteReviewsToFileTask: tentando di acquisire lock in lettura sulle recensioni");
                    acquireReadLock(lockReviews);
                    System.out.println("WriteReviewsToFileTask: lock in lettura acquisita sulle recensioni");

                    // Converti le recensioni in un JSONObject
                    String reviewsJson = convertReviewsToJson();

                    // Salva il JSON nel file
                    saveJsonToFile(reviewsJson, filePath);
                    System.out.println("Aggiornato file reviews");

                } finally {
                    releaseReadLock(lockReviews);
                    System.out.println("WriteReviewsToFileTask: lock in lettura rilasciata sulle recensioni");


                }
                if (lastTime) break;
                try {
                    // Attende per il periodo specificato prima di eseguire un'altra scrittura
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;


                }
            }
            System.out.println("Thread che scrive le recensioni sul disco terminato");
        }

        /**
         * Converte le recensioni in un formato JSON.
         *
         * @return Stringa JSON delle recensioni
         */
        private String convertReviewsToJson() {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject jsonObject = new JsonObject();

            for (Map.Entry<Integer, List<Review>> entry : reviewsHotel.entrySet()) {
                Integer hotelId = entry.getKey();
                List<Review> reviews = entry.getValue();

                // Crea un JSONArray per le recensioni di un hotel specifico
                JsonArray jsonArray = new JsonArray();

                // Itera attraverso tutte le recensioni per un hotel specifico
                for (Review review : reviews) {
                    // Converti la recensione in JsonObject e aggiungila al JSONArray
                    jsonArray.add(gson.toJsonTree(review).getAsJsonObject());
                }

                // Aggiungi il JSONArray al JSONObject principale con l'ID dell'hotel come chiave
                jsonObject.add(hotelId.toString(), jsonArray);
            }

            return gson.toJson(jsonObject);
        }


    }

    /**
     * CalculateLocalRankingTask è una classe che implementa Runnable e si occupa del calcolo del ranking degli hotel
     * in base alle recensioni disponibili per ciascun hotel in diverse città. Questo task viene eseguito periodicamente
     * e aggiorna la classifica degli hotel per ogni città, notificando i client registrati in caso di cambiamenti nella
     * classifica locali a cui si sono registrati. La classe gestisce l'accesso concorrente ai dati utilizzando lock per garantire la consistenza dei dati
     * durante le operazioni di lettura e scrittura.
     */
    public class CalculateLocalRankingTask implements Runnable{
        // Database degli hotel, organizzato per città e nome dell'hotel
        private HashMap<String, HashMap<String, Hotel>> hotelDB;

        // HashMap contenente le recensioni associate agli ID degli hotel
        private HashMap<Integer, List<Review>> reviewsHotel;


        // Mappa contenente i client registrati per il servizio di callback per i ranking locali
        private HashMap<String, List<NotifyEventInterface>> hotelCallbacks;

        private HashMap<String, List<Hotel>> rankingList;

        // Lock per gestire l'accesso concorrente agli hotel
        private ReadWriteLock lockHotel;

        // Lock per gestire l'accesso concorrente alle recensioni deglle review degli hotel
        private ReadWriteLock lockReviews;

        // Lock per gestire l'accesso concorrente alle callbacks
        private ReadWriteLock lockHotelCallback;

        //lock per gestire l'accesso concorrente alla classifica
        private ReadWriteLock lockRanking;

        // Intervallo tra le scritture degli hotel sul file e il calcolo del ranking
        private long timeout;

        private DatagramSocket datagramSocket;

        // InetAddress associato all'indirizzo del gruppo multicast
        private InetAddress multicastGroup;

        // Porta associata all'indirizzo multicast
        private int multicastPort;

        /**
         * Costruttore della classe CalculateLocalRankingTask.
         *
         * @param hotelDB           Database degli hotel, organizzato per città e nome dell'hotel.
         * @param reviewsHotel      HashMap contenente le recensioni associate agli ID degli hotel.
         * @param hotelCallbacks    Mappa contenente i client registrati per il servizio di callback per i ranking locali
         * @param rankingList       Mappa concorrente per la lista dei ranking degli hotel per città.
         * @param lockHotel         Lock per gestire l'accesso concorrente agli hotel.
         * @param lockReviews       Lock per gestire l'accesso concorrente alle recensioni degli hotel.
         * @param lockHotelCallback Lock per gestire l'accesso concorrente alle callbacks.
         * @param lockRanking       Lock per gestire l'accesso concorrente alla classifica
         * @param timeout           Intervallo tra le scritture degli hotel sul file e il calcolo del ranking.
         * @param datagramSocket    Socket per la comunicazione tramite pacchetti UDP, utilizzato per inviare e ricevere messaggi su un gruppo di multicast
         * @param multicastGroup    InetAddress associato al gruppo multicast.
         * @param multicastPort     Porta associata all'indirizzo multicast.
         */

        public CalculateLocalRankingTask(HashMap<String, HashMap<String, Hotel>> hotelDB,
                                         HashMap<Integer, List<Review>> reviewsHotel,
                                         HashMap<String, List<NotifyEventInterface>> hotelCallbacks,
                                         HashMap<String, List<Hotel>> rankingList, ReadWriteLock lockHotel,
                                         ReadWriteLock lockReviews, ReadWriteLock lockHotelCallback,
                                         ReadWriteLock lockRanking, long timeout,
                                         DatagramSocket datagramSocket, InetAddress multicastGroup, int multicastPort){
            this.hotelDB = hotelDB;
            this.reviewsHotel = reviewsHotel;
            this.hotelCallbacks = hotelCallbacks;
            this.rankingList = rankingList;
            this.lockHotel = lockHotel;
            this.lockReviews = lockReviews;
            this.lockHotelCallback = lockHotelCallback;
            this.lockRanking = lockRanking;
            this.timeout = timeout;
            this.datagramSocket = datagramSocket;
            this.multicastGroup = multicastGroup;
            this.multicastPort = multicastPort;
        }

        // Metodo per inviare la notifica UDP tramite multicast
        private void sendMulticastNotification(Hotel topHotel, String city) {
            try {
                String message = "Nuovo primo classificato: " + topHotel.getName() + " è ora al primo posto nella città di " + city;

                // Converte il messaggio in byte
                byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);

                // Crea il pacchetto UDP
                DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length, multicastGroup, multicastPort);

                // Invia il pacchetto al gruppo di multicast
                datagramSocket.send(packet);
                System.out.println("Notifica inviata: " + message);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Calcola periodicamente il rank degli hotel in base alle recensioni disponibili.
         * Dopo aver calcolato il rank, confronta la nuova classifica con quella precedente e notifica i client
         * registrati se ci sono stati cambiamenti. L'accesso ai dati è sincronizzato tramite lock per garantire
         * la consistenza delle informazioni durante le operazioni concorrenti.
         */
        @Override
        public void run(){
            while (!Thread.interrupted()) {

                try {


                    System.out.println("CalculateLocalRankingTask: tento di acquisire lock in lettura sulle recensioni");
                    acquireReadLock(lockReviews);
                    System.out.println("CalculateLocalRankingTask: acquisita lock in lettura sulle recensioni");

                    System.out.println("CalculateLocalRankingTask: tento di acquisire lock in scrittura sugli hotel");
                    acquireWriteLock(lockHotel);
                    System.out.println("CalculateLocalRankingTask: acquisita lock in scrittura sugli hotel");

                    System.out.println("CalculateLocalRankingTask: tento di acquisire lock in scrittura sulla classifica");
                    acquireWriteLock(lockRanking);
                    System.out.println("CalculateLocalRankingTask: acquisita lock in scrittura suglla classifica");

                    // Itera su tutte le città nel database degli hotel
                    for (Map.Entry<String, HashMap<String, Hotel>> cityEntry : hotelDB.entrySet()) {
                        String city = cityEntry.getKey();
                        HashMap<String, Hotel> hotelsInCity = cityEntry.getValue();

                        // Itera su tutti gli hotel in una città specifica e calcola il ranking
                        for (Hotel hotel : hotelsInCity.values()) {
                            // Recupera le recensioni per l'hotel corrente usando l'ID dell'hotel
                            List<Review> reviews = reviewsHotel.get(hotel.getId());

                            // Calcola il ranking per l'hotel corrente
                            hotel.calcolaRanking(reviews);
                        }

                        // Crea una lista ordinata degli hotel basata sul rate
                        List<Hotel> sortedHotels = new ArrayList<>(hotelsInCity.values());
                        sortedHotels.sort((hotel1, hotel2) -> {
                            Double rate1 = hotel1.getRate();
                            Double rate2 = hotel2.getRate();
                            return rate2.compareTo(rate1);  // Ordine decrescente
                        });

                        // Confronta la nuova classifica con quella precedente
                        List<Hotel> oldRanking = rankingList.get(city);
                        boolean res = false;
                        if (oldRanking != null){
                            res = oldRanking.equals(sortedHotels);
                            if (!oldRanking.get(0).equals(sortedHotels.get(0))){
                                sendMulticastNotification(sortedHotels.get(0), city);
                            }
                        }


                        if (oldRanking == null || !res) {


                            rankingList.put(city, sortedHotels);
                            System.out.println("Classifica aggiornata per la città: " + city);

                            System.out.println("CalculateLocalRankingTask: tento di acquisire la lock in lettura sulle callbacks");
                            HotelierServer.acquireReadLock(lockHotelCallback);
                            System.out.println("CalculateLocalRankingTask: acquisita la lock in lettura sulle callback");

                            // Ottieni la lista dei client da notificare per la città specifica
                            List<NotifyEventInterface> callbacks = hotelCallbacks.get(city);
                            List<NotifyEventInterface> toRemove = new ArrayList<>();
                            if (callbacks != null && !callbacks.isEmpty()) {
                                for (NotifyEventInterface callback : callbacks) {
                                    // Notifica ciascun client registrato
                                    try {
                                        callback.notifyEvent(sortedHotels, city);
                                    }catch (RemoteException e) {
                                        System.err.println(e.getMessage());
                                        // Aggiungi lo stub del client alla lista degli elementi da rimuovere
                                        toRemove.add(callback);

                                    }

                                }
                                // Rimuovi gli stub dei client che non sono più attivi dalla struttura dati hotelCallbacks
                                callbacks.removeAll(toRemove);
                            }
                            releaseReadLock(lockHotelCallback);
                            System.out.println("CalculateLocalRankingTask: rilasciata lock in lettura sulle callbacks");


                        }

                    }


                }finally {

                    releaseReadLock(lockReviews);
                    System.out.println("CalculateLocalRankingTask: rilasciata lock in lettura sulle reviews");

                    releaseWriteLock(lockHotel);
                    System.out.println("CalculateLocalRankingTask: rilasciata lock in lettura sugli hotel");

                    releaseWriteLock(lockRanking);
                    System.out.println("CalculateLocalRankingTask: rilasciata lock in scrittura sulla classifica");





                }

                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    System.err.println(e.getMessage());
                    break;

                }
            }

            System.out.println("Thread che calcola ranking terminato");
        }
    }






}
