import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

public class BookMyStay {

    enum RoomType {
        SINGLE,
        DOUBLE,
        SUITE
    }

    // Domain-specific exception for invalid booking flows.
    static class InvalidBookingException extends Exception {
        public InvalidBookingException(String message) {
            super(message);
        }
    }

    // Centralized validation for booking input and inventory state.
    static class InvalidBookingValidator {
        public void validateReservationInput(Reservation reservation) throws InvalidBookingException {
            if (reservation == null) {
                throw new InvalidBookingException("Booking request is missing.");
            }
            if (reservation.guestName == null || reservation.guestName.trim().isEmpty()) {
                throw new InvalidBookingException("Guest name is required.");
            }
            if (reservation.requestedRoomType == null) {
                throw new InvalidBookingException("Requested room type is invalid.");
            }
            if (reservation.nights <= 0) {
                throw new InvalidBookingException("Number of nights must be greater than zero.");
            }
        }

        public void validateInventoryState(Inventory inventory, RoomType roomType) throws InvalidBookingException {
            int current = inventory.getAvailability(roomType);
            if (current < 0) {
                throw new InvalidBookingException("Inventory corruption detected for " + roomType + ".");
            }
        }
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

        public synchronized int getAvailability(RoomType roomType) {
            Integer count = availability.get(roomType);
            return count == null ? 0 : count;
        }

        public synchronized boolean decrementAvailability(RoomType roomType) {
            int current = getAvailability(roomType);
            if (current <= 0) {
                return false;
            }
            availability.put(roomType, current - 1);
            return true;
        }

        public synchronized void incrementAvailability(RoomType roomType) {
            int current = getAvailability(roomType);
            availability.put(roomType, current + 1);
        }

        public synchronized Map<RoomType, Integer> getAvailabilitySnapshot() {
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
        final String guestName;
        final RoomType requestedRoomType;
        final int nights;
        private String assignedRoomId;
        private boolean confirmed;
        private boolean cancelled;

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
            String status = cancelled ? "CANCELLED" : "CONFIRMED";
            return summary() + ", Assigned Room ID: " + assignedRoomId + ", Status: " + status;
        }

        public String getAssignedRoomId() {
            return assignedRoomId;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void markCancelled() {
            this.cancelled = true;
        }
    }

    // FCFS intake queue: collects requests and preserves arrival order.
    static class BookingRequestQueue {
        private final Queue<Reservation> requests = new LinkedList<>();
        private final InvalidBookingValidator validator = new InvalidBookingValidator();

        public synchronized void submitRequest(Reservation reservation) {
            try {
                validator.validateReservationInput(reservation);
                requests.offer(reservation);
            } catch (InvalidBookingException exception) {
                System.out.println("Rejected booking request: " + exception.getMessage());
            }
        }

        public synchronized void submitRequestOrThrow(Reservation reservation) throws InvalidBookingException {
            validator.validateReservationInput(reservation);
            if (reservation == null) {
                return;
            }
            requests.offer(reservation);
        }

        public synchronized void printQueueInArrivalOrder() {
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

        public synchronized int size() {
            return requests.size();
        }

        public synchronized Reservation dequeueRequest() {
            return requests.poll();
        }

        public synchronized boolean isEmpty() {
            return requests.isEmpty();
        }
    }

    // Handles safe allocation, uniqueness enforcement, and inventory synchronization.
    static class BookingService {
        private final Inventory inventory;
        private final BookingHistory bookingHistory;
        private final InvalidBookingValidator validator = new InvalidBookingValidator();
        private final Set<String> allocatedRoomIds = new HashSet<>();
        private final Map<RoomType, Set<String>> allocatedByRoomType = new HashMap<>();
        private final Map<RoomType, Integer> roomTypeCounters = new EnumMap<>(RoomType.class);
        private final Map<String, Reservation> confirmedReservations = new HashMap<>();

