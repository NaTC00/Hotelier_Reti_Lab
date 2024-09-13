package Shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface NotifyEventInterface extends Remote {
    /*
     * Metodo invocato dal server per notificare un evento ad un client remoto.
     */
    public void notifyEvent(List<Hotel> hotels, String city) throws RemoteException;
}
