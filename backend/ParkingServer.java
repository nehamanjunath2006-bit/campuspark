
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import database.DBConnection;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

public class ParkingServer {

    private static final int PORT = 8080;
    private static final DateTimeFormatter DISPLAY_TIME
            = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public static void main(String[] args) throws Exception {

        Class.forName("org.postgresql.Driver");

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", PORT),
                0
        );

        // Use a real thread pool so requests are handled immediately.
        server.setExecutor(Executors.newFixedThreadPool(10));

        server.createContext("/api/test", ParkingServer::test);
        server.createContext("/api/register", ParkingServer::register);
        server.createContext("/api/login", ParkingServer::login);
        server.createContext("/api/dashboard", ParkingServer::dashboard);
        server.createContext("/api/vehicles", ParkingServer::vehicles);
        server.createContext("/api/vehicle", ParkingServer::vehicle);
        server.createContext("/api/slots", ParkingServer::slots);
        server.createContext("/api/park", ParkingServer::park);
        server.createContext("/api/remove", ParkingServer::remove);
        server.createContext("/api/history", ParkingServer::history);
        server.createContext("/api/search", ParkingServer::search);
        server.createContext("/api/admin", ParkingServer::admin);
        server.createContext("/", ParkingServer::frontend);

        server.start();

        System.out.println("========================================");
        System.out.println("          CAMPUSPARK BACKEND");
        System.out.println("========================================");
        System.out.println("Server running on: http://127.0.0.1:8080");
        System.out.println("Test: http://127.0.0.1:8080/api/test");
        System.out.println("========================================");
    }

    // --------------------------------------------------
    // TEST
    // --------------------------------------------------
    private static void test(HttpExchange exchange) throws IOException {

        System.out.println("[TEST] Request received");

        String response = "{\"success\":true,\"message\":\"CampusPark Backend is Running\"}";

        addCors(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        System.out.println("[TEST] Sending response...");

        exchange.sendResponseHeaders(200, bytes.length);

        System.out.println("[TEST] Writing response...");

        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.flush();
        os.close();

        System.out.println("[TEST] Response completed");
    }

    // --------------------------------------------------
    // REGISTER
    // --------------------------------------------------
    private static void register(HttpExchange exchange)
            throws IOException {

        if (!method(exchange, "POST")) {
            return;
        }

        Map<String, String> data = readBody(exchange);

        String name = clean(data.get("name"));
        String studentId = clean(data.get("studentId"));
        String email = clean(data.get("email"));
        String phone = clean(data.get("phone"));
        String password = data.get("password");

        if (name.isEmpty()
                || studentId.isEmpty()
                || email.isEmpty()
                || phone.isEmpty()
                || password == null
                || password.length() < 6) {

            sendJson(exchange, 400,
                    "{\"success\":false,\"message\":\"Please fill all fields. Password must contain at least 6 characters.\"}");
            return;
        }

        String sql
                = "INSERT INTO users "
                + "(full_name, student_id, email, phone, password_hash, role) "
                + "VALUES (?, ?, ?, ?, ?, 'STUDENT') "
                + "RETURNING id, full_name, student_id, email, phone, role";

        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, studentId);
            ps.setString(3, email.toLowerCase());
            ps.setString(4, phone);
            ps.setString(5, hash(password));

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    sendJson(exchange, 201,
                            userJson(
                                    rs.getInt("id"),
                                    rs.getString("full_name"),
                                    rs.getString("student_id"),
                                    rs.getString("email"),
                                    rs.getString("phone"),
                                    rs.getString("role")
                            ));
                }
            }

        } catch (SQLException e) {

            if ("23505".equals(e.getSQLState())) {
                sendJson(exchange, 409,
                        "{\"success\":false,\"message\":\"Student ID or email already exists.\"}");
            } else {
                e.printStackTrace();
                sendJson(exchange, 500,
                        "{\"success\":false,\"message\":\"Registration failed. Check database connection.\"}");
            }
        }
    }

    // --------------------------------------------------
    // LOGIN
    // --------------------------------------------------
    private static void login(HttpExchange exchange)
            throws IOException {

        if (!method(exchange, "POST")) {
            return;
        }

        Map<String, String> data = readBody(exchange);

        String identity = clean(data.get("identity")).toLowerCase();
        String password = data.get("password");

        if (identity.isEmpty()
                || password == null
                || password.isEmpty()) {

            sendJson(exchange, 400,
                    "{\"success\":false,\"message\":\"Enter email/student ID and password.\"}");
            return;
        }

        String sql
                = "SELECT id, full_name, student_id, email, phone, role "
                + "FROM users "
                + "WHERE (LOWER(email) = ? OR LOWER(student_id) = ?) "
                + "AND password_hash = ?";

        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, identity);
            ps.setString(2, identity);
            ps.setString(3, hash(password));

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    sendJson(exchange, 200,
                            userJson(
                                    rs.getInt("id"),
                                    rs.getString("full_name"),
                                    rs.getString("student_id"),
                                    rs.getString("email"),
                                    rs.getString("phone"),
                                    rs.getString("role")
                            ));

                } else {

                    sendJson(exchange, 401,
                            "{\"success\":false,\"message\":\"Invalid email/student ID or password.\"}");
                }
            }

        } catch (SQLException e) {

            e.printStackTrace();

            sendJson(exchange, 500,
                    "{\"success\":false,\"message\":\"Login failed. Check database connection.\"}");
        }
    }

    // --------------------------------------------------
    // DASHBOARD
    // --------------------------------------------------
    private static void dashboard(HttpExchange exchange)
            throws IOException {

        if (!method(exchange, "GET")) {
            return;
        }

        String userId = query(exchange, "userId");

        if (!validId(userId)) {
            sendJson(exchange, 400,
                    "{\"success\":false,\"message\":\"User ID is required.\"}");
            return;
        }

        try (Connection c = DBConnection.getConnection()) {

            int total = count(c,
                    "SELECT COUNT(*) FROM parking_slots");

            int occupied = count(c,
                    "SELECT COUNT(*) FROM parking_slots WHERE available = false");

            int available = total - occupied;

            String currentSql
                    = "SELECT v.vehicle_number, v.vehicle_type, "
                    + "ps.slot_number, ph.parked_at "
                    + "FROM parking_history ph "
                    + "JOIN vehicles v ON v.id = ph.vehicle_id "
                    + "JOIN parking_slots ps ON ps.id = ph.slot_id "
                    + "WHERE ph.user_id = ? AND ph.status = 'ACTIVE' "
                    + "ORDER BY ph.parked_at DESC LIMIT 1";

            String current = "null";

            try (PreparedStatement ps = c.prepareStatement(currentSql)) {

                ps.setInt(1, Integer.parseInt(userId));

                try (ResultSet rs = ps.executeQuery()) {

                    if (rs.next()) {

                        current
                                = "{"
                                + "\"vehicleNumber\":" + quote(rs.getString("vehicle_number")) + ","
                                + "\"vehicleType\":" + quote(rs.getString("vehicle_type")) + ","
                                + "\"slot\":" + quote(rs.getString("slot_number")) + ","
                                + "\"parkedAt\":" + quote(formatTime(rs.getTimestamp("parked_at").toLocalDateTime()))
                                + "}";
                    }
                }
            }

            int myVehicles = count(c,
                    "SELECT COUNT(*) FROM vehicles WHERE user_id = " + Integer.parseInt(userId));

            int myHistory = count(c,
                    "SELECT COUNT(*) FROM parking_history WHERE user_id = " + Integer.parseInt(userId));

            String json
                    = "{"
                    + "\"success\":true,"
                    + "\"totalSlots\":" + total + ","
                    + "\"availableSlots\":" + available + ","
                    + "\"occupiedSlots\":" + occupied + ","
                    + "\"myVehicles\":" + myVehicles + ","
                    + "\"myHistory\":" + myHistory + ","
                    + "\"currentParking\":" + current
                    + "}";

            sendJson(exchange, 200, json);

        } catch (Exception e) {

            e.printStackTrace();

            sendJson(exchange, 500,
                    "{\"success\":false,\"message\":\"Unable to load dashboard.\"}");
        }
    }

    // --------------------------------------------------
    // VEHICLES - GET / ADD
    // --------------------------------------------------
    private static void vehicles(HttpExchange exchange)
            throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        String method = exchange.getRequestMethod();

        if (method.equalsIgnoreCase("GET")) {

            String userId = query(exchange, "userId");

            if (!validId(userId)) {
                sendJson(exchange, 400,
                        "{\"success\":false,\"message\":\"User ID is required.\"}");
                return;
            }

            String sql
                    = "SELECT id, vehicle_number, vehicle_type, vehicle_model, "
                    + "vehicle_color, fuel_type "
                    + "FROM vehicles WHERE user_id = ? ORDER BY id DESC";

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {

                ps.setInt(1, Integer.parseInt(userId));

                try (ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {

                        if (!first) {
                            json.append(",");
                        }
                        first = false;

                        json.append("{")
                                .append("\"id\":").append(rs.getInt("id")).append(",")
                                .append("\"vehicleNumber\":").append(quote(rs.getString("vehicle_number"))).append(",")
                                .append("\"vehicleType\":").append(quote(rs.getString("vehicle_type"))).append(",")
                                .append("\"vehicleModel\":").append(quote(rs.getString("vehicle_model"))).append(",")
                                .append("\"vehicleColor\":").append(quote(rs.getString("vehicle_color"))).append(",")
                                .append("\"fuelType\":").append(quote(rs.getString("fuel_type")))
                                .append("}");
                    }
                }

                json.append("]");

                sendJson(exchange, 200, json.toString());

            } catch (SQLException e) {

                e.printStackTrace();

                sendJson(exchange, 500,
                        "{\"success\":false,\"message\":\"Unable to load vehicles.\"}");
            }

            return;
        }

        if (method.equalsIgnoreCase("POST")) {

            Map<String, String> data = readBody(exchange);

            String userId = clean(data.get("userId"));
            String number = clean(data.get("vehicleNumber")).toUpperCase();
            String type = clean(data.get("vehicleType")).toUpperCase();
            String model = clean(data.get("vehicleModel"));
            String color = clean(data.get("vehicleColor"));
            String fuel = clean(data.get("fuelType"));

            if (!validId(userId)
                    || number.isEmpty()
                    || type.isEmpty()) {

                sendJson(exchange, 400,
                        "{\"success\":false,\"message\":\"Vehicle number and vehicle type are required.\"}");
                return;
            }

            if (!Set.of("CAR", "BIKE", "SCOOTER", "OTHER").contains(type)) {
                sendJson(exchange, 400,
                        "{\"success\":false,\"message\":\"Invalid vehicle type.\"}");
                return;
            }

            String sql
                    = "INSERT INTO vehicles "
                    + "(user_id, vehicle_number, vehicle_type, vehicle_model, vehicle_color, fuel_type) "
                    + "VALUES (?, ?, ?, ?, ?, ?) "
                    + "RETURNING id";

            try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {

                ps.setInt(1, Integer.parseInt(userId));
                ps.setString(2, number);
                ps.setString(3, type);
                ps.setString(4, model);
                ps.setString(5, color);
                ps.setString(6, fuel);

                try (ResultSet rs = ps.executeQuery()) {

                    if (rs.next()) {

                        sendJson(exchange, 201,
                                "{\"success\":true,\"message\":\"Vehicle added successfully.\",\"id\":"
                                + rs.getInt(1) + "}");
                    }
                }

            } catch (SQLException e) {

                if ("23505".equals(e.getSQLState())) {

                    sendJson(exchange, 409,
                            "{\"success\":false,\"message\":\"This vehicle number is already registered.\"}");

                } else {

                    e.printStackTrace();

                    sendJson(exchange, 500,
                            "{\"success\":false,\"message\":\"Unable to add vehicle.\"}");
                }
            }

            return;
        }

        sendJson(exchange, 405,
                "{\"success\":false,\"message\":\"Method not allowed.\"}");
    }

    // --------------------------------------------------
    // DELETE VEHICLE
    // --------------------------------------------------
    private static void vehicle(HttpExchange exchange)
            throws IOException {

        if (!method(exchange, "DELETE")) {
            return;
        }

        String userId = query(exchange, "userId");
        String vehicleId = query(exchange, "vehicleId");

        if (!validId(userId) || !validId(vehicleId)) {

            sendJson(exchange, 400,
                    "{\"success\":false,\"message\":\"User ID and vehicle ID are required.\"}");
            return;
        }

        String sql
                = "DELETE FROM vehicles "
                + "WHERE id = ? AND user_id = ? "
                + "AND NOT EXISTS ("
                + "SELECT 1 FROM parking_history "
                + "WHERE vehicle_id = vehicles.id AND status = 'ACTIVE'"
                + ")";

        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, Integer.parseInt(vehicleId));
            ps.setInt(2, Integer.parseInt(userId));

            int changed = ps.executeUpdate();

            if (changed == 1) {

                sendJson(exchange, 200,
                        "{\"success\":true,\"message\":\"Vehicle deleted.\"}");

            } else {

                sendJson(exchange, 400,
                        "{\"success\":false,\"message\":\"Vehicle not found or currently parked.\"}");
            }

        } catch (SQLException e) {

            e.printStackTrace();

            sendJson(exchange, 500,
                    "{\"success\":false,\"message\":\"Unable to delete vehicle.\"}");
        }
    }

    // --------------------------------------------------
    // PARKING SLOTS
    // --------------------------------------------------
    private static void slots(HttpExchange exchange)
            throws IOException {

        if (!method(exchange, "GET")) {
            return;
        }

        String userId = query(exchange, "userId");

        String sql
                = "SELECT ps.id, ps.slot_number, ps.allowed_type, ps.available, "
                + "v.vehicle_number "
                + "FROM parking_slots ps "
                + "LEFT JOIN parking_history ph "
                + "ON ph.slot_id = ps.id AND ph.status = 'ACTIVE' "
                + "LEFT JOIN vehicles v ON v.id = ph.vehicle_id "
                + "ORDER BY ps.slot_number";

        StringBuilder json = new StringBuilder("[");

        boolean first = true;

        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                if (!first) {
                    json.append(",");
                }
                first = false;

                json.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"slot\":").append(quote(rs.getString("slot_number"))).append(",")
                        .append("\"allowedType\":").append(quote(rs.getString("allowed_type"))).append(",")
                        .append("\"available\":").append(rs.getBoolean("available")).append(",")
                        .append("\"vehicleNumber\":").append(quote(rs.getString("vehicle_number")))
                        .append("}");
            }

            json.append("]");

            sendJson(exchange, 200, json.toString());

        } catch (SQLException e) {

            e.printStackTrace();

            sendJson(exchange, 500,
                    "{\"success\":false,\"message\":\"Unable to load parking slots.\"}");
        }
    }

    // --------------------------------------------------
    // PARK VEHICLE
    // --------------------------------------------------
    private static void park(HttpExchange exchange)
            throws IOException {

        if (!method(exchange, "POST")) {
            return;
        }

        Map<String, String> data = readBody(exchange);

        String userId = clean(data.get("userId"));
        String vehicleId = clean(data.get("vehicleId"));
        String slotId = clean(data.get("slotId"));

        if (!validId(userId)
                || !validId(vehicleId)
                || !validId(slotId)) {

            sendJson(exchange, 400,
                    "{\"success\":false,\"message\":\"User, vehicle and slot are required.\"}");
            return;
        }

        Connection c = null;

        try {

            c = DBConnection.getConnection();
            c.setAutoCommit(false);

            int uid = Integer.parseInt(userId);
            int vid = Integer.parseInt(vehicleId);
            int sid = Integer.parseInt(slotId);

            String vehicleType = null;

            String vehicleSql
                    = "SELECT vehicle_type FROM vehicles "
                    + "WHERE id = ? AND user_id = ?";

            try (PreparedStatement ps = c.prepareStatement(vehicleSql)) {

                ps.setInt(1, vid);
                ps.setInt(2, uid);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {

                        c.rollback();

                        sendJson(exchange, 404,
                                "{\"success\":false,\"message\":\"Vehicle not found in your account.\"}");
                        return;
                    }

                    vehicleType = rs.getString("vehicle_type");
                }
            }

            String activeSql
                    = "SELECT slot_id FROM parking_history "
                    + "WHERE user_id = ? AND status = 'ACTIVE'";

            try (PreparedStatement ps = c.prepareStatement(activeSql)) {

                ps.setInt(1, uid);

                try (ResultSet rs = ps.executeQuery()) {

                    if (rs.next()) {

                        c.rollback();

                        sendJson(exchange, 400,
                                "{\"success\":false,\"message\":\"You already have a vehicle parked. Remove it before parking another vehicle.\"}");
                        return;
                    }
                }
            }

            String vehicleParkedSql
                    = "SELECT 1 FROM parking_history "
                    + "WHERE vehicle_id = ? AND status = 'ACTIVE'";

            try (PreparedStatement ps = c.prepareStatement(vehicleParkedSql)) {

                ps.setInt(1, vid);

                try (ResultSet rs = ps.executeQuery()) {

                    if (rs.next()) {

                        c.rollback();

                        sendJson(exchange, 400,
                                "{\"success\":false,\"message\":\"This vehicle is already parked.\"}");
                        return;
                    }
                }
            }

            String slotSql
                    = "SELECT slot_number, allowed_type, available "
                    + "FROM parking_slots WHERE id = ? FOR UPDATE";

            String slotNumber;
            String allowedType;
            boolean available;

            try (PreparedStatement ps = c.prepareStatement(slotSql)) {

                ps.setInt(1, sid);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {

                        c.rollback();

                        sendJson(exchange, 404,
                                "{\"success\":false,\"message\":\"Parking slot not found.\"}");
                        return;
                    }

                    slotNumber = rs.getString("slot_number");
                    allowedType = rs.getString("allowed_type");
                    available = rs.getBoolean("available");
                }
            }

            if (!available) {

                c.rollback();

                sendJson(exchange, 400,
                        "{\"success\":false,\"message\":\"This parking slot is already occupied.\"}");
                return;
            }

            if (!"ALL".equals(allowedType)
                    && !allowedType.equals(vehicleType)) {

                c.rollback();

                sendJson(exchange, 400,
                        "{\"success\":false,\"message\":\"This slot is reserved for "
                        + allowedType
                        + " vehicles.\"}");
                return;
            }

            String insert
                    = "INSERT INTO parking_history "
                    + "(user_id, vehicle_id, slot_id, parked_at, status) "
                    + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'ACTIVE')";

            try (PreparedStatement ps = c.prepareStatement(insert)) {

                ps.setInt(1, uid);
                ps.setInt(2, vid);
                ps.setInt(3, sid);

                ps.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE parking_slots SET available = false WHERE id = ?")) {

                ps.setInt(1, sid);
                ps.executeUpdate();
            }

            c.commit();

            sendJson(exchange, 200,
                    "{"
                    + "\"success\":true,"
                    + "\"message\":\"Vehicle parked successfully.\","
                    + "\"slot\":" + quote(slotNumber)
                    + "}");

        } catch (Exception e) {

            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                }
            }

            e.printStackTrace();

            sendJson(exchange, 500,
                    "{\"success\":false,\"message\":\"Unable to park vehicle.\"}");

        } finally {

            if (c != null) {
                try {
                    c.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    // --------------------------------------------------
    // REMOVE VEHICLE
    // --------------------------------------------------
    private static void remove(HttpExchange exchange)
            throws IOException {

        if (!method(exchange, "POST")) {
            return;
        }

        Map<String, String> data = readBody(exchange);

        String userId = clean(data.get("userId"));

        if (!validId(userId)) {

            sendJson(exchange, 400,
                    "{\"success\":false,\"message\":\"User ID is required.\"}");
            return;
        }

        Connection c = null;

        try {

            c = DBConnection.getConnection();
            c.setAutoCommit(false);

            int uid = Integer.parseInt(userId);

            String find
                    = "SELECT ph.id, ph.slot_id, ps.slot_number, v.vehicle_number "
                    + "FROM parking_history ph "
                    + "JOIN parking_slots ps ON ps.id = ph.slot_id "
                    + "JOIN vehicles v ON v.id = ph.vehicle_id "
                    + "WHERE ph.user_id = ? AND ph.status = 'ACTIVE' "
                    + "FOR UPDATE";

            int historyId;
            int slotId;
            String slotNumber;
            String vehicleNumber;

            try (PreparedStatement ps = c.prepareStatement(find)) {

                ps.setInt(1, uid);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {

                        c.rollback();

                        sendJson(exchange, 404,
                                "{\"success\":false,\"message\":\"You do not have a currently parked vehicle.\"}");
                        return;
                    }

                    historyId = rs.getInt("id");
                    slotId = rs.getInt("slot_id");
                    slotNumber = rs.getString("slot_number");
                    vehicleNumber = rs.getString("vehicle_number");
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE parking_history "
                    + "SET removed_at = CURRENT_TIMESTAMP, status = 'COMPLETED' "
                    + "WHERE id = ?")) {

                ps.setInt(1, historyId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE parking_slots "
                    + "SET available = true "
                    + "WHERE id = ?")) {

                ps.setInt(1, slotId);
                ps.executeUpdate();
            }

            c.commit();

            sendJson(exchange, 200,
                    "{"
                    + "\"success\":true,"
                    + "\"message\":\"Vehicle removed successfully.\","
                    + "\"vehicleNumber\":" + quote(vehicleNumber) + ","
                    + "\"slot\":" + quote(slotNumber)
                    + "}");

        } catch (Exception e) {

            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                }
            }

            e.printStackTrace();

            sendJson(exchange, 500,
                    "{\"success\":false,\"message\":\"Unable to remove vehicle.\"}");

        } finally {

            if (c != null) {
                try {
                    c.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    // --------------------------------------------------
    // HISTORY
    // --------------------------------------------------
    private static void history(HttpExchange exchange)
            throws IOException {

        if (!method(exchange, "GET")) {
            return;
        }

        String userId = query(exchange, "userId");

        if (!validId(userId)) {

            sendJson(exchange, 400,
                    "{\"success\":false,\"message\":\"User ID is required.\"}");
            return;
        }

        String sql
                = "SELECT v.vehicle_number, v.vehicle_type, "
                + "ps.slot_number, ph.parked_at, ph.removed_at, ph.status "
                + "FROM parking_history ph "
                + "JOIN vehicles v ON v.id = ph.vehicle_id "
                + "JOIN parking_slots ps ON ps.id = ph.slot_id "
                + "WHERE ph.user_id = ? "
                + "ORDER BY ph.parked_at DESC";

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, Integer.parseInt(userId));

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    if (!first) {
                        json.append(",");
                    }
                    first = false;

                    Timestamp parked = rs.getTimestamp("parked_at");
                    Timestamp removed = rs.getTimestamp("removed_at");

                    json.append("{")
                            .append("\"vehicleNumber\":").append(quote(rs.getString("vehicle_number"))).append(",")
                            .append("\"vehicleType\":").append(quote(rs.getString("vehicle_type"))).append(",")
                            .append("\"slot\":").append(quote(rs.getString("slot_number"))).append(",")
                            .append("\"parkedAt\":").append(quote(formatTime(parked.toLocalDateTime()))).append(",")
                            .append("\"removedAt\":").append(removed == null
                            ? "null"
                            : quote(formatTime(removed.toLocalDateTime()))).append(",")
                            .append("\"status\":").append(quote(rs.getString("status")))
                            .append("}");
                }
            }

            json.append("]");

            sendJson(exchange, 200, json.toString());

        } catch (SQLException e) {

            e.printStackTrace();

            sendJson(exchange, 500,
                    "{\"success\":false,\"message\":\"Unable to load parking history.\"}");
        }
    }

    // --------------------------------------------------
    // SEARCH VEHICLE
    // --------------------------------------------------
    private static void search(HttpExchange exchange)
            throws IOException {

        if (!method(exchange, "GET")) {
            return;
        }

        String vehicleNumber
                = clean(query(exchange, "vehicleNumber")).toUpperCase();

        if (vehicleNumber.isEmpty()) {

            sendJson(exchange, 400,
                    "{\"success\":false,\"message\":\"Enter vehicle number.\"}");
            return;
        }

        String sql
                = "SELECT v.vehicle_number, v.vehicle_type, "
                + "u.full_name, ps.slot_number, ph.status, ph.parked_at "
                + "FROM vehicles v "
                + "JOIN users u ON u.id = v.user_id "
                + "LEFT JOIN parking_history ph "
                + "ON ph.vehicle_id = v.id AND ph.status = 'ACTIVE' "
                + "LEFT JOIN parking_slots ps ON ps.id = ph.slot_id "
                + "WHERE UPPER(v.vehicle_number) = ?";

        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, vehicleNumber);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {

                    sendJson(exchange, 404,
                            "{\"success\":false,\"message\":\"Vehicle not found.\"}");
                    return;
                }

                String status
                        = rs.getString("status") == null
                        ? "NOT PARKED"
                        : rs.getString("status");

                String json
                        = "{"
                        + "\"success\":true,"
                        + "\"vehicleNumber\":" + quote(rs.getString("vehicle_number")) + ","
                        + "\"vehicleType\":" + quote(rs.getString("vehicle_type")) + ","
                        + "\"owner\":" + quote(rs.getString("full_name")) + ","
                        + "\"slot\":" + quote(rs.getString("slot_number")) + ","
                        + "\"status\":" + quote(status)
                        + "}";

                sendJson(exchange, 200, json);
            }

        } catch (SQLException e) {

            e.printStackTrace();

            sendJson(exchange, 500,
                    "{\"success\":false,\"message\":\"Search failed.\"}");
        }
    }

    // --------------------------------------------------
    // ADMIN
    // --------------------------------------------------
    private static void admin(HttpExchange exchange)
            throws IOException {

        if (!method(exchange, "GET")) {
            return;
        }

        String userId = query(exchange, "userId");

        if (!validId(userId)) {
            sendJson(exchange, 400,
                    "{\"success\":false,\"message\":\"User ID is required.\"}");
            return;
        }

        try (Connection c = DBConnection.getConnection()) {

            String role;

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT role FROM users WHERE id = ?")) {

                ps.setInt(1, Integer.parseInt(userId));

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        sendJson(exchange, 404,
                                "{\"success\":false,\"message\":\"User not found.\"}");
                        return;
                    }

                    role = rs.getString("role");
                }
            }

            if (!"ADMIN".equalsIgnoreCase(role)) {

                sendJson(exchange, 403,
                        "{\"success\":false,\"message\":\"Admin access required.\"}");
                return;
            }

            int users = count(c,
                    "SELECT COUNT(*) FROM users WHERE role = 'STUDENT'");

            int vehicles = count(c,
                    "SELECT COUNT(*) FROM vehicles");

            int total = count(c,
                    "SELECT COUNT(*) FROM parking_slots");

            int occupied = count(c,
                    "SELECT COUNT(*) FROM parking_slots WHERE available = false");

            int available = total - occupied;

            int active = count(c,
                    "SELECT COUNT(*) FROM parking_history WHERE status = 'ACTIVE'");

            String json
                    = "{"
                    + "\"success\":true,"
                    + "\"users\":" + users + ","
                    + "\"vehicles\":" + vehicles + ","
                    + "\"totalSlots\":" + total + ","
                    + "\"occupied\":" + occupied + ","
                    + "\"available\":" + available + ","
                    + "\"activeParking\":" + active
                    + "}";

            sendJson(exchange, 200, json);

        } catch (Exception e) {

            e.printStackTrace();

            sendJson(exchange, 500,
                    "{\"success\":false,\"message\":\"Unable to load admin dashboard.\"}");
        }
    }

    // --------------------------------------------------
    // HELPERS
    // --------------------------------------------------
    private static int count(Connection c, String sql)
            throws SQLException {

        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {

            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String userJson(
            int id,
            String name,
            String studentId,
            String email,
            String phone,
            String role) {

        return "{"
                + "\"success\":true,"
                + "\"user\":{"
                + "\"id\":" + id + ","
                + "\"name\":" + quote(name) + ","
                + "\"studentId\":" + quote(studentId) + ","
                + "\"email\":" + quote(email) + ","
                + "\"phone\":" + quote(phone) + ","
                + "\"role\":" + quote(role)
                + "}"
                + "}";
    }

    private static String hash(String value) {

        try {

            MessageDigest md
                    = MessageDigest.getInstance("SHA-256");

            byte[] bytes
                    = md.digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();

            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();

        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }

    private static String quote(String value) {

        if (value == null) {
            return "null";
        }

        return "\""
                + value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\r", "\\r")
                        .replace("\n", "\\n")
                + "\"";
    }

    private static String formatTime(LocalDateTime time) {

        return time.format(DISPLAY_TIME);
    }

    private static String clean(String value) {

        return value == null ? "" : value.trim();
    }

    private static boolean validId(String value) {

        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            return Integer.parseInt(value) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean method(
            HttpExchange exchange,
            String expected)
            throws IOException {

        if (handleOptions(exchange)) {
            return false;
        }

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase(expected)) {

            sendJson(exchange, 405,
                    "{\"success\":false,\"message\":\"Method not allowed.\"}");

            return false;
        }

        return true;
    }

    private static boolean handleOptions(
            HttpExchange exchange)
            throws IOException {

        addCors(exchange);

        if (exchange.getRequestMethod()
                .equalsIgnoreCase("OPTIONS")) {

            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }

        return false;
    }

    private static void addCors(HttpExchange exchange) {

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*"
        );

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, DELETE, OPTIONS"
        );

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type"
        );
    }

    private static void sendJson(
            HttpExchange exchange,
            int status,
            String json)
            throws IOException {

        addCors(exchange);

        byte[] bytes
                = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        exchange.sendResponseHeaders(
                status,
                bytes.length
        );

        try (OutputStream out
                = exchange.getResponseBody()) {

            out.write(bytes);
        }
    }

    private static void frontend(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) {
            return;
        }

        String requestPath = exchange.getRequestURI().getPath();
        String relative = "/".equals(requestPath) ? "index.html" : requestPath.substring(1);
        Path file = Path.of("frontend").resolve(relative).normalize();
        Path frontendRoot = Path.of("frontend").toAbsolutePath().normalize();
        if (!file.toAbsolutePath().normalize().startsWith(frontendRoot) || !Files.isRegularFile(file)) {
            sendJson(exchange, 404, "{\"success\":false,\"message\":\"Frontend file not found.\"}");
            return;
        }

        String contentType = Files.probeContentType(file);
        exchange.getResponseHeaders().set("Content-Type", contentType == null ? "application/octet-stream" : contentType);
        byte[] content = Files.readAllBytes(file);
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private static Map<String, String> readBody(
            HttpExchange exchange)
            throws IOException {

        String body
                = new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                );

        Map<String, String> map = new HashMap<>();

        if (body.isBlank()) {
            return map;
        }

        for (String part : body.split("&")) {

            String[] pair = part.split("=", 2);

            if (pair.length == 2) {

                String key
                        = URLDecoder.decode(
                                pair[0],
                                StandardCharsets.UTF_8
                        );

                String value
                        = URLDecoder.decode(
                                pair[1],
                                StandardCharsets.UTF_8
                        );

                map.put(key, value);
            }
        }

        return map;
    }

    private static String query(
            HttpExchange exchange,
            String name) {

        String q
                = exchange.getRequestURI().getRawQuery();

        if (q == null) {
            return "";
        }

        for (String part : q.split("&")) {

            String[] pair = part.split("=", 2);

            if (pair.length == 2) {

                String key
                        = URLDecoder.decode(
                                pair[0],
                                StandardCharsets.UTF_8
                        );

                if (key.equals(name)) {

                    return URLDecoder.decode(
                            pair[1],
                            StandardCharsets.UTF_8
                    );
                }
            }
        }

        return "";
    }
}
