package Client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.*;

public class ClientMain {
    // Variabile di debug per abilitare/disabilitare la stampa di messaggi di log
    public static boolean DEBUG;

    // Oggetto HotelierClient usato per comunicare con il server dell'applicazione Hotelier
    public static HotelierClient hotelierClient;

    public static void main(String[] args) throws IOException {

        // Avvia il client. Se l'inizializzazione o il l'avvio falliscono, termina l'applicazione
        if (!builderAndStartClient() || restartHotelierClient()) {
            System.exit(1); // Esce con codice di errore
        }

        // Esegue il menu principale per l'interazione utente
        runMainMenu();
    }

    // Metodo che gestisce il ciclo del menu principale
    private static void runMainMenu() {
        Scanner scanner = new Scanner(System.in).useDelimiter("\n");
        boolean exit = false;

        while (!exit) {
            printMenuOptions(); // Stampa il menu di opzioni

            // Ottiene l'opzione valida dall'utente (numero compreso tra 1 e 11)
            int option = getValidOption(scanner, 1, 11);
            System.out.println("************************");
            switch (option) {
                case 1:
                    handleRegistration(scanner);
                    break;
                case 2:
                   handleLogin(scanner);
                    break;
                case 3:
                   handleLogout(scanner);
                    break;
                case 4:
                   handleHotelSearch(scanner);
                    break;
                case 5:
                    handleCityHotelsSearch(scanner);
                    break;
                case 6:
                    handleInsertReview(scanner);
                    break;
                case 7:
                   handleShowBadge();
                    break;
                case 8:
                    handleLocalRankingRegistration(scanner);
                    break;
                case 9:
                    handleLocalRankingUnregistration(scanner);
                    break;
                case 10:
                    if(handleExit()){
                        exit = true;
                    }

                    break;
                default:
                    System.out.println("Scelta non valida. Riprova.");
            }
        }
    }

    // Metodo che stampa le opzioni del menu principale
    private static void printMenuOptions() {
        System.out.println("************************");
        System.out.println("Benvenuto in HOTELIER");
        System.out.println("Scegli un'opzione:");
        System.out.println("1. Registrazione");
        System.out.println("2. Login");
        System.out.println("3. Logout");
        System.out.println("4. Cerca un hotel");
        System.out.println("5. Cerca gli hotel di una determinata città");
        System.out.println("6. Inserisci recensione hotel");
        System.out.println("7. Mostra il mio distintivo");
        System.out.println("8. Registrazione servizio di notifica");
        System.out.println("9. Deregistrazione servizio di notifica");
        System.out.println("10. Esci");
        System.out.println("Inserisci il numero corrispondente all'operazione che desideri eseguire: ");

    }

    // Metodo che ottiene un'opzione valida dall'utente
    // Richiede all'utente di inserire un numero tra min e max
    private static int getValidOption(Scanner scanner, int min, int max) {

        String input;
        int option;

        while (true) {
            try {
                input = scanner.next().trim();
                try {
                    option = Integer.parseInt(input);
                    if (option < min || option > max) {
                        System.out.println("L'opzione inserita non corrisponde ad alcuna operazione disponibile");
                    }else break;
                }catch (NumberFormatException e){
                    System.err.println("Per favore, inserisci un numero valido.");
                }
            }catch (Exception e){
                e.printStackTrace();
            }

        }
        return option;

    }

    private static void handleRegistration(Scanner scanner) {
        String username = getInput(scanner, "Inserisci Username");
        String password = getInput(scanner, "Inserisci Password");

        try {
            hotelierClient.registerUser(username, password);
        } catch (RemoteException e) {
            handleRemoteException(e);
        }
    }

    private static void handleLogin(Scanner scanner) {
        String username = getInput(scanner, "Inserisci Username");
        String password = getInput(scanner, "Inserisci Password");

        try {
            hotelierClient.login(username, password, scanner);
        } catch (RemoteException e) {
            handleRemoteException(e);
        } catch (Exception e) {
            handleGenericException(e);
        }
    }

    private static void handleLogout(Scanner scanner) {
        String username = getInput(scanner, "Inserisci Username");

        try {
            hotelierClient.logout(username);
        } catch (IOException e) {
            handleGenericException(e);
        }
    }

    private static void handleHotelSearch(Scanner scanner) {
        String nameHotel = getInput(scanner, "Inserisci il nome dell'hotel che vuoi cercare");
        String cityHotel = getInput(scanner, "Inserisci la città dove si trova l'hotel");

        try {
            hotelierClient.searchHotel(nameHotel, cityHotel);
        } catch (IOException e) {
            handleGenericException(e);
        }
    }

    private static void handleShowBadge() {
        try {
            hotelierClient.showMyBadge();
        } catch (IOException e) {
            handleGenericException(e);
        }
    }

