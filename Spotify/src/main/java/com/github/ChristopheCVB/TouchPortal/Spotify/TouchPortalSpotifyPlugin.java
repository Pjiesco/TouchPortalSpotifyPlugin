package com.github.ChristopheCVB.TouchPortal.Spotify;

import com.github.ChristopheCVB.TouchPortal.Annotations.Action;
import com.github.ChristopheCVB.TouchPortal.Annotations.Category;
import com.github.ChristopheCVB.TouchPortal.Annotations.Data;
import com.github.ChristopheCVB.TouchPortal.Annotations.Plugin;
import com.github.ChristopheCVB.TouchPortal.Helpers.PluginHelper;
import com.github.ChristopheCVB.TouchPortal.Spotify.oauth.SpotifyOAuthTokenApplication;
import com.github.ChristopheCVB.TouchPortal.TouchPortalPlugin;
import com.github.ChristopheCVB.TouchPortal.model.TPInfo;
import com.google.gson.JsonObject;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import org.apache.hc.core5.http.ParseException;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;

@Plugin(version = BuildConfig.VERSION_CODE, colorLight = "#23CF5F", colorDark = "#000000")
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

    private static final String ACTION_DATA_CHOICE_PLAY = "Play";
    private static final String ACTION_DATA_CHOICE_PAUSE = "Pause";
    private static final String ACTION_DATA_CHOICE_TOGGLE = "Toggle";
    private static final String ACTION_DATA_CHOICE_MUTE = "Mute";
    private static final String ACTION_DATA_CHOICE_UNMUTE = "Unmute";
    private static final String ACTION_DATA_CHOICE_LIKE = "Like";
    private static final String ACTION_DATA_CHOICE_DISLIKE = "Dislike";

    private SpotifyApi spotifyAPI;
    private int lastKnownVolume;
    private ArrayList<PlaylistSimplified> userPlaylists = new ArrayList<>();

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

    private void initialize() {
        this.initializeLastKnownVolume();

        this.initializeCurrentUserPlaylists();
    }

    private void initializeCurrentUserPlaylists() {
        try {
            ArrayList<String> currentUserPlaylistsNames = new ArrayList<>();
            Paging<PlaylistSimplified> paginatedUserPlaylists = this.spotifyAPI.getListOfCurrentUsersPlaylists().limit(50).build().execute();
            for (PlaylistSimplified playlistSimplified : paginatedUserPlaylists.getItems()) {
                currentUserPlaylistsNames.add(playlistSimplified.getName());
                this.userPlaylists.add(playlistSimplified);
            }
            while (paginatedUserPlaylists.getNext() != null) {
                paginatedUserPlaylists = this.spotifyAPI.getListOfCurrentUsersPlaylists().offset(currentUserPlaylistsNames.size()).limit(50).build().execute();
                for (PlaylistSimplified playlistSimplified : paginatedUserPlaylists.getItems()) {
                    currentUserPlaylistsNames.add(playlistSimplified.getName());
                    this.userPlaylists.add(playlistSimplified);
                }
            }
            this.sendChoiceUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.Actions.PlaylistStart.PlaylistNames.ID, currentUserPlaylistsNames.toArray(new String[0]));
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::initializeCurrentUserPlaylists);
        }
    }

    private void initializeLastKnownVolume() {
        try {
            CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
            if (playbackInfo != null) {
                this.lastKnownVolume = playbackInfo.getDevice().getVolume_percent();
            }
        }
        catch (IOException | ParseException exception) {
            exception.printStackTrace();
        }
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::initializeLastKnownVolume);
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

                if (connectedPairedAndListening) {
                    spotifyPlugin.initialize();
                }
            }
        }
    }

    @Action(name = "Playback Play/Pause", prefix = "Spotify Player", description = "Playback Play/Pause", format = "{$playPauseActions$} Playback", categoryId = "BaseCategory")
    private void playerPlayPause(@Data(label = "Action", valueChoices = {TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_PLAY, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_PAUSE, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE}, defaultValue = TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE) String[] playPauseActions) {
        switch (playPauseActions[0]) {
            case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE:
                try {
                    CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
                    if (playbackInfo != null) {
                        if (playbackInfo.getIs_playing()) {
                            this.playerPause();
                        }
                        else {
                            this.playerStartResume();
                        }
                    }
                }
                catch (SpotifyWebApiException spotifyWebApiException) {
                    System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
                    this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerPlayPause(playPauseActions));
                }
                catch (IOException | ParseException ignored) {}
                break;

            case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_PLAY:
                this.playerStartResume();
                break;

            case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_PAUSE:
                this.playerPause();
                break;
        }
    }

    @Action(name = "Playback Start/Resume", prefix = "Spotify Player", description = "Playback Start/Resume", categoryId = "BaseCategory")
    private void playerStartResume() {
        try {
            this.spotifyAPI.startResumeUsersPlayback().build().execute();
            System.out.println("Started/Resumed");
        }
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::playerStartResume);
        }
        catch (IOException | ParseException ignored) {}
    }

    @Action(name = "Playback Pause", prefix = "Spotify Player", description = "Playback Pause", categoryId = "BaseCategory")
    private void playerPause() {
        try {
            this.spotifyAPI.pauseUsersPlayback().build().execute();
            System.out.println("Paused");
        }
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::playerPause);
        }
        catch (IOException | ParseException ignored) {}
    }

    @Action(name = "Playback Next Track", prefix = "Spotify Player", description = "Playback Next Track", categoryId = "BaseCategory")
    private void playerNextTrack() {
        try {
            this.spotifyAPI.skipUsersPlaybackToNextTrack().build().execute();
        }
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::playerNextTrack);
        }
        catch (IOException | ParseException ignored) {}
    }

    @Action(name = "Playback Previous Track", prefix = "Spotify Player", description = "Playback Previous Track", categoryId = "BaseCategory")
    private void playerPreviousTrack() {
        try {
            this.spotifyAPI.skipUsersPlaybackToPreviousTrack().build().execute();
            System.out.println("Previous");
        }
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::playerPreviousTrack);
        }
        catch (IOException | ParseException ignored) {}
    }

    @Action(name = "Volume Set", prefix = "Spotify Player", description = "Volume Set", format = "Set Player Volume to {$volume$}", categoryId = "BaseCategory")
    private void playerSetVolume(@Data(label = "Volume Percentage (0-100)", defaultValue = "100") int volume) {
        try {
            CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
            if (playbackInfo != null) {
                if (volume > 100) {
                    volume = 100;
                }
                else if (volume < 0) {
                    volume = 0;
                }
                this.spotifyAPI.setVolumeForUsersPlayback(volume).build().execute();
                if (volume > 0) {
                    this.lastKnownVolume = volume;
                }
            }
        }
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            int finalVolume = volume;
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerSetVolume(finalVolume));
        }
        catch (IOException | ParseException ignored) {}
    }

    @Action(name = "Volume Up", prefix = "Spotify Player", description = "Volume Up", format = "Player Volume Up by {$volumeStep$}", categoryId = "BaseCategory")
    private void playerVolumeUp(@Data(label = "Volume Step", defaultValue = "10") int volumeStep) {
        try {
            CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
            if (playbackInfo != null) {
                this.playerSetVolume(playbackInfo.getDevice().getVolume_percent() + volumeStep);
            }
        }
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerVolumeUp(volumeStep));
        }
        catch (IOException | ParseException ignored) {}
    }

    @Action(name = "Volume Down", prefix = "Spotify Player", description = "Volume Down", format = "Player Volume Down by {$volumeStep$}", categoryId = "BaseCategory")
    private void playerVolumeDown(@Data(label = "Volume Step", defaultValue = "10") int volumeStep) {
        try {
            CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
            if (playbackInfo != null) {
                this.playerSetVolume(playbackInfo.getDevice().getVolume_percent() - volumeStep);
            }
        }
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerVolumeDown(volumeStep));
        }
        catch (IOException | ParseException ignored) {}
    }

    @Action(name = "Volume Mute/Unmute", prefix = "Spotify Player", description = "Mute/Unmute Volume", format = "{$muteUnmuteActions$} Volume", categoryId = "BaseCategory")
    private void playerMuteUnmute(@Data(label = "Action", valueChoices = {TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_MUTE, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_UNMUTE}, defaultValue = TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE) String[] muteUnmuteActions) {
        switch (muteUnmuteActions[0]) {
            case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_MUTE:
                this.playerSetVolume(0);
                break;

            case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_UNMUTE:
                this.playerSetVolume(this.lastKnownVolume);
                break;

            case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE:
                try {
                    CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
                    if (playbackInfo != null) {
                        if (playbackInfo.getDevice().getVolume_percent() > 0) {
                            this.playerSetVolume(0);
                        }
                        else {
                            this.playerSetVolume(this.lastKnownVolume);
                        }
                    }
                }
                catch (SpotifyWebApiException spotifyWebApiException) {
                    System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
                    this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerMuteUnmute(muteUnmuteActions));
                }
                catch (IOException | ParseException ignored) {}
                break;
        }
    }

    @Action(name = "Track Like/Dislike", prefix = "Spotify Track", description = "Like/Dislike a Track", format = "{$likeDislikeActions$} Track", categoryId = "BaseCategory")
    private void trackLikeDislike(@Data(label = "Action", valueChoices = {TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_LIKE, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_DISLIKE}, defaultValue = TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_LIKE) String[] likeDislikeActions) {
        try {
            CurrentlyPlaying currentPlaying = this.spotifyAPI.getUsersCurrentlyPlayingTrack().build().execute();
            Track currentTrack = null;
            if (currentPlaying != null) {
                if (currentPlaying.getItem().getType() == ModelObjectType.TRACK) {
                    currentTrack = (Track) currentPlaying.getItem();
                }
            }

            if (currentTrack != null) {
                switch (likeDislikeActions[0]) {
                    case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_LIKE:
                        this.spotifyAPI.saveTracksForUser(currentTrack.getId()).build().execute();
                        break;

                    case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_DISLIKE:
                        this.spotifyAPI.removeUsersSavedTracks(currentTrack.getId()).build().execute();
                        break;
                }
            }
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.trackLikeDislike(likeDislikeActions));
        }
    }

    @Action(name = "Playlist Start", prefix = "Spotify Playlist", description = "Start playing a specified Playlist", format = "Start Playlist {$playlistNames$}", categoryId = "BaseCategory")
    private void playlistStart(@Data(label = "Playlist Name") String[] playlistNames) {
        for (PlaylistSimplified playlistSimplified : this.userPlaylists) {
            if (playlistNames[0].equals(playlistSimplified.getName())) {
                try {
                    this.spotifyAPI.startResumeUsersPlayback().context_uri(playlistSimplified.getUri()).build().execute();
                }
                catch (IOException | ParseException ignored) {}
                catch (SpotifyWebApiException spotifyWebApiException) {
                    System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
                    this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playlistStart(playlistNames));
                }
                break;
            }
        }
    }

    private void handleSpotifyWebApiException(SpotifyWebApiException spotifyWebApiException, Runnable runnable) {
        if (spotifyWebApiException.getMessage().contains("expired")) {
            try {
                AuthorizationCodeCredentials credentials = this.spotifyAPI.authorizationCodeRefresh().build().execute();

                String oAuthAccessToken = credentials.getAccessToken();
                this.setProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_ACCESS_TOKEN, oAuthAccessToken);
                this.storeProperties();
                this.spotifyAPI.setAccessToken(oAuthAccessToken);

                runnable.run();
            }
            catch (IOException | SpotifyWebApiException | ParseException exception) {
                exception.printStackTrace();
            }
        }
    }

    @Override
    public void onDisconnect(Exception exception) {
        System.exit(0);
    }

    @Override
    public void onReceive(JsonObject jsonMessage) {
    }

    @Override
    public void onInfo(TPInfo tpInfo) {}

    private enum Categories {
        @Category(name = "Spotify", imagePath = "images/icon-24.png")
        BaseCategory
    }
}
