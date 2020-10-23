package com.github.ChristopheCVB.TouchPortal.Spotify.oauth;

import com.github.ChristopheCVB.TouchPortal.Spotify.TouchPortalSpotifyPlugin;
import com.wrapper.spotify.SpotifyApi;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Properties;

public class SpotifyOAuthTokenApplication extends Application {
    private static final String[] SCOPES = new String[]{"streaming", "user-read-playback-state", "user-modify-playback-state", "user-library-modify", "user-library-read", "playlist-modify-private", "playlist-modify-public", "playlist-read-collaborative", "playlist-read-private"};
    public static final String REDIRECT_URI = TouchPortalSpotifyPlugin.PLUGIN_HOME_URL + "oauth2";

    private static URI getAuthorizationURI(SpotifyApi spotifyApi) {
        return spotifyApi.authorizationCodeUri().scope(String.join(",", SCOPES)).build().execute();
    }

    public static void initiate(SpotifyApi spotifyApi, String propertiesFileAbsolutePath) {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            try {
                desktop.browse(SpotifyOAuthTokenApplication.getAuthorizationURI(spotifyApi));
            }
            catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

        launch(propertiesFileAbsolutePath);
    }

    @Override
    public void start(Stage primaryStage) {
        HBox root = new HBox();
        root.setPadding(new Insets(0, 2, 2, 2));

        Label label = new Label("Spotify OAuth Code:");
        TextField tokenTextField = new TextField();
        Button validate = new Button("Ok");

        validate.setOnAction(event -> {
            String oAuthCode = tokenTextField.getText();
            if (!oAuthCode.isEmpty()) {
                try {
                    File propertiesFile = Paths.get(this.getParameters().getRaw().get(0)).toFile();
                    FileInputStream fis = new FileInputStream(propertiesFile.getAbsolutePath());
                    Properties pluginProperties = new Properties();
                    pluginProperties.load(fis);
                    pluginProperties.setProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_CODE, oAuthCode);
                    pluginProperties.store(new FileOutputStream(propertiesFile), "");

                    primaryStage.close();
                }
                catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        });

        root.getChildren().addAll(label, tokenTextField, validate);

        Scene scene = new Scene(root);
        primaryStage.setTitle("Touch Portal Spotify Plugin");
        primaryStage.setScene(scene);

        primaryStage.show();
    }
}
