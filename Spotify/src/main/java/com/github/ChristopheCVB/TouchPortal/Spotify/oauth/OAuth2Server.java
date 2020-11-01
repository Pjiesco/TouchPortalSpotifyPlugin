package com.github.ChristopheCVB.TouchPortal.Spotify.oauth;

import com.sun.net.httpserver.HttpServer;

import java.awt.*;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;

public class OAuth2Server {
    public OAuth2Server(URI authorizationURI, OAuth2CodeListener oAuth2CodeListener) {
        try {
            ServerSocket serverSocket = new ServerSocket(0);
            int availableTCPPort = serverSocket.getLocalPort();
            serverSocket.close();
            String stateQuery = "state=" + availableTCPPort;
            String newQuery = authorizationURI.getQuery();
            if (newQuery == null) {
                newQuery = stateQuery;
            }
            else {
                newQuery += "&" + stateQuery;
            }

            authorizationURI = new URI(authorizationURI.getScheme(), authorizationURI.getAuthority(), authorizationURI.getPath(), newQuery, authorizationURI.getFragment());
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(authorizationURI);
            }

            HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", availableTCPPort), 0);

            httpServer.createContext("/oauth", httpExchange -> {
                String requestMethod = httpExchange.getRequestMethod();
                httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                if ("POST".equalsIgnoreCase(requestMethod) || "GET".equalsIgnoreCase(requestMethod)) {
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
                    oAuth2CodeListener.onOAuthCode(oAuthCode);

                    OutputStream outputStream = httpExchange.getResponseBody();
                    String response = "{\"success\": true}";
                    httpExchange.getResponseHeaders().add("Content-Type", "application/json");
                    httpExchange.sendResponseHeaders(200, response.length());
                    outputStream.write(response.getBytes());
                    outputStream.flush();
                    outputStream.close();

                    httpServer.stop(0);
                }
                else if ("OPTIONS".equalsIgnoreCase(requestMethod)) {
                    httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
                    httpExchange.sendResponseHeaders(204, -1);
                }
            });
            httpServer.start();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface OAuth2CodeListener {
        void onOAuthCode(String oAuth2Code);
    }
}
