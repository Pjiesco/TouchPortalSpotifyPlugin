package com.github.ChristopheCVB.TouchPortal.Spotify.oauth;

import com.sun.net.httpserver.HttpServer;

import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

public class OAuth2Server {
    public OAuth2Server(URI authorizationURI, OAuthCodeListener oAuthCodeListener) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(authorizationURI);
            }

            HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 8042), 0);

            httpServer.createContext("/oauth", httpExchange -> {
                String requestMethod = httpExchange.getRequestMethod();
                if ("POST".equalsIgnoreCase(requestMethod)) {
                    String oAuthCode = null;
                    String query = httpExchange.getRequestURI().getQuery();
                    String[] params = query.split("&");
                    for (String param : params) {
                        String[] keyValue = param.split("=");
                        if ("code".equals(keyValue[0])) {
                            oAuthCode = keyValue[1];
                            break;
                        }
                    }
                    oAuthCodeListener.onOAuthCode(oAuthCode);

                    OutputStream outputStream = httpExchange.getResponseBody();
                    String response = "{\"success\": true}";
                    httpExchange.getResponseHeaders().add("Content-Type", "application/json");
                    httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    httpExchange.sendResponseHeaders(200, response.length());
                    outputStream.write(response.getBytes());
                    outputStream.flush();
                    outputStream.close();

                    httpServer.stop(0);
                }
            });
            httpServer.start();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public interface OAuthCodeListener {
        void onOAuthCode(String oAuthCode);
    }
}
