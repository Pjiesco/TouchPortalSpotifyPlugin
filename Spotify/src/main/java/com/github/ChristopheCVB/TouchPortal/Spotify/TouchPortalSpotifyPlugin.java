package com.github.ChristopheCVB.TouchPortal.Spotify;

import com.github.ChristopheCVB.TouchPortal.Annotations.Action;
import com.github.ChristopheCVB.TouchPortal.Annotations.Category;
import com.github.ChristopheCVB.TouchPortal.Annotations.Plugin;
import com.github.ChristopheCVB.TouchPortal.Helpers.PluginHelper;
import com.github.ChristopheCVB.TouchPortal.Spotify.oauth.SpotifyOAuthTokenApplication;
import com.github.ChristopheCVB.TouchPortal.TouchPortalPlugin;
import com.github.ChristopheCVB.TouchPortal.model.TPInfo;
import com.google.gson.JsonObject;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import org.apache.hc.core5.http.ParseException;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Properties;

@Plugin(version = BuildConfig.VERSION_CODE, colorLight = "#999999", colorDark = "#333333")
public class TouchPortalSpotifyPlugin extends TouchPortalPlugin implements TouchPortalPlugin.TouchPortalPluginListener {
    public static final String PLUGIN_HOME_URL = "http://christophecvb.ovh/Touch%20Portal/Plugins/Spotify/";
    public static final String PLUGIN_CONFIG_URL = TouchPortalSpotifyPlugin.PLUGIN_HOME_URL + "plugin.config";
    public static final String PLUGIN_UPDATE_URL = TouchPortalSpotifyPlugin.PLUGIN_HOME_URL + "?update=true&from=" + BuildConfig.VERSION_CODE;
    public static final String KEY_PLUGIN_VERSION = "plugin.version";
    public static final String KEY_SPOTIFY_CLIENT_ID = "spotify.clientid";
    public static final String KEY_SPOTIFY_CLIENT_SECRET = "spotify.clientsecret";
    public static final String KEY_SPOTIFY_OAUTH_CODE = "spotify.oauthcode";
    public static final String KEY_SPOTIFY_OAUTH_ACCESS_TOKEN = "spotify.oauthaccestoken";
    public static final String KEY_SPOTIFY_OAUTH_REFRESH_TOKEN = "spotify.oauthrefreshtoken";

    private SpotifyApi spotifyAPI;

    /**
     * Constructor
     *
     * @param touchPortalPluginFolder String - args[1]
     */
    protected TouchPortalSpotifyPlugin(String touchPortalPluginFolder) {
        super(touchPortalPluginFolder, true);

        try {
            this.loadProperties("plugin.config");

            String clientId = this.getProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_CLIENT_ID);
            String clientSecret = this.getProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_CLIENT_SECRET);

            this.spotifyAPI = new SpotifyApi.Builder().setClientId(clientId).setClientSecret(clientSecret).setRedirectUri(URI.create(SpotifyOAuthTokenApplication.REDIRECT_URI)).build();

            String oAuthAccessToken = this.getProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_ACCESS_TOKEN);
            String oAuthRefreshToken = this.getProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_REFRESH_TOKEN);
            if (oAuthAccessToken == null || oAuthAccessToken.isEmpty()) {
                TouchPortalSpotifyPlugin.showSpotifyOAuth(this.spotifyAPI, this.getPropertiesFile().getAbsolutePath());

                this.reloadProperties();
                String oAuthCode = this.getProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_CODE);
                AuthorizationCodeCredentials credentials = this.spotifyAPI.authorizationCode(oAuthCode).build().execute();
                oAuthAccessToken = credentials.getAccessToken();
                oAuthRefreshToken = credentials.getRefreshToken();
                this.setProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_ACCESS_TOKEN, oAuthAccessToken);
                this.setProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_REFRESH_TOKEN, oAuthRefreshToken);
                this.storeProperties();
            }
            else {
                try {
                    Properties cloudProperties = new Properties();
                    cloudProperties.load(new URL(TouchPortalSpotifyPlugin.PLUGIN_CONFIG_URL).openStream());
                    long lastPluginVersion = Long.parseLong(cloudProperties.getProperty(TouchPortalSpotifyPlugin.KEY_PLUGIN_VERSION));
                    if (lastPluginVersion > BuildConfig.VERSION_CODE) {
                        if (Desktop.isDesktopSupported()) {
                            Desktop desktop = Desktop.getDesktop();
                            try {
                                desktop.browse(URI.create(TouchPortalSpotifyPlugin.PLUGIN_UPDATE_URL));
                            }
                            catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
                        }
                    }
                }
                catch (NumberFormatException | IOException e) {
                    e.printStackTrace();
                }
            }

            this.spotifyAPI.setAccessToken(oAuthAccessToken);
            this.spotifyAPI.setRefreshToken(oAuthRefreshToken);
        }
        catch (IOException | ParseException | SpotifyWebApiException exception) {
            exception.printStackTrace();
        }
    }

    private static void showSpotifyOAuth(SpotifyApi spotifyApi, String propertiesFilePath) {
        SpotifyOAuthTokenApplication.initiate(spotifyApi, propertiesFilePath);
    }

    public static void main(String[] args) {
        if (args != null && args.length == 2) {
            if (PluginHelper.COMMAND_START.equals(args[0])) {
                // Initialize the Plugin
                TouchPortalSpotifyPlugin spotifyPlugin = new TouchPortalSpotifyPlugin(args[1]);

                boolean connectedPairedAndListening = spotifyPlugin.connectThenPairAndListen(spotifyPlugin);
            }
        }
    }

    @Action(description = "Log Spotify API", categoryId = "BaseCategory")
    private void logSpotifyAPI() {
        System.out.println(this.spotifyAPI.getAccessToken());
    }

    @Override
    public void onDisconnect(Exception exception) {
        System.exit(0);
    }

    @Override
    public void onReceive(JsonObject jsonMessage) {}

    @Override
    public void onInfo(TPInfo tpInfo) {}

    private enum Categories {
        @Category(name = "Spotify", imagePath = "images/icon-24.png")
        BaseCategory
    }
}
