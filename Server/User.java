package Server;

import java.util.concurrent.locks.ReadWriteLock;

public class User {

    //username è univoco
    private String username;

    //la password non può essere una stringa vuota
    private String password;

    private int numReviews;

    private String badges;



    public User(String username, String password, int numReviews, String badges) {

       this.username = username;
       this.password = password;
       this.numReviews = numReviews;
       this.badges = badges;

       updateBadge();
    }



    public String getUsername() {
        return username;
    }

    public String getPassword() { return password;
    }

    public int getNumReview() {
        return numReviews;
    }

    public String getBadges() {
        return badges;
    }



    public void incrementNumReview() {

        numReviews++;
        updateBadge();


    }

    private void updateBadge() {
        if (numReviews >= 20) {
            badges = "Contributore Super";
        } else if (numReviews >= 15) {
            badges = "Contributore Esperto";
        } else if (numReviews >= 10) {
            badges = "Contributore";
        } else if (numReviews >= 5) {
            badges = "Recensore Esperto";
        } else if (numReviews >= 1) {
            badges = "Recensore";
        }
    }
}
