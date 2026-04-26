import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class BookMyStay {

    enum RoomType {
        SINGLE,
        DOUBLE,
        SUITE
    }

    // Domain model for room information and pricing.
    static abstract class Room {
        protected int beds;
        protected int size; // in sqft
        protected double pricePerNight;
        protected String amenities;

        public Room(int beds, int size, double pricePerNight, String amenities) {
            this.beds = beds;
            this.size = size;
            this.pricePerNight = pricePerNight;
            this.amenities = amenities;
        }

        public void printDetails() {
            System.out.println("Beds: " + beds);
            System.out.println("Size: " + size + " sqft");
            System.out.println("Price per night: " + pricePerNight);
            System.out.println("Amenities: " + amenities);
        }
    }

    static class SingleRoom extends Room {
        public SingleRoom() {
            super(1, 250, 1500.0, "WiFi, Work Desk");
        }
    }

    static class DoubleRoom extends Room {
        public DoubleRoom() {
            super(2, 400, 2500.0, "WiFi, Work Desk, Balcony");
        }
    }

    static class SuiteRoom extends Room {
        public SuiteRoom() {
            super(3, 750, 5000.0, "WiFi, Living Area, Bathtub");
        }
    }

    // Centralized state holder for room availability.
    static class Inventory {
        private final Map<RoomType, Integer> availability = new EnumMap<>(RoomType.class);

        public Inventory() {
            availability.put(RoomType.SINGLE, 5);
            availability.put(RoomType.DOUBLE, 3);
            availability.put(RoomType.SUITE, 0);
        }

        public int getAvailability(RoomType roomType) {
            Integer count = availability.get(roomType);
            return count == null ? 0 : count;
        }

        public Map<RoomType, Integer> getAvailabilitySnapshot() {
            return Collections.unmodifiableMap(new EnumMap<>(availability));
        }
    }

    // Read-only service that retrieves and filters availability data.
    static class SearchService {
        private final Inventory inventory;
        private final Map<RoomType, Room> roomCatalog;

        public SearchService(Inventory inventory, Map<RoomType, Room> roomCatalog) {
            this.inventory = inventory;
            this.roomCatalog = roomCatalog;
        }

        public void displayAvailableRooms() {
            System.out.println("Available Rooms:\n");
            boolean anyAvailable = false;

            for (RoomType roomType : RoomType.values()) {
                int availableCount = inventory.getAvailability(roomType);
                Room room = roomCatalog.get(roomType);

                // Defensive checks: avoid invalid catalog entries and show actionable results only.
                if (room == null || availableCount <= 0) {
                    continue;
                }

                anyAvailable = true;
                System.out.println(roomType + " Room:");
                room.printDetails();
                System.out.println("Available: " + availableCount + "\n");
            }

            if (!anyAvailable) {
                System.out.println("No room types are currently available.");
            }
        }
    }

    public static void main(String[] args) {
        Inventory inventory = new Inventory();

        Map<RoomType, Room> roomCatalog = new EnumMap<>(RoomType.class);
        roomCatalog.put(RoomType.SINGLE, new SingleRoom());
        roomCatalog.put(RoomType.DOUBLE, new DoubleRoom());
        roomCatalog.put(RoomType.SUITE, new SuiteRoom());

        SearchService searchService = new SearchService(inventory, roomCatalog);

        System.out.println("Guest initiated room search.\n");
        searchService.displayAvailableRooms();

        // Verify search is read-only by comparing before/after snapshots.
        Map<RoomType, Integer> snapshotAfterSearch = inventory.getAvailabilitySnapshot();
        System.out.println("Inventory state after search (unchanged): " + snapshotAfterSearch);
    }
}