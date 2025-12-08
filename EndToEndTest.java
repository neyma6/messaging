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

    private static final String REGISTRY_URL = "http://localhost:8081/registry/user";
    private static final String HISTORY_URL = "http://localhost:8082/history";

    public static void main(String[] args) throws Exception {
        System.out.println("Starting End-to-End Test...");
        HttpClient client = HttpClient.newHttpClient();

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        System.out.println("Generated User A: " + userA);
        System.out.println("Generated User B: " + userB);

        // 1. Get Chat ID
        System.out.println("1. Getting Chat ID...");
        String chatUri = HISTORY_URL + "/chat?userId1=" + userA + "&userId2=" + userB;
        HttpResponse<String> chatResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(chatUri)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        if (chatResp.statusCode() != 200) {
            throw new RuntimeException("Chat ID failed: " + chatResp.statusCode() + " " + chatResp.body());
        }

        String chatIdStr = chatResp.body().replace("\"", "").trim();
        System.out.println("Chat ID: " + chatIdStr);

        // 2. Discover Address
        System.out.println("2. Querying Service Registry...");
        String addrA = getAddress(client, userA);
        String addrB = getAddress(client, userB);
        System.out.println("Addresses: " + addrA + " | " + addrB);

        // 3. Connect WS
        CountDownLatch latch = new CountDownLatch(2);
        WebSocket wsA = connect(client, addrA, userA, new MessageListener("A", latch));
        WebSocket wsB = connect(client, addrB, userB, new MessageListener("B", latch));

        // 4. Send Messages
        System.out.println("4. Sending Messages...");
        String msgA = String.format(
                "{\"chatId\":\"%s\",\"messageContent\":\"Hello from A\",\"userId\":\"%s\",\"messageSent\":\"now\"}",
                chatIdStr, userA);
        wsA.sendText(msgA, true).join();

        String msgB = String.format(
                "{\"chatId\":\"%s\",\"messageContent\":\"Hello from B\",\"userId\":\"%s\",\"messageSent\":\"now\"}",
                chatIdStr, userB);
        wsB.sendText(msgB, true).join();

        // 5. Verify Delivery
        System.out.println("5. Waiting for delivery...");
        boolean delivered = latch.await(10, TimeUnit.SECONDS);
        if (!delivered) {
            System.err.println("FAILURE: Messages not received.");
            System.exit(1);
        }
        System.out.println("Messages Delivery Verified.");

        // 6. Verify Chat List (New Feature)
        System.out.println("6. Verifying Chat List...");
        HttpRequest listReq = HttpRequest.newBuilder()
                .uri(URI.create(HISTORY_URL + "/user/" + userA + "/chats"))
                .GET().build();
        HttpResponse<String> listResp = client.send(listReq, HttpResponse.BodyHandlers.ofString());
        if (!listResp.body().contains(chatIdStr)) {
            System.err.println("FAILURE: Chat ID not found in user list: " + listResp.body());
            System.exit(1);
        }
        System.out.println("Chat List Verified: " + listResp.body());

        // Cleanup
        wsA.sendClose(WebSocket.NORMAL_CLOSURE, "Done");
        wsB.sendClose(WebSocket.NORMAL_CLOSURE, "Done");

        System.out.println("SUCCESS! End-to-End Test Passed.");
    }

    private static String getAddress(HttpClient client, UUID userId) throws Exception {
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder().uri(URI.create(REGISTRY_URL + "/" + userId)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("Registry failed: " + resp.statusCode());
        Matcher m = Pattern.compile("\"address\":\"(.*?)\"").matcher(resp.body());
        if (m.find())
            return m.group(1);
        throw new RuntimeException("No address in " + resp.body());
    }

    private static WebSocket connect(HttpClient client, String url, UUID userId, WebSocket.Listener listener) {
        return client.newWebSocketBuilder()
                .buildAsync(URI.create(url + "?userId=" + userId), listener)
                .join();
    }

    static class MessageListener implements WebSocket.Listener {
        String name;
        CountDownLatch latch;

        MessageListener(String name, CountDownLatch latch) {
            this.name = name;
            this.latch = latch;
        }

        public void onOpen(WebSocket ws) {
            System.out.println(name + " Connected");
            WebSocket.Listener.super.onOpen(ws);
        }

        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            System.out.println(name + " Received: " + data);
            latch.countDown();
            return WebSocket.Listener.super.onText(ws, data, last);
        }
    }
}
