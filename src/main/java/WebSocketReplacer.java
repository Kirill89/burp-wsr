import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.websocket.*;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static burp.api.montoya.websocket.Direction.CLIENT_TO_SERVER;
import static burp.api.montoya.websocket.Direction.SERVER_TO_CLIENT;

public class WebSocketReplacer implements BurpExtension {
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        montoyaApi.extension().setName("WebSocketReplacer");

        montoyaApi.websockets().registerWebSocketCreatedHandler(webSocketCreated -> {
            webSocketCreated.webSocket().registerMessageHandler(new MessageHandler() {
                @Override
                public TextMessageAction handleTextMessage(TextMessage textMessage) {
                    Config config = getConfig();
                    Map<String, String> replacements = null;

                    if (textMessage.direction().equals(CLIENT_TO_SERVER)) {
                        replacements = config.getClientToServer();
                    } else if (textMessage.direction().equals(SERVER_TO_CLIENT)) {
                        replacements = config.getServerToClient();
                    }

                    if (replacements == null || replacements.isEmpty()) {
                        return TextMessageAction.continueWith(textMessage);
                    }

                    String newPayload = textMessage.payload();

                    for (Map.Entry<String, String> e : replacements.entrySet()) {
                        String match = e.getKey();
                        String replacement = e.getValue();

                        if (newPayload.contains(match)) {
                            newPayload = newPayload.replace(match, replacement);
                            System.out.printf("%s text message replacement: %s => %s%n", textMessage.direction().name(), match, replacement);
                        }
                    }

                    return TextMessageAction.continueWith(newPayload);
                }

                @Override
                public BinaryMessageAction handleBinaryMessage(BinaryMessage binaryMessage) {
                    Config config = getConfig();
                    Map<String, String> replacements = null;

                    if (binaryMessage.direction().equals(CLIENT_TO_SERVER)) {
                        replacements = config.getClientToServer();
                    } else if (binaryMessage.direction().equals(SERVER_TO_CLIENT)) {
                        replacements = config.getServerToClient();
                    }

                    if (replacements == null || replacements.isEmpty()) {
                        return BinaryMessageAction.continueWith(binaryMessage);
                    }

                    ByteArray newPayload = binaryMessage.payload().copy();

                    for (Map.Entry<String, String> e : replacements.entrySet()) {
                        String match = e.getKey();
                        String replacement = e.getValue();

                        if (newPayload.indexOf(match) >= 0) {
                            newPayload.setBytes(newPayload.indexOf(match), replacement.getBytes());
                            System.out.printf("%s binary message replacement: %s => %s%n", binaryMessage.direction().name(), match, replacement);
                        }
                    }

                    return BinaryMessageAction.continueWith(newPayload);
                }
            });
        });
    }

    private static Config getConfig() {
        Path path = Paths.get("wsr-config.json");
        String configText;

        try {
            configText = Files.readString(path);
        } catch (IOException e) {
            System.err.println("Can't read config file");
            return new Config();
        }

        return new Gson().fromJson(configText, Config.class);
    }

    private static class Config {
        private Map<String, String> clientToServer = new HashMap<>();
        private Map<String, String> serverToClient = new HashMap<>();

        public Map<String, String> getClientToServer() {
            return clientToServer;
        }

        public void setClientToServer(Map<String, String> clientToServer) {
            this.clientToServer = clientToServer;
        }

        public Map<String, String> getServerToClient() {
            return serverToClient;
        }

        public void setServerToClient(Map<String, String> serverToClient) {
            this.serverToClient = serverToClient;
        }
    }
}
