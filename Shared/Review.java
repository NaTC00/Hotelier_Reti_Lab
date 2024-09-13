package Shared;


public class Review {
    private String user;
    private int idHotel;
    private String nameHotel;
    private int globalscor;
    private String data;
    private Categories ratings;

    public Review(String user, int idHotel, String nameHotel, int globalscor, String data, Categories retings) {
        this.user = user;
        this.idHotel = idHotel;
        this.nameHotel = nameHotel;
        this.globalscor = globalscor;
        this.data = data;
        this.ratings = retings;

    }

    public String getUser() {
        return user;
    }

    public Categories getRatings() {
        return ratings;
    }
    public int getGlobalscor() {
        return globalscor;
    }

    public int getIdHotel() {
        return idHotel;
    }

    public String getNameHotel() {
        return nameHotel;
    }

    public String getData() {
        return data;
    }




}