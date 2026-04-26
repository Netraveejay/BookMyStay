public class BookMyStay {

    // Abstract class representing a general Room
    static abstract class Room {
        protected int beds;
        protected int size;          // in sqft
        protected double pricePerNight;

        public Room(int beds, int size, double pricePerNight) {
            this.beds = beds;
            this.size = size;
            this.pricePerNight = pricePerNight;
        }

        // Method to display room details
        public void printDetails() {
            System.out.println("Beds: " + beds);
            System.out.println("Size: " + size + " sqft");
            System.out.println("Price per night: " + pricePerNight);
        }
    }

    // Concrete SingleRoom class
    static class SingleRoom extends Room {
        public SingleRoom() {
            super(1, 250, 1500.0);
        }
    }

    // Concrete DoubleRoom class
    static class DoubleRoom extends Room {
        public DoubleRoom() {
            super(2, 400, 2500.0);
        }
    }

    // Concrete SuiteRoom class
    static class SuiteRoom extends Room {
        public SuiteRoom() {
            super(3, 750, 5000.0);
        }
    }

    // Application entry point
    public static void main(String[] args) {
        // Static availability variables
        int singleRoomAvailable = 5;
        int doubleRoomAvailable = 3;
        int suiteRoomAvailable = 2;

        System.out.println("Hotel Room Initialization\n");

        // Create room objects
        Room single = new SingleRoom();
        Room doubleRoom = new DoubleRoom();
        Room suite = new SuiteRoom();

        // Display Single Room details
        System.out.println("Single Room:");
        single.printDetails();
        System.out.println("Available: " + singleRoomAvailable + "\n");

        // Display Double Room details
        System.out.println("Double Room:");
        doubleRoom.printDetails();
        System.out.println("Available: " + doubleRoomAvailable + "\n");

        // Display Suite Room details
        System.out.println("Suite Room:");
        suite.printDetails();
        System.out.println("Available: " + suiteRoomAvailable);
    }
}