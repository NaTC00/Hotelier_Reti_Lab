package Client;

import java.util.HashMap;
import java.util.Map;

/**
 * La classe Request rappresenta una richiesta generica con un'operazione
 * e una serie di parametri associati. Questa classe viene utilizzata per
 * gestire le richieste in modo flessibile, permettendo di specificare
 * un'operazione e i relativi parametri dinamici.
 */
public class Request {

    // Il nome dell'operazione che si desidera eseguire
    private String operation;

    // Mappa che contiene i parametri della richiesta, con chiavi come nomi dei parametri e valori associati
    private Map<String, Object> param;


    public Request() {
        param = new HashMap<>();
    }

    /**
     * Imposta l'operazione della richiesta.
     *
     * @param operation Il nome dell'operazione che deve essere eseguita.
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    /**
     * Restituisce il nome dell'operazione corrente della richiesta.
     *
     * @return Il nome dell'operazione.
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Aggiunge un parametro alla richiesta.
     * Ogni parametro viene memorizzato come una coppia chiave-valore all'interno della mappa.
     *
     * @param key   Il nome del parametro.
     * @param value Il valore associato al parametro.
     */
    public void addParam(String key, Object value) {
        param.put(key, value);
    }

    /**
     * Restituisce tutti i parametri della richiesta sotto forma di mappa.
     * La mappa contiene coppie chiave-valore che rappresentano i parametri della richiesta.
     *
     * @return Una mappa contenente i parametri della richiesta.
     */
    public Map<String, Object> getParams() {
        return param;
    }
}