    private static void handleCityHotelsSearch(Scanner scanner) {
        String cityHotel = getInput(scanner, "Inserisci una città");

        try {
            hotelierClient.searchAllHotels(cityHotel);
        } catch (IOException e) {
            handleGenericException(e);
        }
    }

    private static void handleInsertReview(Scanner scanner) {
        String nameHotel = getInput(scanner, "Inserisci il nome dell'hotel che vuoi recensire");
        String cityHotel = getInput(scanner, "Inserisci la città dove si trova l'hotel");
        int globalScore = getValidScore(scanner, "Inserisci un punteggio globale all'hotel");

        int[] categorie = new int[4];
        String[] nameCategorie = {"posizione", "pulizia", "servizio", "prezzo"};
        for (int i = 0; i < nameCategorie.length; i++) {
            categorie[i] = getValidScore(scanner, "Inserisci il punteggio per la categoria " + nameCategorie[i]);
        }

        try {
            hotelierClient.insertReview(nameHotel, cityHotel, globalScore, categorie);
        } catch (IOException e) {
            handleGenericException(e);
        }
    }

    public static void handleLocalRankingRegistration(Scanner scanner) {

        while (true){

            try {
                String city = getInput(scanner, "Inserisci le città per cui desideri ricevere aggiornamenti sul ranking o 'fine' per terminare");

                // Verifico se l'utente ha inserito "fine" per terminare
                if (city.trim().equalsIgnoreCase("fine")) {
                    System.out.println("Registrazione ai ranking locali completata.");
                    break;
                }

                hotelierClient.registerForLocalRanking(city);
            }catch (IOException e) {
                handleGenericException(e);
            }


        }

    }


    // Metodo che gestisce la deregistrazione dell'utente dal servizio di notifica per il ranking locale
    public static void handleLocalRankingUnregistration(Scanner scanner) {

        NotifyEventImpl notifyEvent = hotelierClient.getNotifyEventImpl();


        // Ciclo che continua finché l'utente non si deregistra da tutte le città o termina l'operazione
        while (true) {
            // Ottiene la lista delle città a cui l'utente è attualmente registrato per le notifiche
            List<String> registeredCities = new ArrayList<>(notifyEvent.getHotelList().keySet());

            // Se non ci sono città registrate, avvisa l'utente e interrompe il ciclo
            if (registeredCities.isEmpty()) {
                System.out.println("Non ci sono città a cui deregistrarsi dal servizio di notifica");
                break;
            }

            // Mostra all'utente la lista delle città a cui è registrato
            showRegisteredCities(registeredCities);

            // Chiede all'utente di inserire un'opzione per scegliere la città da cui deregistrarsi
            String choice = getInput(scanner, "Seleziona il numero corrispondente alla città da cui vuoi deregistrarti:");

            // Elabora la scelta dell'utente. Se l'utente sceglie di chiudere l'operazione o si deregistra da tutte le città, esce dal ciclo
            if (processUserChoice(choice, registeredCities)) {
                break; // Esce dal ciclo se l'utente ha terminato l'operazione
            }
        }
    }


    // Mostra la lista delle città registrate e le opzioni speciali (Tutte, Chiudi)
    private static void showRegisteredCities(List<String> registeredCities) {
        System.out.println("Sei registrato alle seguenti città:");
        for (int i = 0; i < registeredCities.size(); i++) {
            System.out.println((i + 1) + ". " + registeredCities.get(i));
        }
        System.out.println((registeredCities.size() + 1) + ". Tutte");
        System.out.println((registeredCities.size() + 2) + ". Chiudi");
    }

    // Metodo che elabora la scelta dell'utente e restituisce true se l'operazione dovrebbe terminare
    private static boolean processUserChoice(String choice, List<String> registeredCities) {
        try {
            // Converte la scelta dell'utente in un numero intero
            int option = Integer.parseInt(choice);

            // Se l'utente sceglie l'opzione per deregistrarsi da tutte le città
            if (option == registeredCities.size() + 1) {
                // Deregistra l'utente da tutte le città
                hotelierClient.deregisterAllCities(registeredCities);
                return false; // Non termina l'operazione qui, poiché potrebbe esserci un'altra fase successiva
            }
            // Se l'utente sceglie di chiudere l'operazione
            else if (option == registeredCities.size() + 2) {
                System.out.println("Operazione di deregistrazione chiusa.");
                return true; // Termina l'operazione e restituisce true per uscire dal ciclo
            }
            // Se l'utente sceglie una città valida da cui deregistrarsi
            else if (option > 0 && option <= registeredCities.size()) {
                // Ottiene il nome della città selezionata dall'utente
                String city = registeredCities.get(option - 1);
                // Deregistra l'utente dalla città selezionata
                hotelierClient.deregisterCity(city);
                return false; // Continua l'operazione, in quanto potrebbero esserci altre città
            }
            // Se l'opzione inserita non è valida
            else {
                System.out.println("Opzione non valida. Riprova.");
            }
        } catch (NumberFormatException e) {
            // Gestisce l'errore nel caso l'input non sia un numero valido
            System.out.println("Inserisci un numero valido.");
        } catch (RemoteException ex) {
            // Gestisce eventuali eccezioni RemoteException dovute a problemi di comunicazione remota
            handleRemoteException(ex);
        }

        return false; // Continua il loop se l'utente non ha scelto di terminare l'operazione
    }
    private static boolean handleExit() {
        try {
            hotelierClient.closeFullConnection();
            return true;
        } catch (Exception e) {
            if (DEBUG) System.err.println("Errore: " + e.getMessage());
            System.out.println("Si è verificato un problema durante la chiusura della connessione.");
            return false;
        }
    }

