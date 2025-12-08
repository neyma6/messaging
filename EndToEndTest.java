
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EndToEndTest {
    
    // Config
    private static final String REGISTRY_URL = "http://localhost:8081/registry/user";
    private static final String HISTORY_URL = "http://localhost:8082/history/chat";
    
    public static void main(String[] args) throws Exception {
        System.out.println("Starting End-to-End Test...");
        
        HttpClient client = HttpClient.newHttpClient();
        
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        
        System.out.println("Generated User A: " + userA);
        System.out.println("Generated User B: " + userB);
        
        // 1. Create/Get Chat ID
        System.out.println("1. Getting Chat ID...");
        String chatUri = HISTORY_URL + "?userId1=" + userA + "&userId2=" + userB;
        HttpResponse<String> chatResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(chatUri)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        
        if (chatResp.statusCode() != 200) {
            throw new RuntimeException("Failed to get chat ID. Status: " + chatResp.statusCode() + " Body: " + chatResp.body());
        }
        
        String chatIdStr = chatResp.body().replace("\"", "").trim();
        System.out.println("Chat ID: " + chatIdStr);
        // Validate UUID format
        UUID.fromString(chatIdStr);
        
        // 2. Discover Messaging Service Address
        System.out.println("2. Querying Service Registry...");
        String addrA = getAddress(client, userA);
        String addrB = getAddress(client, userB);
        
        System.out.println("User A assigned to: " + addrA);
        System.out.println("User B assigned to: " + addrB);
        
        // 3. Connect WebSockets
        System.out.println("3. Connecting WebSockets...");
        CountDownLatch latch = new CountDownLatch(2); // Expect 2 received messages (A->B, B->A)
        
        MessageListener listenerA = new MessageListener("A", latch);
        MessageListener listenerB = new MessageListener("B", latch);
        
        WebSocket wsA = connect(client, addrA, userA, listenerA);
        WebSocket wsB = connect(client, addrB, userB, listenerB);
        
        // 4. Send Messages
        System.out.println("4. Sending Messages...");
        
        // A sends to B
        String msgA = String.format("{\"chatId\":\"%s\",\"messageContent\":\"Hello from A\",\"userId\":\"%s\",\"messageSent\":\"now\"}", chatIdStr, userA);
        wsA.sendText(msgA, true).join();
        System.out.println("Sent A -> B");

        // B sends to A
        String msgB = String.format("{\"chatId\":\"%s\",\"messageContent\":\"Hello from B\",\"userId\":\"%s\",\"messageSent\":\"now\"}", chatIdStr, userB);
        wsB.sendText(msgB, true).join();
        System.out.println("Sent B -> A");
        
        // 5. Verify
        System.out.println("5. Waiting for delivery...");
        boolean success = latch.await(10, TimeUnit.SECONDS);
        
        if (success) {
            System.out.println("SUCCESS: Both messages received!");
        } else {
            System.out.println("FAILURE: Timed out waiting for messages.");
            System.out.println("Count: " + latch.getCount());
        }
        
        // Graceful close
        wsA.sendClose(WebSocket.NORMAL_CLOSURE, "Done").join();
        wsB.sendClose(WebSocket.NORMAL_CLOSURE, "Done").join();
        
        System.exit(success ? 0 : 1);
    }
    
    private static String getAddress(HttpClient client, UUID userId) throws Exception {
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder().uri(URI.create(REGISTRY_URL + "/" + userId)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Registry failed: " + resp.statusCode());
        }
        
        // Parse JSON: {"serviceId":"...", "address":"..."}
        Pattern p = Pattern.compile("\"address\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(resp.body());
        if (m.find()) {
            return m.group(1);
        }
        // If address is null or missing
        throw new RuntimeException("Address not found in: " + resp.body());
    }
    
    private static WebSocket connect(HttpClient client, String baseUrl, UUID userId, WebSocket.Listener listener) throws Exception {
        // Construct WS URI: address + "?userId=" + userId
        // baseUrl already includes /ws from our fix
        String uri = baseUrl + "?userId=" + userId;
        return client.newWebSocketBuilder()
                .buildAsync(URI.create(uri), listener)
                .join();
    }
    
    static class MessageListener implements WebSocket.Listener {
        private final String name;
        private final CountDownLatch latch;
        
        MessageListener(String name, CountDownLatch latch) {
            this.name = name;
            this.latch = latch;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println(name + " Connected");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            System.out.println(name + " Received: " + data);
            latch.countDown();
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }
        
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
             System.err.println(name + " Error: " + error.getMessage());
        }
    }
}
