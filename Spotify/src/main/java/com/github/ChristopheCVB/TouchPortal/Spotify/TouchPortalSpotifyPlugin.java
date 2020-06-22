package com.github.ChristopheCVB.TouchPortal.Spotify;

import com.github.ChristopheCVB.TouchPortal.Annotations.Category;
import com.github.ChristopheCVB.TouchPortal.Annotations.*;
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
import com.wrapper.spotify.model_objects.miscellaneous.Device;
import com.wrapper.spotify.model_objects.specification.*;
import org.apache.hc.core5.http.ParseException;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    public static final String KEY_STATES_UPDATE_INTERVAL = "states.updateInterval";

    private static final String ACTION_DATA_CHOICE_PLAY = "Play";
    private static final String ACTION_DATA_CHOICE_PAUSE = "Pause";
    private static final String ACTION_DATA_CHOICE_TOGGLE = "Toggle";
    private static final String ACTION_DATA_CHOICE_MUTE = "Mute";
    private static final String ACTION_DATA_CHOICE_UNMUTE = "Unmute";
    private static final String ACTION_DATA_CHOICE_LIKE = "Like";
    private static final String ACTION_DATA_CHOICE_DISLIKE = "Dislike";
    private static final String ACTION_DATA_CHOICE_ENABLE = "Enable";
    private static final String ACTION_DATA_CHOICE_DISABLE = "Disable";
    private static final String ACTION_DATA_CHOICE_REPEAT_TRACK = "Repeat Track";
    private static final String ACTION_DATA_CHOICE_REPEAT_CONTEXT = "Repeat All";
    private static final String ACTION_DATA_CHOICE_REPEAT_OFF = "Off";
    private static final String ACTION_DATA_CHOICE_REPEAT_CYCLE = "Cycle";

    private SpotifyApi spotifyAPI;
    private int lastKnownPositiveVolume = 100;
    private ArrayList<PlaylistSimplified> userPlaylists = new ArrayList<>();

    private ScheduledExecutorService scheduledExecutorService;

    @State(defaultValue = "100", desc = "Spotify Current Volume (0 - 100)")
    private String currentVolume;
    @State(defaultValue = "", desc = "Spotify Current Artist Name")
    private String currentArtistName;
    @State(defaultValue = "", desc = "Spotify Current Track Name")
    private String currentTrackName;
    @State(defaultValue = "", desc = "Spotify Current Playlist Name")
    private String currentPlaylistName;

    /**
     * Constructor
     *
     * @param touchPortalPluginFolder String - args[1]
     */
    private TouchPortalSpotifyPlugin(String touchPortalPluginFolder) {
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
                this.removeProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_CODE);
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

                if (connectedPairedAndListening) {
                    spotifyPlugin.startUpdatingStatesAndValues();
                }
            }
        }
    }

    private void startUpdatingStatesAndValues() {
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        int updateInterval = 30;
        try {
            updateInterval = Integer.parseInt(this.getProperty(TouchPortalSpotifyPlugin.KEY_STATES_UPDATE_INTERVAL));
        }
        catch (NumberFormatException ignored) {}
        this.scheduledExecutorService.scheduleAtFixedRate(this::updateStatesAndChoices, 0, updateInterval, TimeUnit.SECONDS);
    }

    private void updateStatesAndChoices() {
        this.updateCurrentStates();
        this.updateCurrentUserPlaylists();
    }

    private void updateCurrentStates() {
        this.updateCurrentVolume();
        try {
            CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
            if (playbackInfo != null) {
                Track currentTrack = null;
                if (playbackInfo.getItem().getType() == ModelObjectType.TRACK) {
                    currentTrack = (Track) playbackInfo.getItem();
                }

                this.updateCurrentArtistName(currentTrack);
                this.updateCurrentTrackName(currentTrack);
                this.updateCurrentPlaylistName(playbackInfo);
            }
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::updateCurrentStates);
        }
    }

    private void updateCurrentArtistName(Track currentTrack) {
        if (currentTrack != null) {
            ArrayList<ArtistSimplified> currentTrackArtists = new ArrayList<>();
            Collections.addAll(currentTrackArtists, currentTrack.getArtists());
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentArtistName.ID, String.join(", ", currentTrackArtists.stream().map(ArtistSimplified::getName).toArray(String[]::new)));
        }
    }

    private void updateCurrentTrackName(Track currentTrack) {
        if (currentTrack != null) {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackName.ID, currentTrack.getName());
        }
    }

    private void updateCurrentPlaylistName(CurrentlyPlayingContext playbackInfo) {
        try {
            if (playbackInfo != null) {
                switch (playbackInfo.getContext().getType()) {
                    case PLAYLIST:
                        // FIXME: Current Playlist
                        Paging<PlaylistSimplified> playlistSimplifiedPaging = this.spotifyAPI.searchPlaylists(playbackInfo.getContext().getUri()).limit(1).build().execute();
                        if (playlistSimplifiedPaging.getItems().length > 0) {
                            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentPlaylistName.ID, playlistSimplifiedPaging.getItems()[0].getName());
                        }
                        break;

                    default:
                        this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentPlaylistName.ID, "");
                        break;
                }
            }
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.updateCurrentPlaylistName(playbackInfo));
        }
    }

    private void updateCurrentVolume() {
        try {
            Device activeDevice = this.getActiveDevice();
            if (activeDevice != null) {
                if (activeDevice.getVolume_percent() > 0) {
                    this.lastKnownPositiveVolume = activeDevice.getVolume_percent();
                }
                this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentVolume.ID, activeDevice.getVolume_percent() + "");
            }
        }
        catch (IOException | ParseException exception) {
            exception.printStackTrace();
        }
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::updateCurrentVolume);
        }
    }

    private void updateCurrentUserPlaylists() {
        try {
            Paging<PlaylistSimplified> paginatedUserPlaylists = this.spotifyAPI.getListOfCurrentUsersPlaylists().limit(50).build().execute();
            ArrayList<PlaylistSimplified> queryingUserPlaylists = new ArrayList<>(Arrays.asList(paginatedUserPlaylists.getItems()));
            while (paginatedUserPlaylists.getNext() != null) {
                paginatedUserPlaylists = this.spotifyAPI.getListOfCurrentUsersPlaylists().offset(queryingUserPlaylists.size()).limit(50).build().execute();
                queryingUserPlaylists.addAll(Arrays.asList(paginatedUserPlaylists.getItems()));
            }
            this.userPlaylists = queryingUserPlaylists;
            String[] playlistNames = this.userPlaylists.stream().map(PlaylistSimplified::getName).toArray(String[]::new);

            this.sendChoiceUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.Actions.PlaylistStart.PlaylistNames.ID, playlistNames);
            this.sendChoiceUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.Actions.SaveCurrentTrackToPlaylist.PlaylistNames.ID, playlistNames);
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::updateCurrentUserPlaylists);
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
                catch (IOException | ParseException ignored) {}
                catch (SpotifyWebApiException spotifyWebApiException) {
                    System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
                    this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerPlayPause(playPauseActions));
                }
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
            Device activeDevice = this.getActiveDevice();
            if (activeDevice != null) {
                this.spotifyAPI.startResumeUsersPlayback().device_id(activeDevice.getId()).build().execute();
                System.out.println("Started/Resumed");
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::playerStartResume);
        }
    }

    @Action(name = "Playback Pause", prefix = "Spotify Player", description = "Playback Pause", categoryId = "BaseCategory")
    private void playerPause() {
        try {
            this.spotifyAPI.pauseUsersPlayback().build().execute();
            System.out.println("Paused");
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::playerPause);
        }
    }

    @Action(name = "Playback Next Track", prefix = "Spotify Player", description = "Playback Next Track", categoryId = "BaseCategory")
    private void playerNextTrack() {
        try {
            this.spotifyAPI.skipUsersPlaybackToNextTrack().build().execute();
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::playerNextTrack);
        }
    }

    @Action(name = "Playback Previous Track", prefix = "Spotify Player", description = "Playback Previous Track", categoryId = "BaseCategory")
    private void playerPreviousTrack() {
        try {
            this.spotifyAPI.skipUsersPlaybackToPreviousTrack().build().execute();
            System.out.println("Previous");
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::playerPreviousTrack);
        }
    }

    @Action(name = "Volume Set", prefix = "Spotify Player", description = "Volume Set", format = "Set Player Volume to {$volume$}", categoryId = "BaseCategory")
    private void playerSetVolume(@Data(label = "Volume Percentage (0-100)", defaultValue = "100") int volume) {
        try {
            Device activeDevice = this.getActiveDevice();
            if (activeDevice != null) {
                volume = Math.max(Math.min(volume, 100), 0);
                this.spotifyAPI.setVolumeForUsersPlayback(volume).device_id(activeDevice.getId()).build().execute();
                if (volume > 0) {
                    this.lastKnownPositiveVolume = volume;
                }
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            int finalVolume = volume;
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerSetVolume(finalVolume));
        }
    }

    @Action(name = "Volume Up", prefix = "Spotify Player", description = "Volume Up", format = "Player Volume Up by {$volumeStep$}", categoryId = "BaseCategory")
    private void playerVolumeUp(@Data(label = "Volume Step", defaultValue = "10") int volumeStep) {
        try {
            CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
            if (playbackInfo != null) {
                this.playerSetVolume(playbackInfo.getDevice().getVolume_percent() + volumeStep);
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerVolumeUp(volumeStep));
        }
    }

    @Action(name = "Volume Down", prefix = "Spotify Player", description = "Volume Down", format = "Player Volume Down by {$volumeStep$}", categoryId = "BaseCategory")
    private void playerVolumeDown(@Data(label = "Volume Step", defaultValue = "10") int volumeStep) {
        try {
            CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
            if (playbackInfo != null) {
                this.playerSetVolume(playbackInfo.getDevice().getVolume_percent() - volumeStep);
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerVolumeDown(volumeStep));
        }
    }

    @Action(name = "Volume Mute/Unmute", prefix = "Spotify Player", description = "Mute/Unmute Volume", format = "{$muteUnmuteActions$} Volume", categoryId = "BaseCategory")
    private void playerMuteUnmute(@Data(label = "Action", valueChoices = {TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_MUTE, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_UNMUTE}, defaultValue = TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE) String[] muteUnmuteActions) {
        switch (muteUnmuteActions[0]) {
            case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_MUTE:
                this.playerSetVolume(0);
                break;

            case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_UNMUTE:
                this.playerSetVolume(this.lastKnownPositiveVolume);
                break;

            case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE:
                try {
                    CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
                    if (playbackInfo != null) {
                        if (playbackInfo.getDevice().getVolume_percent() > 0) {
                            this.playerSetVolume(0);
                        }
                        else {
                            this.playerSetVolume(this.lastKnownPositiveVolume);
                        }
                    }
                }
                catch (IOException | ParseException ignored) {}
                catch (SpotifyWebApiException spotifyWebApiException) {
                    System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
                    this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerMuteUnmute(muteUnmuteActions));
                }
                break;
        }
    }

    @Action(name = "Track Like/Dislike", prefix = "Spotify Track", description = "Like/Dislike a Track", format = "{$likeDislikeActions$} Track", categoryId = "BaseCategory")
    private void trackLikeDislike(@Data(label = "Action", valueChoices = {TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_LIKE, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_DISLIKE, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE}, defaultValue = TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_LIKE) String[] likeDislikeActions) {
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

                    case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE:
                        if (this.spotifyAPI.checkUsersSavedTracks(currentTrack.getId()).build().execute()[0]) {
                            this.spotifyAPI.removeUsersSavedTracks(currentTrack.getId()).build().execute();
                        }
                        else {
                            this.spotifyAPI.saveTracksForUser(currentTrack.getId()).build().execute();
                        }
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
        PlaylistSimplified selectedPlaylistSimplified = this.getUserPlaylistFromName(playlistNames[0]);
        if (selectedPlaylistSimplified != null) {
            try {
                this.spotifyAPI.startResumeUsersPlayback().context_uri(selectedPlaylistSimplified.getUri()).build().execute();
            }
            catch (IOException | ParseException ignored) {}
            catch (SpotifyWebApiException spotifyWebApiException) {
                System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
                this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playlistStart(playlistNames));
            }
        }
    }

    @Action(name = "Player Shuffle Mode", prefix = "Spotify Player", description = "Player Shuffle Mode", format = "{$shuffleModeActions$} Shuffle Mode", categoryId = "BaseCategory")
    private void playerShuffleMode(@Data(label = "Action", valueChoices = {TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_ENABLE, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_DISABLE, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE}, defaultValue = TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE) String[] shuffleModeActions) {
        try {
            switch (shuffleModeActions[0]) {
                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_ENABLE:
                    this.spotifyAPI.toggleShuffleForUsersPlayback(true).build().execute();
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_DISABLE:
                    this.spotifyAPI.toggleShuffleForUsersPlayback(false).build().execute();
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE:
                    CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
                    if (playbackInfo != null) {
                        this.spotifyAPI.toggleShuffleForUsersPlayback(!playbackInfo.getShuffle_state()).build().execute();
                    }
                    break;
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerShuffleMode(shuffleModeActions));
        }
    }

    @Action(name = "Player Repeat Mode", prefix = "Spotify Player", description = "Player Repeat Mode", format = "Set Repeat Mode to {$repeatModeActions$}", categoryId = "BaseCategory")
    private void playerRepeatMode(@Data(label = "Action", valueChoices = {TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_TRACK, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_CONTEXT, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_OFF, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_CYCLE, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE}, defaultValue = TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_CYCLE) String[] repeatModeActions) {
        try {
            CurrentlyPlayingContext playbackInfo;
            switch (repeatModeActions[0]) {
                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_TRACK:
                    this.spotifyAPI.setRepeatModeOnUsersPlayback("track").build().execute();
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_CONTEXT:
                    this.spotifyAPI.setRepeatModeOnUsersPlayback("context").build().execute();
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_OFF:
                    this.spotifyAPI.setRepeatModeOnUsersPlayback("off").build().execute();
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_CYCLE:
                    playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
                    if (playbackInfo != null) {
                        String nextRepeatMode;
                        switch (playbackInfo.getRepeat_state()) {
                            case "context":
                                nextRepeatMode = "track";
                                break;

                            case "track":
                                nextRepeatMode = "off";
                                break;

                            default:
                            case "off":
                                nextRepeatMode = "context";
                                break;
                        }
                        this.spotifyAPI.setRepeatModeOnUsersPlayback(nextRepeatMode).build().execute();
                    }
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE:
                    playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
                    if (playbackInfo != null) {
                        String repeatMode;
                        if (playbackInfo.getRepeat_state().equals("track")) {
                            repeatMode = "off";
                        }
                        else {
                            repeatMode = "track";
                        }
                        this.spotifyAPI.setRepeatModeOnUsersPlayback(repeatMode).build().execute();
                    }
                    break;
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerRepeatMode(repeatModeActions));
        }
    }

    @Action(name = "Add Track to Playlist", prefix = "Spotify Playlist", description = "Add Current Track to Playlist", format = "Add Current Track to Playlist {$playlistNames$}", categoryId = "BaseCategory")
    private void saveCurrentTrackToPlaylist(@Data(label = "Playlist Name") String[] playlistNames) {
        try {
            PlaylistSimplified selectedPlaylistSimplified = this.getUserPlaylistFromName(playlistNames[0]);
            if (selectedPlaylistSimplified != null) {
                CurrentlyPlaying currentPlaying = this.spotifyAPI.getUsersCurrentlyPlayingTrack().build().execute();
                Track currentTrack = null;
                if (currentPlaying != null) {
                    if (currentPlaying.getItem().getType() == ModelObjectType.TRACK) {
                        currentTrack = (Track) currentPlaying.getItem();
                    }
                }

                if (currentTrack != null) {
                    Paging<PlaylistTrack> paginatedPlaylistTracks = this.spotifyAPI.getPlaylistsItems(selectedPlaylistSimplified.getId()).limit(100).build().execute();
                    ArrayList<PlaylistTrack> playlistTracks = new ArrayList<>(Arrays.asList(paginatedPlaylistTracks.getItems()));
                    while (paginatedPlaylistTracks.getNext() != null) {
                        paginatedPlaylistTracks = this.spotifyAPI.getPlaylistsItems(selectedPlaylistSimplified.getId()).offset(this.userPlaylists.size()).limit(100).build().execute();
                        playlistTracks.addAll(Arrays.asList(paginatedPlaylistTracks.getItems()));
                    }
                    Track finalCurrentTrack = currentTrack;
                    boolean alreadyPresent = playlistTracks.stream().anyMatch(playlistTrack -> {
                        if (playlistTrack.getTrack().getType().equals(ModelObjectType.TRACK)) {
                            Track playlistTrackTrack = (Track) playlistTrack.getTrack();
                            return playlistTrackTrack.getId().equals(finalCurrentTrack.getId());
                        }
                        return false;
                    });

                    if (!alreadyPresent) {
                        this.spotifyAPI.addItemsToPlaylist(selectedPlaylistSimplified.getId(), new String[]{currentTrack.getUri()}).build().execute();
                    }
                }
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.saveCurrentTrackToPlaylist(playlistNames));
        }
    }

    private synchronized void handleSpotifyWebApiException(SpotifyWebApiException spotifyWebApiException, Runnable runnable) {
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
                this.removeProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_ACCESS_TOKEN);
                this.removeProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_REFRESH_TOKEN);
                try {
                    this.storeProperties();
                }
                catch (IOException ignored) {}
                // TODO: Ask new OAuth Code to user
            }
        }
    }

    private PlaylistSimplified getUserPlaylistFromName(String playlistName) {
        PlaylistSimplified playlistSimplified = null;
        for (PlaylistSimplified playlistSimplifiedItem : this.userPlaylists) {
            if (playlistName.equals(playlistSimplifiedItem.getName())) {
                playlistSimplified = playlistSimplifiedItem;
                break;
            }
        }
        return playlistSimplified;
    }

    private Device getActiveDevice() throws ParseException, SpotifyWebApiException, IOException {
        Device activeDevice = null;
        Optional<Device> optionalActiveDevice = Arrays.stream(this.spotifyAPI.getUsersAvailableDevices().build().execute()).filter(Device::getIs_active).findFirst();
        if (optionalActiveDevice.isPresent()) {
            activeDevice = optionalActiveDevice.get();
        }
        return activeDevice;
    }

    @Override
    public void onDisconnect(Exception exception) {
        this.scheduledExecutorService.shutdownNow();
        System.exit(0);
    }

    @Override
    public void onReceive(JsonObject jsonMessage) {
    }

    @Override
    public void onInfo(TPInfo tpInfo) {
    }

    private enum Categories {
        @Category(name = "Spotify", imagePath = "images/icon-24.png")
        BaseCategory
    }
}
