package Shared;


import java.io.Serializable;

public class Categories implements Serializable {
    private static final long serialVersionUID = 1L;

    private double cleaning;
    private double position;
    private double services;
    private double quality;

    public Categories(double cleaning, double position, double services, double quality) {

        this.cleaning = cleaning;
        this.position = position;
        this.services = services;
        this.quality = quality;
    }

    public double getPosition() {
        return position;
    }

    public double getQuality() {
        return quality;
    }

    public double getCleaning() {
        return cleaning;
    }
    public double getServices() {
        return services;
    }

    public void setCleaning(double cleaning) {
        this.cleaning = cleaning;
    }

    public void setPosition(double position) {
        this.position = position;
    }

    public void setServices(double services) {
        this.services = services;
    }

    public void setQuality(double quality) {
        this.quality = quality;
    }


}