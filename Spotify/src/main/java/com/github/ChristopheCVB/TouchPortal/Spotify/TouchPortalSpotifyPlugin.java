package com.github.ChristopheCVB.TouchPortal.Spotify;

import com.github.ChristopheCVB.TouchPortal.Annotations.Category;
import com.github.ChristopheCVB.TouchPortal.Annotations.Event;
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
import com.wrapper.spotify.model_objects.specification.Image;
import com.wrapper.spotify.model_objects.specification.*;
import org.apache.hc.core5.http.ParseException;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private static final String STATE_CHOICE_ENABLED = "Enabled";
    private static final String STATE_CHOICE_DISABLED = "Disabled";
    private static final String STATE_CHOICE_LIKED = "Liked";
    private static final String STATE_CHOICE_DISLIKED = "Disliked";
    private static final String ACTION_DATA_CHOICE_REPEAT_TRACK = "Repeat Track";
    private static final String ACTION_DATA_CHOICE_REPEAT_CONTEXT = "Repeat All";
    private static final String ACTION_DATA_CHOICE_REPEAT_OFF = "Off";
    private static final String ACTION_DATA_CHOICE_REPEAT_CYCLE = "Cycle";

    private SpotifyApi spotifyAPI;

    private int lastKnownPositiveVolume = 100;
    private final ArrayList<PlaylistSimplified> userPlaylists = new ArrayList<>();
    private final HashMap<String, String> trackAlbumImages = new HashMap<>();

    private ScheduledExecutorService scheduledExecutorService;

    @State(defaultValue = "100", desc = "Spotify Current Volume (0 - 100)")
    private String currentVolume;
    @State(defaultValue = "", desc = "Spotify Current Artist Name")
    private String currentArtistName;
    @State(defaultValue = "", desc = "Spotify Current Track Name")
    private String currentTrackName;
    @State(defaultValue = "", desc = "Spotify Current Track Image")
    private String currentTrackImage;
    @State(defaultValue = "", desc = "Spotify Current Playlist Name")
    private String currentPlaylistName;
    @Event(format = "When Repeat Mode changes to $val", name = "When Repeat Mode changes")
    @State(defaultValue = TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_OFF, desc = "Spotify Current Repeat Mode", valueChoices = {TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_TRACK, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_CONTEXT, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_OFF})
    private String[] currentRepeatMode;
    @Event(format = "When Shuffle Mode changes to $val", name = "When Shuffle Mode changes")
    @State(defaultValue = TouchPortalSpotifyPlugin.STATE_CHOICE_DISABLED, desc = "Spotify Current Shuffle Mode", valueChoices = {TouchPortalSpotifyPlugin.STATE_CHOICE_ENABLED, TouchPortalSpotifyPlugin.STATE_CHOICE_DISABLED})
    private String[] currentShuffleMode;
    @Event(format = "When Current Track Like status changes to $val", name = "When Current Track Like status changes")
    @State(defaultValue = "", desc = "Spotify Current Track Like Status", valueChoices = {TouchPortalSpotifyPlugin.STATE_CHOICE_LIKED, TouchPortalSpotifyPlugin.STATE_CHOICE_DISLIKED})
    private String[] currentTrackLikeStatus;

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
        this.updateStates();
        this.updateChoices();
    }

    private void updateChoices() {
        this.updateAvailableDevices();
        this.updateCurrentUserPlaylists();
    }

    private void updateStates() {
        try {
            this.updateCurrentVolume();
            CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
            if (playbackInfo != null) {
                this.updateCurrentTrackFromPlaybackInfo(playbackInfo);
                this.updateCurrentPlaylistName(playbackInfo);
                this.updateCurrentRepeatMode(playbackInfo);
                this.updateCurrentShuffleMode(playbackInfo);
            }
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::updateStates);
        }
    }

    private void updateCurrentRepeatMode(CurrentlyPlayingContext playbackInfo) {
        if (playbackInfo != null) {
            String displayableRepeatMode = this.getDisplayableRepeatMode(playbackInfo.getRepeat_state());
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentRepeatMode.ID, displayableRepeatMode);
        }
    }

    private String getDisplayableRepeatMode(String repeatMode) {
        String displayableRepeatMode;
        switch (repeatMode) {
            case "context":
                displayableRepeatMode = TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_CONTEXT;
                break;

            case "track":
                displayableRepeatMode = TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_TRACK;
                break;

            default:
            case "off":
                displayableRepeatMode = TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_OFF;
                break;
        }
        return displayableRepeatMode;
    }

    private void updateCurrentShuffleMode(CurrentlyPlayingContext playbackInfo) {
        if (playbackInfo != null) {
            String displayableShuffleMode = playbackInfo.getShuffle_state() ? TouchPortalSpotifyPlugin.STATE_CHOICE_ENABLED : TouchPortalSpotifyPlugin.STATE_CHOICE_DISABLED;
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentShuffleMode.ID, displayableShuffleMode);
        }
    }

    private void updateCurrentTrackFromPlaybackInfo(CurrentlyPlayingContext playbackInfo) {
        if (playbackInfo != null) {
            if (playbackInfo.getItem().getType() == ModelObjectType.TRACK) {
                this.updateCurrentTrack((Track) playbackInfo.getItem());
            }
            else {
                this.updateCurrentTrack(null);
            }
        }
        else {
            this.updateCurrentTrack(null);
        }
    }

    private void updateCurrentTrack(Track currentTrack) {
        this.updateCurrentArtistName(currentTrack);
        this.updateCurrentTrackName(currentTrack);
        this.updateCurrentTrackImage(currentTrack);
        this.updateCurrentTrackLikeStatus(currentTrack);
    }

    private void updateCurrentTrackLikeStatus(Track currentTrack) {
        try {
            if (currentTrack != null) {
                Boolean liked = this.spotifyAPI.checkUsersSavedTracks(currentTrack.getId()).build().execute()[0];
                this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackLikeStatus.ID, liked ? TouchPortalSpotifyPlugin.STATE_CHOICE_LIKED : TouchPortalSpotifyPlugin.STATE_CHOICE_DISLIKED);
            }
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.updateCurrentTrackLikeStatus(currentTrack));
        }
    }

    private void updateCurrentArtistName(Track currentTrack) {
        if (currentTrack != null) {
            ArrayList<ArtistSimplified> currentTrackArtists = new ArrayList<>();
            Collections.addAll(currentTrackArtists, currentTrack.getArtists());
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentArtistName.ID, String.join(", ", currentTrackArtists.stream().map(ArtistSimplified::getName).toArray(String[]::new)));
        }
        else {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentArtistName.ID, "");
        }
    }

    private void updateCurrentTrackName(Track currentTrack) {
        if (currentTrack != null) {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackName.ID, currentTrack.getName());
        }
        else {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackName.ID, "");
        }
    }

    private void updateCurrentTrackImage(Track currentTrack) {
        if (currentTrack != null) {
            Image albumImage = currentTrack.getAlbum().getImages()[0];
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackImage.ID, this.imageUrlToBase64(albumImage.getUrl()));
        }
        else {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackImage.ID, "");
        }
    }

    private String imageUrlToBase64(String imageUrl) {
        String base64 = null;
        if (this.trackAlbumImages.containsKey(imageUrl)) {
            base64 = this.trackAlbumImages.get(imageUrl);
        }
        else {
            InputStream inputStream = null;
            ByteArrayOutputStream byteArrayOutputStream = null;
            try {
                URL imageURL = new URL(imageUrl);
                inputStream = imageURL.openStream();
                byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, read);
                }
                byteArrayOutputStream.flush();
                base64 = Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
                this.trackAlbumImages.put(imageUrl, base64);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    }
                    catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
                if (byteArrayOutputStream != null) {
                    try {
                        byteArrayOutputStream.close();
                    }
                    catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            }
        }
        return base64;
    }

    private void updateCurrentPlaylistName(CurrentlyPlayingContext playbackInfo) {
        try {
            if (playbackInfo != null && playbackInfo.getContext() != null) {
                switch (playbackInfo.getContext().getType()) {
                    case PLAYLIST:
                        String[] playlistUriParts = playbackInfo.getContext().getUri().split(":");
                        Playlist playlist = this.spotifyAPI.getPlaylist(playlistUriParts[playlistUriParts.length - 1]).build().execute();
                        this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentPlaylistName.ID, playlist.getName());
                        break;

                    default:
                        this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentPlaylistName.ID, "");
                        break;
                }
            }
            else {
                this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentPlaylistName.ID, "");
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
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::updateCurrentVolume);
        }
    }

    private void updateAvailableDevices() {
        try {
            Device[] availableDevices = this.spotifyAPI.getUsersAvailableDevices().build().execute();
            this.sendChoiceUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.Actions.PlayerStartPlayingThroughDevice.Devices.ID, Arrays.stream(availableDevices).map(Device::getName).toArray(String[]::new));
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::updateCurrentUserPlaylists);
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
            this.userPlaylists.clear();
            this.userPlaylists.addAll(queryingUserPlaylists);
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

    @Action(name = "Playback Start through Device", description = "Start Playing with the selected Available Device", format = "Start Playing on {$devices$}", categoryId = "BaseCategory")
    private void playerStartPlayingThroughDevice(@Data(label = "Device") String[] devices) {
        try {
            Device[] availableDevices = this.spotifyAPI.getUsersAvailableDevices().build().execute();
            Optional<Device> optionalDevice = Arrays.stream(availableDevices).filter(device -> devices[0].equals(device.getName())).findFirst();
            if (optionalDevice.isPresent()) {
                this.spotifyAPI.startResumeUsersPlayback().device_id(optionalDevice.get().getId()).build().execute();
                System.out.println("Spotify: Playback Start through Device: " + devices[0]);
                Thread.sleep(250);
                this.updateStates();
            }
        }
        catch (IOException | ParseException | InterruptedException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerStartPlayingThroughDevice(devices));
        }
    }

    @Action(name = "Playback Start/Resume", prefix = "Spotify Player", description = "Playback Start/Resume", categoryId = "BaseCategory")
    private void playerStartResume() {
        try {
            Device activeDevice = this.getActiveDevice();
            if (activeDevice != null) {
                this.spotifyAPI.startResumeUsersPlayback().device_id(activeDevice.getId()).build().execute();
                System.out.println("Spotify: Playback Start/Resume");
                Thread.sleep(250);
                this.updateStates();
            }
            else {
                System.out.println("Spotify: No Active Device Found");
            }
        }
        catch (IOException | ParseException | InterruptedException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::playerStartResume);
        }
    }

    @Action(name = "Playback Pause", prefix = "Spotify Player", description = "Playback Pause", categoryId = "BaseCategory")
    private void playerPause() {
        try {
            Device activeDevice = this.getActiveDevice();
            if (activeDevice != null) {
                this.spotifyAPI.pauseUsersPlayback().device_id(activeDevice.getId()).build().execute();
                System.out.println("Spotify: Playback Pause");
            }
            else {
                System.out.println("Spotify: No Active Device Found");
            }
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
            System.out.println("Spotify: Playback Next Track");
            Thread.sleep(250);
            this.updateStates();
        }
        catch (IOException | ParseException | InterruptedException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::playerNextTrack);
        }
    }

    @Action(name = "Playback Previous Track", prefix = "Spotify Player", description = "Playback Previous Track", categoryId = "BaseCategory")
    private void playerPreviousTrack() {
        try {
            this.spotifyAPI.skipUsersPlaybackToPreviousTrack().build().execute();
            System.out.println("Spotify: Playback Previous Track");
            Thread.sleep(250);
            this.updateStates();
        }
        catch (IOException | ParseException | InterruptedException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, this::playerPreviousTrack);
        }
    }

    @Action(name = "Playback Volume Set", prefix = "Spotify Player", description = "Playback Volume Set", format = "Set Player Volume to {$volume$}", categoryId = "BaseCategory")
    private void playerSetVolume(@Data(label = "Volume Percentage (0-100)", defaultValue = "100") int volume) {
        try {
            Device activeDevice = this.getActiveDevice();
            if (activeDevice != null) {
                volume = Math.max(Math.min(volume, 100), 0);
                this.spotifyAPI.setVolumeForUsersPlayback(volume).device_id(activeDevice.getId()).build().execute();
                System.out.println("Spotify: Playback Volume Set: " + volume);
                if (volume > 0) {
                    this.lastKnownPositiveVolume = volume;
                }
                this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentVolume.ID, volume + "");
            }
            else {
                System.out.println("Spotify: No Active Device Found");
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            int finalVolume = volume;
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerSetVolume(finalVolume));
        }
    }

    @Action(name = "Playback Volume Up", prefix = "Spotify Player", description = "Playback Volume Up", format = "Player Volume Up by {$volumeStep$}", categoryId = "BaseCategory")
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

    @Action(name = "Playback Volume Down", prefix = "Spotify Player", description = "Playback Volume Down", format = "Player Volume Down by {$volumeStep$}", categoryId = "BaseCategory")
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
                        this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackLikeStatus.ID, TouchPortalSpotifyPlugin.STATE_CHOICE_LIKED);
                        break;

                    case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_DISLIKE:
                        this.spotifyAPI.removeUsersSavedTracks(currentTrack.getId()).build().execute();
                        this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackLikeStatus.ID, TouchPortalSpotifyPlugin.STATE_CHOICE_DISLIKED);
                        break;

                    case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE:
                        if (this.spotifyAPI.checkUsersSavedTracks(currentTrack.getId()).build().execute()[0]) {
                            this.spotifyAPI.removeUsersSavedTracks(currentTrack.getId()).build().execute();
                            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackLikeStatus.ID, TouchPortalSpotifyPlugin.STATE_CHOICE_DISLIKED);
                        }
                        else {
                            this.spotifyAPI.saveTracksForUser(currentTrack.getId()).build().execute();
                            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackLikeStatus.ID, TouchPortalSpotifyPlugin.STATE_CHOICE_LIKED);
                        }
                        break;
                }
                System.out.println("Spotify: Track " + likeDislikeActions[0]);
            }
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.trackLikeDislike(likeDislikeActions));
        }
    }

    @Action(name = "Playlist Start", prefix = "Spotify Playlist", description = "Start playing a specific Playlist", format = "Start Playlist {$playlistNames$}", categoryId = "BaseCategory")
    private void playlistStart(@Data(label = "Playlist Name") String[] playlistNames) {
        PlaylistSimplified selectedPlaylistSimplified = this.getUserPlaylistFromName(playlistNames[0]);
        if (selectedPlaylistSimplified != null) {
            try {
                this.spotifyAPI.startResumeUsersPlayback().context_uri(selectedPlaylistSimplified.getUri()).build().execute();
                System.out.println("Spotify: Playlist Start: " + playlistNames[0]);
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
            Boolean shuffleMode = null;
            CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
            switch (shuffleModeActions[0]) {
                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_ENABLE:
                    shuffleMode = true;
                    this.spotifyAPI.toggleShuffleForUsersPlayback(true).build().execute();
                    System.out.println("Spotify: Player Shuffle Mode: " + shuffleMode);
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_DISABLE:
                    shuffleMode = false;
                    this.spotifyAPI.toggleShuffleForUsersPlayback(false).build().execute();
                    System.out.println("Spotify: Player Shuffle Mode: " + shuffleMode);
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE:
                    if (playbackInfo != null) {
                        shuffleMode = !playbackInfo.getShuffle_state();
                        this.spotifyAPI.toggleShuffleForUsersPlayback(shuffleMode).build().execute();
                        System.out.println("Spotify: Player Shuffle Mode: " + shuffleMode);
                    }
                    break;
            }
            if (shuffleMode != null) {
                this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentShuffleMode.ID, shuffleMode ? TouchPortalSpotifyPlugin.STATE_CHOICE_ENABLED : TouchPortalSpotifyPlugin.STATE_CHOICE_DISABLED);
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
            String newRepeatMode = null;
            CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
            switch (repeatModeActions[0]) {
                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_TRACK:
                    newRepeatMode = "track";
                    this.spotifyAPI.setRepeatModeOnUsersPlayback(newRepeatMode).build().execute();
                    System.out.println("Spotify: Player Repeat Mode: " + newRepeatMode);
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_CONTEXT:
                    newRepeatMode = "context";
                    this.spotifyAPI.setRepeatModeOnUsersPlayback(newRepeatMode).build().execute();
                    System.out.println("Spotify: Player Repeat Mode: " + newRepeatMode);
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_OFF:
                    newRepeatMode = "off";
                    this.spotifyAPI.setRepeatModeOnUsersPlayback(newRepeatMode).build().execute();
                    System.out.println("Spotify: Player Repeat Mode: " + newRepeatMode);
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_CYCLE:
                    if (playbackInfo != null) {
                        switch (playbackInfo.getRepeat_state()) {
                            case "context":
                                newRepeatMode = "track";
                                break;

                            case "track":
                                newRepeatMode = "off";
                                break;

                            default:
                            case "off":
                                newRepeatMode = "context";
                                break;
                        }
                        this.spotifyAPI.setRepeatModeOnUsersPlayback(newRepeatMode).build().execute();
                        System.out.println("Spotify: Player Repeat Mode: " + newRepeatMode);
                    }
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE:
                    if (playbackInfo != null) {
                        if (playbackInfo.getRepeat_state().equals("track")) {
                            newRepeatMode = "off";
                        }
                        else {
                            newRepeatMode = "track";
                        }
                        this.spotifyAPI.setRepeatModeOnUsersPlayback(newRepeatMode).build().execute();
                        System.out.println("Spotify: Player Repeat Mode: " + newRepeatMode);
                    }
                    break;
            }
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentRepeatMode.ID, this.getDisplayableRepeatMode(newRepeatMode));
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getMessage());
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerRepeatMode(repeatModeActions));
        }
    }

    @Action(name = "Playlist Add Track", prefix = "Spotify Playlist", description = "Add Current Track to Playlist", format = "Add Current Track to Playlist {$playlistNames$}", categoryId = "BaseCategory")
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
                        System.out.println("Spotify: Playlist Add Track: " + currentTrack.getName());
                    }
                    else {
                        System.out.println("Spotify: Playlist Add Track already present");
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
