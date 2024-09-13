package Client;

import Shared.Categories;
import Shared.NotifyEventInterface;
import Shared.Hotel;
import com.google.gson.Gson;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class NotifyEventImpl extends RemoteObject implements NotifyEventInterface {
    private ConcurrentHashMap<String, List<Hotel>> hotelList;


    public NotifyEventImpl(ConcurrentHashMap<String, List<Hotel>> hotelList) throws RemoteException {
        super();
        this.hotelList = hotelList;
    }



    public ConcurrentHashMap<String, List<Hotel>> getHotelList() {
        return hotelList;
    }

    public void addNewCity(String city) throws RemoteException {
        hotelList.putIfAbsent(city, new ArrayList<>());
    }

    public void removeHotelsInCity(String city) throws RemoteException {
        hotelList.remove(city);
    }

    @Override
    public void notifyEvent(List<Hotel> hotels, String city) throws RemoteException {
        System.out.println("Classifica aggiornata per la città: " + city);


        // Itera attraverso la lista di hotel
        for (int i = 0; i < hotels.size(); i++) {
            Hotel hotel = hotels.get(i);

            // Stampa la posizione dell'hotel nella lista (classifica)
            System.out.println("Posizione #" + (i + 1));

            // Stampa le informazioni dell'hotel
            System.out.println("Name: " + hotel.getName());
            System.out.println("Rate: " + hotel.getRate());

            // Ottieni le categorie di ratings
            Categories ratings = hotel.getRatings();

            // Stampa i ratings per ogni categoria
            System.out.println("Ratings:");
            System.out.println("  Cleaning: " + ratings.getCleaning());
            System.out.println("  Position: " + ratings.getPosition());
            System.out.println("  Services: " + ratings.getServices());
            System.out.println("  Quality: " + ratings.getQuality());

            // Stampa una linea divisoria tra un hotel e l'altro
            if (i < hotels.size() - 1) {
                System.out.println("------------------------");
            }
        }

        // Aggiorna la lista degli hotel nella mappa hotelList
        hotelList.put(city, hotels);

        System.out.println("Inserisci il numero corrispondente all'operazione che desideri eseguire: ");

    }

}
