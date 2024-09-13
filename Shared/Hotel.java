package Shared;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class Hotel implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    private String description;
    private String city;
    private String phone;
    private List<String> services;
    private double rate;
    private Categories ratings;


    public Hotel(int id, String name, String description, String city, String phone, List<String> services, double rate, Categories ratings) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.city = city;
        this.phone = phone;
        this.services = services;
        this.rate = rate;
        this.ratings = ratings;

    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCity() {
        return city;
    }

    public String getPhone() {
        return phone;
    }

    public List<String> getServices() {
        return services;
    }

    public double getRate() {
        return rate;
    }

    public Categories getRatings() {
        return ratings;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public void calcolaRanking(List<Review> reviewsHotel) {

        if (reviewsHotel== null || reviewsHotel.isEmpty())
        {
            return;
        }
        double totalRate = 0.0;
        double quantityWeight;

        double positionRate = 0.0;
        double cleaningRate = 0.0;
        double servicesRate = 0.0;
        double qualityRate = 0.0;
        LocalDateTime now = LocalDateTime.now();

        for (Review review : reviewsHotel) {

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate reviewDate = LocalDate.parse(review.getData(), formatter);
            long daysBetween = ChronoUnit.DAYS.between(reviewDate, now);

            double yearsBetween = daysBetween / 365.0; // Calcola il numero di anni trascorsi
            double recencyWeight = 1.0 - (0.05 * Math.floor(yearsBetween)); // L'attualità scende di 0.05 ogni anno

            totalRate += recencyWeight * review.getGlobalscor(); //al punteggio applico il peso calcolato
            cleaningRate += recencyWeight * review.getRatings().getCleaning();
            positionRate += recencyWeight * review.getRatings().getPosition();
            servicesRate += recencyWeight * review.getRatings().getServices();
            qualityRate += recencyWeight * review.getRatings().getQuality();
        }

        quantityWeight = 0.3 - (0.02 * (reviewsHotel.size() -1));

        // Dopo 15 recensioni, si presume che l'utente si concentri solo sulla qualità e sull'attualità della recensione.
        //Il peso della quantità (quante recensioni ci sono) diventa meno importante rispetto alla qualità (quanto sono buone le recensioni) e all'attualità (quanto sono recenti le recensioni).
        if (quantityWeight < 0) quantityWeight = 0;

        this.rate =  (totalRate /reviewsHotel.size()) - quantityWeight;
        this.ratings.setCleaning((cleaningRate /reviewsHotel.size()) - quantityWeight);
        this.ratings.setPosition((positionRate /reviewsHotel.size()) - quantityWeight);
        this.ratings.setServices( (servicesRate /reviewsHotel.size()) - quantityWeight);
        this.ratings.setQuality((qualityRate /reviewsHotel.size()) - quantityWeight);


    }




}


