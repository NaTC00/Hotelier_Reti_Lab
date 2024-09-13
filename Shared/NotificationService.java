package Shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotificationService extends Remote {

    /* registrazione per la callback */
    public String registerForCallback(String city,NotifyEventInterface clientInterface) throws RemoteException ;

    /* cancella registrazione per la callback */
    public String unregisterForCallback(String city, NotifyEventInterface clientInterface) throws RemoteException ;


}