    public static String getInput(Scanner scanner, String prompt) {
        String input;
        do {
            System.out.println(prompt);
            input = scanner.next().trim();
        } while (input.isEmpty());
        return input;
    }

    private static int getValidScore(Scanner scanner, String prompt) {

        String input;
        int score;
        while (true){
            System.out.println(prompt);
            input = scanner.next().trim();
            try {
                score = Integer.parseInt(input);
                break;
            }catch (NumberFormatException e){
                System.err.println("Per favore, inserisci un numero valido.");
            }

        }
        return score;

    }

    private static void handleRemoteException(RemoteException e) {
        if (DEBUG) System.out.println("Errore: " + e.getMessage());
        System.out.println("Si è verificato un problema di connessione con il server. Per favore, riprova più tardi.");

    }

    private static void handleGenericException(Exception e) {
        if (DEBUG) System.err.println("Errore: " + e.getMessage());
        System.out.println("Si è verificato un problema. Per favore, riprova più tardi.");
        try {
            hotelierClient.stopAndCloseSocket();
        } catch (IOException | NotBoundException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        if (restartHotelierClient())  System.exit(1);
    }


    public static boolean builderAndStartClient() {
        try {
            File config = new File(System.getProperty("user.dir"), "config_client.properties");
            FileInputStream fis = new FileInputStream(config);
            Properties prop = new Properties();
            prop.load(fis);

            DEBUG = Boolean.parseBoolean(prop.getProperty("debug"));
            hotelierClient = new HotelierClient(
                    prop.getProperty("multicast_address"),
                    Integer.parseInt(prop.getProperty("multicast_port")),
                    Integer.parseInt(prop.getProperty("registry_port")),
                    Integer.parseInt(prop.getProperty("callback_obj")),
                    Integer.parseInt(prop.getProperty("server_port_tcp")),
                    Integer.parseInt(prop.getProperty("P_number")),
                    Integer.parseInt(prop.getProperty("G_generator")),
                    null, null, null, null,
                    null, null, null,
                    null, null
            );
            return true;
        } catch (IOException e) {
            if (DEBUG) System.err.println("Errore: " + e.getMessage());
            System.out.println("Errore nella lettura del file di configurazione.");
            return false;
        }
    }

    /**
     * Tenta di riavviare il client dell'Hotelier. Se la connessione remota fallisce,
     * permette all'utente di riprovare o uscire dal programma.
     *
     * @return true se l'utente decide di uscire dal programma, false altrimenti.
     */
    public static boolean restartHotelierClient() {
        // Crea uno scanner per leggere l'input dell'utente
        Scanner scanner = new Scanner(System.in).useDelimiter("\n");
        String input = "";

        while (!input.trim().equalsIgnoreCase("ESCI")) {
            try {
                // Tentativo di avviare la connessione del client
                System.out.println("Tentativo di connessione...");
                hotelierClient.start();  // Avvia il client

                // Se la connessione è riuscita, informa l'utente e interrompe il ciclo
                System.out.println("Connessione riuscita. Ora puoi scegliere l'operazione da effettuare.");
                break;

            } catch (Exception e) {
                // Gestisce eccezioni durante il tentativo di connessione
                if (DEBUG) {
                    System.err.println("Errore di connessione: " + e.getMessage());
                }
                System.out.println("Errore durante la connessione remota. Riprova più tardi.");

                // Deregistra il servizio di notifica se c'è stato un errore
               // hotelierClient.unregisterNotificationService();
            }

            // Chiede all'utente se desidera riprovare o uscire
            System.out.println("Inserisci 'RIPROVA' per tentare di nuovo o 'ESCI' per uscire:");
            input = scanner.next();  // Legge l'input dell'utente
        }

        // Se l'utente ha scelto di uscire, informa e restituisce true
        if (input.trim().equalsIgnoreCase("ESCI")) {
            System.out.println("Uscita dal programma.");
            return true;
        }

        // Se il ciclo è terminato senza uscita, restituisce false
        return false;
    }

}