import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

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

        public boolean decrementAvailability(RoomType roomType) {
            int current = getAvailability(roomType);
            if (current <= 0) {
                return false;
            }
            availability.put(roomType, current - 1);
            return true;
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

    // Guest booking intent captured before any allocation is attempted.
    static class Reservation {
        private final String guestName;
        private final RoomType requestedRoomType;
        private final int nights;
        private String assignedRoomId;
        private boolean confirmed;

        public Reservation(String guestName, RoomType requestedRoomType, int nights) {
            this.guestName = guestName;
            this.requestedRoomType = requestedRoomType;
            this.nights = nights;
        }

        public String summary() {
            return "Guest: " + guestName + ", Room: " + requestedRoomType + ", Nights: " + nights;
        }

        public RoomType getRequestedRoomType() {
            return requestedRoomType;
        }

        public void markConfirmed(String assignedRoomId) {
            this.assignedRoomId = assignedRoomId;
            this.confirmed = true;
        }

        public String confirmationSummary() {
            if (!confirmed) {
                return summary() + ", Status: PENDING";
            }
            return summary() + ", Assigned Room ID: " + assignedRoomId + ", Status: CONFIRMED";
        }
    }

    // FCFS intake queue: collects requests and preserves arrival order.
    static class BookingRequestQueue {
        private final Queue<Reservation> requests = new LinkedList<>();

        public void submitRequest(Reservation reservation) {
            if (reservation == null) {
                return;
            }
            requests.offer(reservation);
        }

        public void printQueueInArrivalOrder() {
            if (requests.isEmpty()) {
                System.out.println("No pending booking requests.");
                return;
            }

            System.out.println("Pending Booking Requests (FCFS Order):");
            int position = 1;
            for (Reservation reservation : requests) {
                System.out.println(position + ". " + reservation.summary());
                position++;
            }
        }

        public int size() {
            return requests.size();
        }

        public Reservation dequeueRequest() {
            return requests.poll();
        }

        public boolean isEmpty() {
            return requests.isEmpty();
        }
    }

    // Handles safe allocation, uniqueness enforcement, and inventory synchronization.
    static class BookingService {
        private final Inventory inventory;
        private final Set<String> allocatedRoomIds = new HashSet<>();
        private final Map<RoomType, Set<String>> allocatedByRoomType = new HashMap<>();
        private final Map<RoomType, Integer> roomTypeCounters = new EnumMap<>(RoomType.class);

        public BookingService(Inventory inventory) {
            this.inventory = inventory;
            for (RoomType roomType : RoomType.values()) {
                allocatedByRoomType.put(roomType, new HashSet<>());
                roomTypeCounters.put(roomType, 0);
            }
        }

        public void processQueuedRequests(BookingRequestQueue requestQueue) {
            System.out.println("\nProcessing Booking Requests:\n");
            while (!requestQueue.isEmpty()) {
                Reservation request = requestQueue.dequeueRequest();
                if (request == null) {
                    continue;
                }
                allocateRoom(request);
            }
        }

        private void allocateRoom(Reservation reservation) {
            RoomType requestedRoomType = reservation.getRequestedRoomType();
            if (inventory.getAvailability(requestedRoomType) <= 0) {
                System.out.println("Unable to confirm -> " + reservation.summary() + ", Reason: Not available");
                return;
            }

            String roomId = generateUniqueRoomId(requestedRoomType);
            if (!inventory.decrementAvailability(requestedRoomType)) {
                System.out.println("Unable to confirm -> " + reservation.summary() + ", Reason: Availability changed");
                return;
            }

            allocatedRoomIds.add(roomId);
            allocatedByRoomType.get(requestedRoomType).add(roomId);
            reservation.markConfirmed(roomId);
            System.out.println("Confirmed -> " + reservation.confirmationSummary());
        }

        private String generateUniqueRoomId(RoomType roomType) {
            String roomId;
            do {
                int nextCounter = roomTypeCounters.get(roomType) + 1;
                roomTypeCounters.put(roomType, nextCounter);
                roomId = roomType.name() + "-" + String.format("%03d", nextCounter);
            } while (allocatedRoomIds.contains(roomId));
            return roomId;
        }

        public void printAllocationSummary() {
            System.out.println("\nAllocated Room IDs by Type:");
            for (RoomType roomType : RoomType.values()) {
                Set<String> allocatedIds = allocatedByRoomType.get(roomType);
                System.out.println(roomType + ": " + allocatedIds);
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

        System.out.println("\nGuest booking requests submitted:\n");
        BookingRequestQueue requestQueue = new BookingRequestQueue();
        requestQueue.submitRequest(new Reservation("Aarav", RoomType.SINGLE, 2));
        requestQueue.submitRequest(new Reservation("Meera", RoomType.DOUBLE, 1));
        requestQueue.submitRequest(new Reservation("Rohan", RoomType.SUITE, 3));
        requestQueue.submitRequest(new Reservation("Ishita", RoomType.SINGLE, 1));
        requestQueue.printQueueInArrivalOrder();

        Map<RoomType, Integer> inventoryAfterRequestIntake = inventory.getAvailabilitySnapshot();
        System.out.println("\nQueue size awaiting allocation: " + requestQueue.size());
        System.out.println("Inventory after request intake (unchanged): " + inventoryAfterRequestIntake);

        BookingService bookingService = new BookingService(inventory);
        bookingService.processQueuedRequests(requestQueue);
        bookingService.printAllocationSummary();
        System.out.println("\nInventory after allocation: " + inventory.getAvailabilitySnapshot());
    }
}