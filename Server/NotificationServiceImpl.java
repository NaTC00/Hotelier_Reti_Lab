package Server;

import Shared.*;
import com.google.gson.Gson;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;

public class NotificationServiceImpl extends RemoteObject implements NotificationService {

    // Mappa contenente le città e gli hotel associati
    private HashMap<String, HashMap<String, Hotel>> hotelDB;

    // Mappa contenente le callback registrate per ciascun hotel
    private HashMap<String, List<NotifyEventInterface>> hotelCallbacks;

    //Lock per gestire l'accesso concorrente alla lista delle callback
    private ReadWriteLock lockHotelCallback;

    /**
     * Costruttore della classe NotificationServiceImpl.
     *
     * @param hotelDB        Mappa contenente le città e gli hotel associati
     * @param hotelCallbacks Mappa contenente le callback registrate per ciascun hotel
     * @param lockHotelCallback Lock per gestire l'accesso concorrente alla lista delle callback
     */
    public NotificationServiceImpl(HashMap<String, HashMap<String, Hotel>> hotelDB,
                                   HashMap<String, List<NotifyEventInterface>> hotelCallbacks, ReadWriteLock lockHotelCallback) throws RemoteException {
        super();
        this.hotelDB = hotelDB;
        this.hotelCallbacks = hotelCallbacks;
        this.lockHotelCallback = lockHotelCallback;
    }

    /**
     * Registra un client per un hotel specifico.
     *
     * @param city            La città in cui si trova l'hotel
     * @param clientInterface Stub dell'oggetto remoto del client
     * @return Una stringa JSON che rappresenta il risultato della registrazione
     * @throws RemoteException Se si verifica un errore durante la registrazione
     */
    public String registerForCallback(String city, NotifyEventInterface clientInterface) throws RemoteException {
        Gson gson = new Gson();
        // Input validation
        if (city == null  || city.isEmpty()) {
            return gson.toJson(new Response(400, new Response.Message(null, "Input nullo")));

        }
        // Verifica se la città esiste nel database

        if (hotelDB.containsKey(city)) {


            try {
                System.out.println("registerForCallback: tento di acquisire la lock in scrittura sulle callbacks");
                HotelierServer.acquireWriteLock(lockHotelCallback);
                System.out.println("registerForCallback: acquisita la lock in scrittura sulle callback");

                List<NotifyEventInterface> callbacks = hotelCallbacks.computeIfAbsent(city, k -> new ArrayList<>());

                // Aggiungi lo stub dell'oggetto remoto del client alla lista degli stub dei client solo se non è ancora registrato
                if(!callbacks.contains(clientInterface)){
                    callbacks.add(clientInterface);
                    return gson.toJson(new Response(200, new Response.Message(null, "Registrazione per la citta '" + city + "' eseguita con successo")));
                }else{
                    return gson.toJson(new Response(403, new Response.Message(null, "Registrazione per la citta '" + city + "' già effettuata")));
                }
                

                
            }finally {

                HotelierServer.releaseWriteLock(lockHotelCallback);
                System.out.println("registerForCallback: rilasciata la lock in scrittura sulle callback");


            }

        } else {
            // Città non trovata
            return gson.toJson(new Response(404, new Response.Message(null, "Errore nella registrazione per la citta '" + city + "': non ci sono hotel nella citta cercata")));
        }
    }


    /**
     * Deregistra un client dal servizio di callback.
     * Questo metodo rimuove il client specificato dalla lista di callback per la città
     * specificata.
     *
     * La deregistrazione è gestita in modo thread-safe utilizzando un lock in scrittura
     * per garantire che la mappa delle callback non venga modificata da altri thread
     * durante l'operazione.
     * @param city            città da cui l'utente vuole deregistrarsi
     * @param clientInterface Stub dell'oggetto remoto del client da rimuovere dalla lista degli stub degli oggetti remoti dei client
     * @throws RemoteException Se si verifica un errore nella comunicazione remota
     */
    @Override
    public String unregisterForCallback(String city, NotifyEventInterface clientInterface) throws RemoteException {
        Gson gson = new Gson();

        // Verifica che il clientInterface non sia nullo
        if (clientInterface == null) {
            return gson.toJson(new Response(400, new Response.Message(null, "Input nullo")));
        }

        // Verifica che il nome della città non sia nullo o vuoto
        if (city == null || city.isEmpty()) {
            return gson.toJson(new Response(400, new Response.Message(null, "Nome della città non valido")));
        }

        try {
            // Acquisizione del lock in scrittura per garantire esclusività nell'accesso alla mappa delle callback
            System.out.println("unregisterForCallback: tento di acquisire la lock in scrittura sulle callbacks");
            HotelierServer.acquireWriteLock(lockHotelCallback);
            System.out.println("unregisterForCallback: acquisita la lock in scrittura sulle callback");

            // Converti il nome della città in minuscolo per uniformità
            city = city.toLowerCase();

            // Ottieni la lista delle callback per la città specificata
            List<NotifyEventInterface> callbacks = hotelCallbacks.get(city);

            // Verifica se esiste una lista di callback per la città specificata
            if (callbacks != null && callbacks.remove(clientInterface)) {
                System.out.println("unregisterForCallback: Callback rimossa per la città " + city);
                // Ritorna una risposta di successo se la deregistrazione è avvenuta
                return gson.toJson(new Response(200, new Response.Message(null, "Deregistrazione dal servizio di callback per la città " + city + " effettuata")));
            } else {
                // Ritorna una risposta di errore se la callback non è stata trovata nella città specificata
                return gson.toJson(new Response(404, new Response.Message(null, "Callback non trovata per la città " + city)));
            }

        } finally {
            // Rilascio del lock in scrittura dopo aver completato l'operazione
            HotelierServer.releaseWriteLock(lockHotelCallback);
            System.out.println("unregisterForCallback: rilasciata la lock in scrittura sulle callback");
        }
    }



}
