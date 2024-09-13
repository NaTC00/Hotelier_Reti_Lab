package Server;

import Shared.RegistrationService;
import Shared.Response;
import Shared.SecurityClass;
import com.google.gson.Gson;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Classe RegistrationServiceImpl che estende RemoteServer e implementa RegistrationService.
 * Gestisce la registrazione degli utenti.
 */
public class RegistrationServiceImpl extends RemoteServer implements RegistrationService {

    // Mappa contenente gli utenti registrati
    private ConcurrentHashMap<String, User> registredUserDB;

    // Mappa contenente le chiavi di decrittazione
    private ConcurrentHashMap<String, String> decryptionKeys;

    // Lock per gestire l'accesso concorrente agli utenti registrati
    private ReadWriteLock lockUser;

    /**
     * Costruttore della classe RegistrationServiceImpl.
     *
     * @param registredUserDB ConcurrentHashMap contenente gli utenti registrati
     * @param decryptionKeys  ConcurrentHashMap contenente le chiavi di decrittazione
     * @param lockUser  ReadWriteLock per gestire l'accesso concorrente agli utenti registrati
     */
    public RegistrationServiceImpl(ConcurrentHashMap<String, User> registredUserDB, ConcurrentHashMap<String, String> decryptionKeys, ReadWriteLock lockUser) {
        this.registredUserDB = registredUserDB;
        this.decryptionKeys = decryptionKeys;
        this.lockUser = lockUser;
    }

    /**
     * Metodo addRegistration che aggiunge un nuovo utente al database.
     *
     * Questo metodo gestisce la registrazione di un nuovo utente, assicurandosi che quel username non sia già utilizzato.
     * Utilizza la lock per garantire la sincronizzazione durante l'accesso al database degli utenti.
     *
     * @param username Nome utente da registrare.
     * @param password Password cifrata dell'utente.
     * @param uuid UUID associato alla chiave di decrittazione.
     * @return Stringa JSON contenente la risposta, con codice di stato e messaggio.
     * @throws RemoteException Eccezione remota in caso di errori di comunicazione.
     */
    @Override
    public String addRegistration(String username, byte[] password, String uuid) throws RemoteException {
        Gson gson = new Gson();
        // Controlla che username e password non siano null e non siano vuoti
        if (username == null || password == null || username.isEmpty() || password.length == 0) {
            String errorMessage = "Credenziali vuote";
            System.err.println("Registrazione fallita: " + errorMessage);
            return gson.toJson(new Response(400, new Response.Message(null, errorMessage)));
        }

        // Recupera la chiave per decifrare la password
        String keydecryption = decryptionKeys.get(uuid);
        if (keydecryption == null) {
            String errorMessage = "Chiave di decrittazione non trovata per UUID: " + uuid;
            System.err.println("Registrazione fallita: " + errorMessage);
            return gson.toJson(new Response(404, new Response.Message(null, errorMessage)));
        }

        // Decifra la password
        String passwordDecryption = SecurityClass.decrypt(password, keydecryption);
        if (passwordDecryption == null || passwordDecryption.isEmpty()) {
            String errorMessage = "Password decifrata vuota";
            System.err.println("Registrazione fallita: " + errorMessage);
            return gson.toJson(new Response(400, new Response.Message(null, errorMessage)));
        }

        // Crea un nuovo utente
        User user = new User(username, passwordDecryption, 0, null);
        try {
            System.out.println("Tentativo di registrazione per l'utente: " + username);
            // Acquisisce il lock per la scrittura per garantire la sincronizzazione
            System.out.println("addRegistration: tento di acquisire la lock in scrittura sugli utenti");
            HotelierServer.acquireWriteLock(lockUser);
            System.out.println("addRegistration: acquisita la lock in scrittura sugli utenti");

            // Aggiunge il nuovo utente al database se non esiste già
            if (registredUserDB.putIfAbsent(username, user) != null) {
                String errorMessage = "Username già utilizzato: " + username;
                System.err.println("Registrazione fallita: " + errorMessage);
                return gson.toJson(new Response(401, new Response.Message(null, errorMessage)));
            }

            // Log dell'utente registrato con successo
            System.out.println("Registrato un nuovo utente: " + username);
            System.out.println("Registrazione completata con successo per l'utente: " + username);

            // Restituisce una risposta positiva
            return gson.toJson(new Response(200, new Response.Message(null, "Utente registrato")));
        }finally {
            // Rilascia il lock per la scrittura
            HotelierServer.releaseWriteLock(lockUser);
            System.out.println("addRegistration: rilascita la lock in scrittura sugli utenti");
        }

    }

}

