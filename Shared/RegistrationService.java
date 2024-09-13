package Shared;
/**
* Shared.RegistrationService è un’interfaccia che rappresenta un servizio di registrazione utenti.
* Questa interfaccia definisce una serie di metodi che devono essere implementati.
* L’interfaccia estende Remote, il che significa che gli oggetti che implementano questa interfaccia
*  possono avere i loro metodi chiamati da remoto attraverso Java RMI
 */

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RegistrationService extends Remote {

    /**
     *
     * @param username
     * @param password
     * @return true se la registrazione è avvuta con successo altrimenti fals
     * @throws RemoteException
     * @effects aggiunge al database delle registrazioni un nuovo utente registrato con le proprie credenziali
     */
    String addRegistration(String username, byte[] password, String uuid) throws RemoteException;
}