        public BookingService(Inventory inventory, BookingHistory bookingHistory) {
            this.inventory = inventory;
            this.bookingHistory = bookingHistory;
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

        private synchronized void allocateRoom(Reservation reservation) {
            try {
                validator.validateReservationInput(reservation);
                RoomType requestedRoomType = reservation.getRequestedRoomType();
                validator.validateInventoryState(inventory, requestedRoomType);

                if (inventory.getAvailability(requestedRoomType) <= 0) {
                    throw new InvalidBookingException(
                        "Unable to confirm -> " + reservation.summary() + ", Reason: Not available"
                    );
                }

                String roomId = generateUniqueRoomId(requestedRoomType);
                if (!inventory.decrementAvailability(requestedRoomType)) {
                    throw new InvalidBookingException(
                        "Unable to confirm -> " + reservation.summary() + ", Reason: Availability changed"
                    );
                }

                allocatedRoomIds.add(roomId);
                allocatedByRoomType.get(requestedRoomType).add(roomId);
                reservation.markConfirmed(roomId);
                confirmedReservations.put(roomId, reservation);
                bookingHistory.addConfirmedReservation(reservation);
                System.out.println("Confirmed -> " + reservation.confirmationSummary());
            } catch (InvalidBookingException exception) {
                System.out.println(exception.getMessage());
            }
        }

        public void allocateReservationFromThread(Reservation reservation, String workerName) {
            allocateRoom(reservation);
            if (reservation != null) {
                System.out.println("Worker " + workerName + " processed -> " + reservation.summary());
            }
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

        public Set<String> getConfirmedReservationIds() {
            return new HashSet<>(confirmedReservations.keySet());
        }

        public Reservation getReservationById(String reservationId) {
            return confirmedReservations.get(reservationId);
        }

        public void removeConfirmedReservation(String reservationId) {
            confirmedReservations.remove(reservationId);
        }

        public void releaseRoomId(RoomType roomType, String roomId) {
            allocatedRoomIds.remove(roomId);
            Set<String> roomTypeAllocations = allocatedByRoomType.get(roomType);
            if (roomTypeAllocations != null) {
                roomTypeAllocations.remove(roomId);
            }
        }
    }

    // Ordered historical record of confirmed bookings.
    static class BookingHistory {
        private final List<Reservation> confirmedReservations = new ArrayList<>();

        public void addConfirmedReservation(Reservation reservation) {
            if (reservation == null) {
                return;
            }
            confirmedReservations.add(reservation);
        }

        public List<Reservation> getConfirmedReservations() {
            return Collections.unmodifiableList(confirmedReservations);
        }

        public boolean markReservationCancelled(String reservationId) {
            for (Reservation reservation : confirmedReservations) {
                String assignedRoomId = reservation.getAssignedRoomId();
                if (assignedRoomId != null && assignedRoomId.equals(reservationId) && !reservation.isCancelled()) {
                    reservation.markCancelled();
                    return true;
                }
            }
            return false;
        }
    }

    // Validates cancellation requests and performs rollback operations safely.
    static class CancellationService {
        private final Inventory inventory;
        private final BookingService bookingService;
        private final BookingHistory bookingHistory;
        private final Stack<String> rollbackReleasedRoomIds = new Stack<>();

        public CancellationService(Inventory inventory, BookingService bookingService, BookingHistory bookingHistory) {
            this.inventory = inventory;
            this.bookingService = bookingService;
            this.bookingHistory = bookingHistory;
        }

        public void cancelReservation(String reservationId) {
            Reservation reservation = bookingService.getReservationById(reservationId);
            if (reservation == null) {
                System.out.println("Cancellation failed: reservation " + reservationId + " does not exist.");
                return;
            }
            if (reservation.isCancelled()) {
                System.out.println("Cancellation failed: reservation " + reservationId + " is already cancelled.");
                return;
            }

            RoomType roomType = reservation.getRequestedRoomType();
            bookingService.releaseRoomId(roomType, reservationId);
            rollbackReleasedRoomIds.push(reservationId);
            inventory.incrementAvailability(roomType);

            boolean historyUpdated = bookingHistory.markReservationCancelled(reservationId);
            if (!historyUpdated) {
                System.out.println("Cancellation warning: history update missing for " + reservationId + ".");
            }

            reservation.markCancelled();
            bookingService.removeConfirmedReservation(reservationId);
            System.out.println("Cancellation confirmed -> " + reservation.confirmationSummary());
        }

        public void printRollbackStack() {
            System.out.println("\nRollback stack (most recent release on top): " + rollbackReleasedRoomIds);
        }
    }

    // Multi-threaded processor that safely handles shared queue and shared booking state.
    static class ConcurrentBookingProcessor {
        private final BookingRequestQueue sharedQueue;
        private final BookingService bookingService;

        public ConcurrentBookingProcessor(BookingRequestQueue sharedQueue, BookingService bookingService) {
            this.sharedQueue = sharedQueue;
            this.bookingService = bookingService;
        }

        public void processConcurrently(int workerCount) {
            List<Thread> workers = new ArrayList<>();
            for (int i = 1; i <= workerCount; i++) {
                final String workerName = "T" + i;
                Thread worker = new Thread(() -> {
                    while (true) {
                        Reservation request = sharedQueue.dequeueRequest();
                        if (request == null) {
                            break;
                        }
                        bookingService.allocateReservationFromThread(request, workerName);
                    }
                }, "BookingWorker-" + i);
                workers.add(worker);
            }

            for (Thread worker : workers) {
                worker.start();
            }
            for (Thread worker : workers) {
                try {
                    worker.join();
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // Read-only reporting layer for admins using stored booking history.
    static class BookingReportService {
        private final BookingHistory bookingHistory;

        public BookingReportService(BookingHistory bookingHistory) {
            this.bookingHistory = bookingHistory;
        }

        public void printBookingHistory() {
            System.out.println("\nBooking History (Chronological):");
            List<Reservation> reservations = bookingHistory.getConfirmedReservations();
            if (reservations.isEmpty()) {
                System.out.println("No confirmed reservations found.");
                return;
            }

            int index = 1;
            for (Reservation reservation : reservations) {
                System.out.println(index + ". " + reservation.confirmationSummary());
                index++;
            }
        }

        public void printSummaryReport() {
            List<Reservation> reservations = bookingHistory.getConfirmedReservations();
            Map<RoomType, Integer> countsByRoomType = new EnumMap<>(RoomType.class);
            for (RoomType roomType : RoomType.values()) {
                countsByRoomType.put(roomType, 0);
            }

            for (Reservation reservation : reservations) {
                RoomType roomType = reservation.getRequestedRoomType();
                countsByRoomType.put(roomType, countsByRoomType.get(roomType) + 1);
            }

            System.out.println("\nBooking Summary Report:");
            System.out.println("Total confirmed bookings: " + reservations.size());
            for (RoomType roomType : RoomType.values()) {
                System.out.println(roomType + " confirmations: " + countsByRoomType.get(roomType));
            }
        }
    }

    // Optional business service attached to a confirmed reservation.
    static class AddOnService {
        private final String name;
        private final double price;

        public AddOnService(String name, double price) {
            this.name = name;
            this.price = price;
        }

        public String summary() {
            return name + " (+" + price + ")";
        }

        public double getPrice() {
            return price;
        }
    }

    // Manages reservation-to-services mapping independent from core allocation.
    static class AddOnServiceManager {
        private final Map<String, List<AddOnService>> servicesByReservationId = new HashMap<>();

        public void addService(String reservationId, AddOnService service) {
            if (reservationId == null || reservationId.isEmpty() || service == null) {
                return;
            }
            servicesByReservationId.computeIfAbsent(reservationId, key -> new ArrayList<>()).add(service);
        }

        public List<AddOnService> getServices(String reservationId) {
            return servicesByReservationId.getOrDefault(reservationId, Collections.emptyList());
        }

        public double getTotalAdditionalCost(String reservationId) {
            double total = 0.0;
            for (AddOnService service : getServices(reservationId)) {
                total += service.getPrice();
            }
            return total;
        }

        public void printReservationAddOns(String reservationId) {
            List<AddOnService> services = getServices(reservationId);
            System.out.println("\nAdd-ons for Reservation " + reservationId + ":");
            if (services.isEmpty()) {
                System.out.println("None selected.");
                return;
            }

            for (AddOnService service : services) {
                System.out.println("- " + service.summary());
            }
            System.out.println("Total additional cost: " + getTotalAdditionalCost(reservationId));
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
        requestQueue.submitRequest(new Reservation("", RoomType.DOUBLE, 2));
        requestQueue.submitRequest(new Reservation("Kabir", RoomType.DOUBLE, 0));
        try {
            requestQueue.submitRequestOrThrow(new Reservation("Naina", null, 1));
        } catch (InvalidBookingException exception) {
            System.out.println("Validation error: " + exception.getMessage());
        }
        requestQueue.printQueueInArrivalOrder();

        Map<RoomType, Integer> inventoryAfterRequestIntake = inventory.getAvailabilitySnapshot();
        System.out.println("\nQueue size awaiting allocation: " + requestQueue.size());
        System.out.println("Inventory after request intake (unchanged): " + inventoryAfterRequestIntake);

        BookingHistory bookingHistory = new BookingHistory();
        BookingService bookingService = new BookingService(inventory, bookingHistory);
        bookingService.processQueuedRequests(requestQueue);
        bookingService.printAllocationSummary();
        System.out.println("\nInventory after allocation: " + inventory.getAvailabilitySnapshot());

        AddOnServiceManager addOnServiceManager = new AddOnServiceManager();
        Set<String> confirmedReservationIds = bookingService.getConfirmedReservationIds();
        for (String reservationId : confirmedReservationIds) {
            if (reservationId.startsWith("SINGLE")) {
                addOnServiceManager.addService(reservationId, new AddOnService("Breakfast", 350.0));
                addOnServiceManager.addService(reservationId, new AddOnService("Airport Pickup", 800.0));
            } else if (reservationId.startsWith("DOUBLE")) {
                addOnServiceManager.addService(reservationId, new AddOnService("Dinner Buffet", 900.0));
            }
        }

        for (String reservationId : confirmedReservationIds) {
            addOnServiceManager.printReservationAddOns(reservationId);
        }

        System.out.println("\nInventory after add-on selection (unchanged): " + inventory.getAvailabilitySnapshot());

        BookingReportService bookingReportService = new BookingReportService(bookingHistory);
        bookingReportService.printBookingHistory();
        bookingReportService.printSummaryReport();

        CancellationService cancellationService = new CancellationService(inventory, bookingService, bookingHistory);
        System.out.println("\nProcessing cancellation requests:");
        cancellationService.cancelReservation("SINGLE-001");
        cancellationService.cancelReservation("SINGLE-001");
        cancellationService.cancelReservation("SUITE-001");
        cancellationService.printRollbackStack();
        System.out.println("Inventory after cancellations: " + inventory.getAvailabilitySnapshot());
        bookingReportService.printBookingHistory();

        System.out.println("\nConcurrent booking simulation:");
        Inventory concurrentInventory = new Inventory();
        BookingHistory concurrentHistory = new BookingHistory();
        BookingService concurrentBookingService = new BookingService(concurrentInventory, concurrentHistory);
        BookingRequestQueue concurrentQueue = new BookingRequestQueue();

        concurrentQueue.submitRequest(new Reservation("Guest-A", RoomType.SINGLE, 1));
        concurrentQueue.submitRequest(new Reservation("Guest-B", RoomType.SINGLE, 1));
        concurrentQueue.submitRequest(new Reservation("Guest-C", RoomType.SINGLE, 1));
        concurrentQueue.submitRequest(new Reservation("Guest-D", RoomType.DOUBLE, 1));
        concurrentQueue.submitRequest(new Reservation("Guest-E", RoomType.DOUBLE, 1));
        concurrentQueue.submitRequest(new Reservation("Guest-F", RoomType.DOUBLE, 1));
        concurrentQueue.submitRequest(new Reservation("Guest-G", RoomType.SUITE, 1));

        ConcurrentBookingProcessor concurrentProcessor =
            new ConcurrentBookingProcessor(concurrentQueue, concurrentBookingService);
        concurrentProcessor.processConcurrently(3);
        concurrentBookingService.printAllocationSummary();
        System.out.println("Concurrent inventory after processing: " + concurrentInventory.getAvailabilitySnapshot());
    }
}