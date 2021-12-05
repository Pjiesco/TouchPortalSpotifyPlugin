package com.github.ChristopheCVB.TouchPortal.Spotify;

import com.christophecvb.touchportal.annotations.Category;
import com.christophecvb.touchportal.annotations.Event;
import com.christophecvb.touchportal.annotations.*;
import com.christophecvb.touchportal.helpers.PluginHelper;
import com.christophecvb.touchportal.TouchPortalPlugin;
import com.christophecvb.touchportal.model.TPBroadcastMessage;
import com.christophecvb.touchportal.model.TPInfoMessage;
import com.christophecvb.touchportal.model.TPListChangeMessage;
import com.christophecvb.touchportal.model.TPSettingsMessage;
import com.christophecvb.touchportal.oauth2.OAuth2LocalServerReceiver;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.exceptions.detailed.TooManyRequestsException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import com.wrapper.spotify.model_objects.miscellaneous.Device;
import com.wrapper.spotify.model_objects.specification.Image;
import com.wrapper.spotify.model_objects.specification.*;
import org.apache.hc.core5.http.ParseException;
import org.imgscalr.Scalr;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Plugin(version = BuildConfig.VERSION_CODE, colorLight = "#23CF5F", colorDark = "#000000")
public class TouchPortalSpotifyPlugin extends TouchPortalPlugin implements TouchPortalPlugin.TouchPortalPluginListener {
    public static final String PLUGIN_HOME_URL = "https://www.christophecvb.com/touch-portal/plugins/spotify/";
    public static final String PLUGIN_CONFIG_URL = TouchPortalSpotifyPlugin.PLUGIN_HOME_URL + "plugin.config";
    public static final String PLUGIN_UPDATE_URL = TouchPortalSpotifyPlugin.PLUGIN_HOME_URL + "?update=true&from=" + BuildConfig.VERSION_CODE;
    public static final String REDIRECT_URI = TouchPortalSpotifyPlugin.PLUGIN_HOME_URL + "oauth2";
    public static final String KEY_SPOTIFY_CLIENT_ID = "spotify.clientid";
    public static final String KEY_SPOTIFY_CLIENT_SECRET = "spotify.clientsecret";
    public static final String KEY_SPOTIFY_OAUTH_ACCESS_TOKEN = "spotify.oauthaccestoken";
    public static final String KEY_SPOTIFY_OAUTH_REFRESH_TOKEN = "spotify.oauthrefreshtoken";
    public static final String KEY_STATES_UPDATE_INTERVAL = "states.updateInterval";
    public static final String KEY_IMAGE_SIZE = "image.size";
    private static final String[] SCOPES = new String[]{"streaming", "user-read-playback-state", "user-modify-playback-state", "user-library-modify", "user-library-read", "playlist-modify-private", "playlist-modify-public", "playlist-read-collaborative", "playlist-read-private"};
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
    private static final String STATE_VALUE_ENABLED = "Enabled";
    private static final String STATE_VALUE_DISABLED = "Disabled";
    private static final String STATE_VALUE_LIKED = "Liked";
    private static final String STATE_VALUE_DISLIKED = "Disliked";
    private static final String STATE_VALUE_PLAYING = "Playing";
    private static final String STATE_VALUE_PAUSED = "Paused";
    private static final String STATE_VALUE_MUTED = "Muted";
    private static final String STATE_VALUE_UNMUTED = "Unmuted";

    private SpotifyApi spotifyAPI;
    private OAuth2LocalServerReceiver oAuth2LocalServerReceiver;

    private int lastKnownPositiveVolume = 100;
    private final ArrayList<PlaylistSimplified> userPlaylists = new ArrayList<>();
    private final HashMap<String, String> base64Images = new HashMap<>();

    private ScheduledExecutorService scheduledExecutorService;

    @State(defaultValue = "100", desc = "Spotify Current Volume (0 - 100)", categoryId = "BaseCategory")
    private String currentVolume;
    @State(defaultValue = "", desc = "Spotify Current Artist Name", categoryId = "BaseCategory")
    private String currentArtistName;
    @State(defaultValue = "", desc = "Spotify Current Track Name", categoryId = "BaseCategory")
    private String currentTrackName;
    @State(defaultValue = "", desc = "Spotify Current Track Album Name", categoryId = "BaseCategory")
    private String currentTrackAlbumName;
    @State(defaultValue = "", desc = "Spotify Current Track Image", categoryId = "BaseCategory")
    private String currentTrackImage;
    @State(defaultValue = "", desc = "Spotify Current Track URL", categoryId = "BaseCategory")
    private String currentTrackUrl;
    @State(defaultValue = "", desc = "Spotify Current Playlist Image", categoryId = "BaseCategory")
    private String currentPlaylistImage;
    @State(defaultValue = "", desc = "Spotify Current Playlist Name", categoryId = "BaseCategory")
    private String currentPlaylistName;
    @Event(valueChoices = {TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_TRACK, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_CONTEXT, TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_OFF}, format = "When Repeat Mode changes to $val", name = "When Repeat Mode changes")
    @State(defaultValue = TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_REPEAT_OFF, desc = "Spotify Current Repeat Mode", categoryId = "BaseCategory")
    private String currentRepeatMode;
    @Event(valueChoices = {TouchPortalSpotifyPlugin.STATE_VALUE_ENABLED, TouchPortalSpotifyPlugin.STATE_VALUE_DISABLED}, format = "When Shuffle Mode changes to $val", name = "When Shuffle Mode changes")
    @State(defaultValue = TouchPortalSpotifyPlugin.STATE_VALUE_DISABLED, desc = "Spotify Current Shuffle Mode", categoryId = "BaseCategory")
    private String currentShuffleMode;
    @Event(valueChoices = {TouchPortalSpotifyPlugin.STATE_VALUE_LIKED, TouchPortalSpotifyPlugin.STATE_VALUE_DISLIKED}, format = "When Current Track Like status changes to $val", name = "When Current Track Like status changes")
    @State(defaultValue = "", desc = "Spotify Current Track Like Status", categoryId = "BaseCategory")
    private String currentTrackLikeStatus;
    @Event(valueChoices = {TouchPortalSpotifyPlugin.STATE_VALUE_PLAYING, TouchPortalSpotifyPlugin.STATE_VALUE_PAUSED}, format = "When Current Playback status changes to $val", name = "When Current Playback status changes")
    @State(defaultValue = TouchPortalSpotifyPlugin.STATE_VALUE_PAUSED, desc = "Spotify Current Playback Status", categoryId = "BaseCategory")
    private String currentPlaybackStatus;
    @Event(valueChoices = {TouchPortalSpotifyPlugin.STATE_VALUE_MUTED, TouchPortalSpotifyPlugin.STATE_VALUE_UNMUTED}, format = "When Current Mute status changes to $val", name = "When Current Mute status changes")
    @State(defaultValue = TouchPortalSpotifyPlugin.STATE_VALUE_MUTED, desc = "Spotify Current Mute Status", categoryId = "BaseCategory")
    private String currentMuteStatus;
    @State(defaultValue = "", desc = "Spotify Current Active Device", categoryId = "BaseCategory")
    private String currentActiveDevice;

    /**
     * Constructor
     */
    private TouchPortalSpotifyPlugin() {
        super(true);

        if (this.loadProperties("plugin.config")) {
            String clientId = this.getProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_CLIENT_ID);
            String clientSecret = this.getProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_CLIENT_SECRET);

            this.spotifyAPI = new SpotifyApi.Builder().setClientId(clientId).setClientSecret(clientSecret).setRedirectUri(URI.create(TouchPortalSpotifyPlugin.REDIRECT_URI)).build();

            String oAuthAccessToken = this.getProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_ACCESS_TOKEN);
            String oAuthRefreshToken = this.getProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_REFRESH_TOKEN);
            if (oAuthAccessToken == null || oAuthAccessToken.isEmpty()) {
                this.startOAuthProcess();
            }
            else {
                this.checkForUpdate();
                this.spotifyAPI.setAccessToken(oAuthAccessToken);
                this.spotifyAPI.setRefreshToken(oAuthRefreshToken);
                this.onSpotifyAPIReady();
            }
        }
        else {
            System.out.println("Could not read plugin.config");
        }
    }

    public static void main(String[] args) {
        if (args != null && args.length == 1) {
            if (PluginHelper.COMMAND_START.equals(args[0])) {
                // Initialize the Plugin
                new TouchPortalSpotifyPlugin();
            }
        }
    }

    private void onSpotifyAPIReady() {
        boolean connectedPairedAndListening = this.connectThenPairAndListen(this);

        if (connectedPairedAndListening) {
            this.startUpdatingStatesAndChoices(3);
        }
    }

    private synchronized void startOAuthProcess() {
        if (this.oAuth2LocalServerReceiver == null) {
            this.oAuth2LocalServerReceiver = new OAuth2LocalServerReceiver.Builder().setCallbackPath("/oauth").build();
            this.oAuth2LocalServerReceiver.waitForCode(this.spotifyAPI.authorizationCodeUri()
                            .scope(String.join(",", TouchPortalSpotifyPlugin.SCOPES))
                            .state(this.oAuth2LocalServerReceiver.getPort() + "")
                            .build()
                            .execute(),
                    (oAuth2Code, oAuth2Error) -> {
                        if (oAuth2Error == null) {
                            try {
                                System.out.println("Spotify OAuth Code: " + oAuth2Code);
                                AuthorizationCodeCredentials credentials = this.spotifyAPI.authorizationCode(oAuth2Code).build().execute();
                                String oAuthAccessToken = credentials.getAccessToken();
                                String oAuthRefreshToken = credentials.getRefreshToken();
                                this.setProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_ACCESS_TOKEN, oAuthAccessToken);
                                this.setProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_REFRESH_TOKEN, oAuthRefreshToken);
                                this.storeProperties();
                                this.spotifyAPI.setAccessToken(oAuthAccessToken);
                                this.spotifyAPI.setRefreshToken(oAuthRefreshToken);

                                this.oAuth2LocalServerReceiver = null;

                                this.onSpotifyAPIReady();
                            }
                            catch (SpotifyWebApiException | IOException | ParseException exception) {
                                System.out.println("OAuth Process Failed: " + exception.getMessage());
                            }
                        }
                    });
        }
    }

    private void checkForUpdate() {
        if (this.isUpdateAvailable(TouchPortalSpotifyPlugin.PLUGIN_CONFIG_URL, BuildConfig.VERSION_CODE)) {
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

    private void startUpdatingStatesAndChoices(long initialDelay) {
        if (this.scheduledExecutorService != null) {
            this.scheduledExecutorService.shutdownNow();
        }
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.scheduledExecutorService.schedule(this::updateStatesAndChoices, initialDelay, TimeUnit.SECONDS);
    }

    private void updateStatesAndChoices() {
        System.out.println("Update States And Choices : NOW");
        long nextSchedule = 30;
        try {
            nextSchedule = Long.parseLong(this.getProperty(TouchPortalSpotifyPlugin.KEY_STATES_UPDATE_INTERVAL));
        }
        catch (NumberFormatException ignored) {}
        try {
            CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
            String currentActiveDevice = "";
            if (playbackInfo == null || playbackInfo.getDevice() == null) {
                nextSchedule = 60;
            }
            else {
                currentActiveDevice = playbackInfo.getDevice().getName();
                this.updateStates(playbackInfo);
                this.updateChoices();
            }
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentActiveDevice.ID, currentActiveDevice, true);

            this.scheduledExecutorService.schedule(this::updateStatesAndChoices, Math.max(nextSchedule, 10), TimeUnit.SECONDS);
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            this.handleSpotifyWebApiException(spotifyWebApiException, this::updateStatesAndChoices);
        }
    }

    private void updateChoices() {
        this.updateAvailableDevices();
        this.updateCurrentUserPlaylists();
    }

    private void updateStates(CurrentlyPlayingContext playbackInfo) {
        this.updateCurrentVolumeAndMuteStatus(playbackInfo);
        this.updateCurrentTrackFromPlaybackInfo(playbackInfo);
        this.updateCurrentPlaylist(playbackInfo);
        this.updateCurrentRepeatMode(playbackInfo);
        this.updateCurrentShuffleMode(playbackInfo);
        this.updateCurrentPlaybackStatus(playbackInfo);
    }

    private void updateStates() {
        try {
            CurrentlyPlayingContext playbackInfo = this.spotifyAPI.getInformationAboutUsersCurrentPlayback().build().execute();
            this.updateStates(playbackInfo);
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
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
        String displayableRepeatMode = null;
        if (repeatMode != null) {
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
        }
        return displayableRepeatMode;
    }

    private void updateCurrentShuffleMode(CurrentlyPlayingContext playbackInfo) {
        String displayableShuffleMode = playbackInfo != null && playbackInfo.getShuffle_state() ? TouchPortalSpotifyPlugin.STATE_VALUE_ENABLED : TouchPortalSpotifyPlugin.STATE_VALUE_DISABLED;
        this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentShuffleMode.ID, displayableShuffleMode);
    }

    private void updateCurrentPlaybackStatus(CurrentlyPlayingContext playbackInfo) {
        this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentPlaybackStatus.ID, playbackInfo != null && playbackInfo.getIs_playing() ? TouchPortalSpotifyPlugin.STATE_VALUE_PLAYING : TouchPortalSpotifyPlugin.STATE_VALUE_PAUSED);
    }

    private void updateCurrentTrackFromPlaybackInfo(CurrentlyPlayingContext playbackInfo) {
        this.updateCurrentTrack(playbackInfo != null && playbackInfo.getItem().getType() == ModelObjectType.TRACK ? (Track) playbackInfo.getItem() : null);
    }

    private void updateCurrentTrack(Track currentTrack) {
        this.updateCurrentTrackName(currentTrack);
        this.updateCurrentTrackImage(currentTrack);
        this.updateCurrentTrackUrl(currentTrack);
        this.updateCurrentTrackLikeStatus(currentTrack);
        this.updateCurrentArtistName(currentTrack);
        this.updateCurrentTrackAlbumName(currentTrack);
    }

    private void updateCurrentTrackLikeStatus(Track currentTrack) {
        try {
            if (currentTrack != null) {
                Boolean liked = this.spotifyAPI.checkUsersSavedTracks(currentTrack.getId()).build().execute()[0];
                this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackLikeStatus.ID, liked ? TouchPortalSpotifyPlugin.STATE_VALUE_LIKED : TouchPortalSpotifyPlugin.STATE_VALUE_DISLIKED);
            }
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
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
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentArtistName.ID, "", true);
        }
    }

    private void updateCurrentTrackName(Track currentTrack) {
        if (currentTrack != null) {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackName.ID, currentTrack.getName());
        }
        else {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackName.ID, "", true);
        }
    }

    private void updateCurrentTrackAlbumName(Track currentTrack) {
        if (currentTrack != null && currentTrack.getAlbum() != null) {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackAlbumName.ID, currentTrack.getAlbum().getName());
        }
        else {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackAlbumName.ID, "", true);
        }
    }

    private void updateCurrentTrackUrl(Track currentTrack) {
        if (currentTrack != null) {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackUrl.ID, currentTrack.getExternalUrls() != null ? currentTrack.getExternalUrls().get("spotify") : "");
        }
        else {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackUrl.ID, "", true);
        }
    }

    private void updateCurrentTrackImage(Track currentTrack) {
        boolean imageSent = false;
        if (currentTrack != null) {
            if (currentTrack.getAlbum().getImages().length > 0) {
                Image albumImage = currentTrack.getAlbum().getImages()[0];
                this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackImage.ID, this.imageUrlToBase64(albumImage.getUrl()), true);
                imageSent = true;
            }
        }
        if (!imageSent) {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackImage.ID, "", true);
        }
    }

    private String imageUrlToBase64(String imageUrl) {
        String base64 = "";
        if (this.base64Images.containsKey(imageUrl)) {
            base64 = this.base64Images.get(imageUrl);
        }
        else {
            ByteArrayOutputStream byteArrayOutputStream = null;
            try {
                int imageSize = Integer.parseInt(this.getProperty(TouchPortalSpotifyPlugin.KEY_IMAGE_SIZE, "256"));
                String finalImageUrl = imageUrl;
                if (!imageUrl.contains("==/default") && imageUrl.contains("/default")) {
                    finalImageUrl = imageUrl.replace("/default", "");
                }
                BufferedImage bufferedImage = ImageIO.read(new URL(finalImageUrl));
                BufferedImage resizedBufferedImage = Scalr.resize(bufferedImage, imageSize);

                ImageIO.write(resizedBufferedImage, "jpg", byteArrayOutputStream = new ByteArrayOutputStream());
                base64 = Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
                this.base64Images.put(imageUrl, base64);
            }
            catch (Exception exception) {
                System.out.println(exception.getMessage() + " for Image URL: " + imageUrl);
            }
            finally {
                if (byteArrayOutputStream != null) {
                    try {
                        byteArrayOutputStream.close();
                    }
                    catch (IOException ignored) {}
                }
            }
        }
        return base64;
    }

    private void updateCurrentPlaylist(CurrentlyPlayingContext playbackInfo) {
        try {
            if (playbackInfo != null && playbackInfo.getContext() != null) {
                if (playbackInfo.getContext().getType() == ModelObjectType.PLAYLIST) {
                    String[] playlistUriParts = playbackInfo.getContext().getUri().split(":");
                    Playlist playlist = this.spotifyAPI.getPlaylist(playlistUriParts[playlistUriParts.length - 1]).build().execute(); // FIXME: Call to get current playlist data

                    this.updateCurrentPlaylistName(playlist);
                    this.updateCurrentPlaylistImage(playlist);
                }
                else {
                    this.updateCurrentPlaylistName(null);
                    this.updateCurrentPlaylistImage(null);
                }
            }
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.updateCurrentPlaylist(playbackInfo));
        }
    }

    private void updateCurrentPlaylistName(Playlist playlist) {
        boolean nameSent = false;
        if (playlist != null) {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentPlaylistName.ID, playlist.getName());
            nameSent = true;
        }
        if (!nameSent) {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentPlaylistName.ID, "", true);
        }
    }

    private void updateCurrentPlaylistImage(Playlist playlist) {
        boolean imageSent = false;
        if (playlist != null) {
            if (playlist.getImages().length > 0) {
                this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentPlaylistImage.ID, this.imageUrlToBase64(playlist.getImages()[0].getUrl()), true);
                imageSent = true;
            }
        }
        if (!imageSent) {
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentPlaylistImage.ID, "", true);
        }
    }

    private void updateCurrentVolumeAndMuteStatus(CurrentlyPlayingContext playbackInfo) {
        if (playbackInfo != null && playbackInfo.getDevice() != null) {
            Device activeDevice = playbackInfo.getDevice();
            if (activeDevice.getVolume_percent() > 0) {
                this.lastKnownPositiveVolume = activeDevice.getVolume_percent();
            }
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentVolume.ID, activeDevice.getVolume_percent() + "");
            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentMuteStatus.ID, activeDevice.getVolume_percent() > 0 ? TouchPortalSpotifyPlugin.STATE_VALUE_UNMUTED : TouchPortalSpotifyPlugin.STATE_VALUE_MUTED);
        }
    }

    private void updateAvailableDevices() {
        try {
            ArrayList<StoredDevice> storedDevices = this.getStoredDevices();
            Device[] availableDevices = this.spotifyAPI.getUsersAvailableDevices().build().execute(); // FIXME: Call to get Available devices
            for (Device availableDevice : availableDevices) {
                StoredDevice discoveredDevice = new StoredDevice(availableDevice);
                storedDevices.remove(discoveredDevice);
                storedDevices.add(discoveredDevice);
            }
            this.setStoredDevices(storedDevices);
            String[] deviceNames = storedDevices.stream().map(storedDevice -> storedDevice.name).toArray(String[]::new);
            this.sendChoiceUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.Actions.PlayerStartPlayingThroughDevice.Devices.ID, deviceNames);
            this.sendChoiceUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.Actions.PlayerTransferPlaybackToDevice.Devices.ID, deviceNames);
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            this.handleSpotifyWebApiException(spotifyWebApiException, this::updateAvailableDevices);
        }
    }

    private ArrayList<StoredDevice> getStoredDevices() {
        ArrayList<StoredDevice> storedDevices = new ArrayList<>();

        String rawDiscoveredDevices = this.getProperty("plugin.discoveredDevices");
        if (rawDiscoveredDevices != null && !rawDiscoveredDevices.isEmpty()) {
            for (String rawDevice : rawDiscoveredDevices.split("::")) {
                StoredDevice storedDevice = new StoredDevice(rawDevice);
                storedDevices.add(storedDevice);
            }
        }

        return storedDevices;
    }

    private void setStoredDevices(ArrayList<StoredDevice> storedDevices) {
        ArrayList<StoredDevice> alreadyStoredDevices = this.getStoredDevices();
        for (StoredDevice storedDevice : storedDevices) {
            if (!alreadyStoredDevices.contains(storedDevice)) {
                alreadyStoredDevices.add(storedDevice);
            }
        }
        String rawDiscoveredDevices = String.join("::", alreadyStoredDevices.stream().map(StoredDevice::toString).toArray(String[]::new));
        this.setProperty("plugin.discoveredDevices", rawDiscoveredDevices);
        this.storeProperties();
    }

    private void updateCurrentUserPlaylists() {
        try {
            Paging<PlaylistSimplified> paginatedUserPlaylists = this.spotifyAPI.getListOfCurrentUsersPlaylists().limit(50).build().execute(); // FIXME: Call to get User's playlists
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
            for (StoredDevice storedDevice : this.getStoredDevices()) {
                if (devices[0].equals(storedDevice.name)) {
                    this.spotifyAPI.startResumeUsersPlayback().device_id(storedDevice.id).build().execute();
                    System.out.println("Spotify: Playback Start through Device: " + devices[0]);
                    Thread.sleep(250);
                    this.updateStates();
                    break;
                }
            }
        }
        catch (IOException | ParseException | InterruptedException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerStartPlayingThroughDevice(devices));
        }
    }

    @Action(name = "Transfer Playback to Device", description = "Transfer Playback to selected Device", format = "Transfer Playback to {$devices$}", categoryId = "BaseCategory")
    private void playerTransferPlaybackToDevice(@Data(label = "Device") String[] devices) {
        try {
            for (StoredDevice storedDevice : this.getStoredDevices()) {
                if (devices[0].equals(storedDevice.name)) {
                    JsonArray transferDevices = new JsonArray();
                    transferDevices.add(storedDevice.id);
                    this.spotifyAPI.transferUsersPlayback(transferDevices).play(true).build().execute();
                    System.out.println("Spotify: Transfer Playback to Device: " + devices[0]);
                    Thread.sleep(250);
                    this.updateStates();
                    break;
                }
            }
        }
        catch (IOException | ParseException | InterruptedException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playerTransferPlaybackToDevice(devices));
        }
    }

    //    @Action(name = "Playback Start/Resume", prefix = "Spotify Player", description = "Playback Start/Resume", categoryId = "BaseCategory")
    private void playerStartResume() {
        try {
            Device activeDevice = this.getActiveDevice();
            if (activeDevice != null) {
                this.spotifyAPI.startResumeUsersPlayback().device_id(activeDevice.getId()).build().execute();
                System.out.println("Spotify: Playback Start/Resume");
                this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentPlaybackStatus.ID, TouchPortalSpotifyPlugin.STATE_VALUE_PLAYING);
            }
            else {
                System.out.println("Spotify: No Active Device Found");
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            this.handleSpotifyWebApiException(spotifyWebApiException, this::playerStartResume);
        }
    }

    //    @Action(name = "Playback Pause", prefix = "Spotify Player", description = "Playback Pause", categoryId = "BaseCategory")
    private void playerPause() {
        try {
            Device activeDevice = this.getActiveDevice();
            if (activeDevice != null) {
                this.spotifyAPI.pauseUsersPlayback().device_id(activeDevice.getId()).build().execute();
                System.out.println("Spotify: Playback Pause");
                this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentPlaybackStatus.ID, TouchPortalSpotifyPlugin.STATE_VALUE_PAUSED);
            }
            else {
                System.out.println("Spotify: No Active Device Found");
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
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
                this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentMuteStatus.ID, volume > 0 ? TouchPortalSpotifyPlugin.STATE_VALUE_UNMUTED : TouchPortalSpotifyPlugin.STATE_VALUE_MUTED);
            }
            else {
                System.out.println("Spotify: No Active Device Found");
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
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
                        this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackLikeStatus.ID, TouchPortalSpotifyPlugin.STATE_VALUE_LIKED);
                        break;

                    case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_DISLIKE:
                        this.spotifyAPI.removeUsersSavedTracks(currentTrack.getId()).build().execute();
                        this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackLikeStatus.ID, TouchPortalSpotifyPlugin.STATE_VALUE_DISLIKED);
                        break;

                    case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE:
                        if (this.spotifyAPI.checkUsersSavedTracks(currentTrack.getId()).build().execute()[0]) {
                            this.spotifyAPI.removeUsersSavedTracks(currentTrack.getId()).build().execute();
                            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackLikeStatus.ID, TouchPortalSpotifyPlugin.STATE_VALUE_DISLIKED);
                        }
                        else {
                            this.spotifyAPI.saveTracksForUser(currentTrack.getId()).build().execute();
                            this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentTrackLikeStatus.ID, TouchPortalSpotifyPlugin.STATE_VALUE_LIKED);
                        }
                        break;
                }
                System.out.println("Spotify: Track " + likeDislikeActions[0]);
            }
        }
        catch (ParseException | IOException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
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
                this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.playlistStart(playlistNames));
            }
        }
    }

    @Action(name = "Start Playing Liked Songs", prefix = "Spotify Playlist", description = "Start playing Liked Songs", categoryId = "BaseCategory")
    private void likedSongsStart() {
        try {
            ArrayList<SavedTrack> savedTracks = new ArrayList<>();
            Paging<SavedTrack> pagedUserSavedTracks = this.spotifyAPI.getUsersSavedTracks().limit(50).build().execute();
            Collections.addAll(savedTracks, pagedUserSavedTracks.getItems());
            while (pagedUserSavedTracks.getNext() != null) {
                pagedUserSavedTracks = this.spotifyAPI.getUsersSavedTracks().limit(50).offset(pagedUserSavedTracks.getOffset()).build().execute();
            }

            JsonArray savedTracksUris = new JsonArray();
            for (SavedTrack savedTrack : savedTracks) {
                savedTracksUris.add(savedTrack.getTrack().getUri());
            }


            this.spotifyAPI.startResumeUsersPlayback().uris(savedTracksUris).build().execute();
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            this.handleSpotifyWebApiException(spotifyWebApiException, this::likedSongsStart);
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
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_DISABLE:
                    shuffleMode = false;
                    this.spotifyAPI.toggleShuffleForUsersPlayback(false).build().execute();
                    break;

                case TouchPortalSpotifyPlugin.ACTION_DATA_CHOICE_TOGGLE:
                    if (playbackInfo != null) {
                        shuffleMode = !playbackInfo.getShuffle_state();
                        this.spotifyAPI.toggleShuffleForUsersPlayback(shuffleMode).build().execute();
                    }
                    break;
            }
            System.out.println("Spotify: Player Shuffle Mode: " + shuffleMode);
            if (shuffleMode != null) {
                this.sendStateUpdate(TouchPortalSpotifyPluginConstants.BaseCategory.States.CurrentShuffleMode.ID, shuffleMode ? TouchPortalSpotifyPlugin.STATE_VALUE_ENABLED : TouchPortalSpotifyPlugin.STATE_VALUE_DISABLED);
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
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
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.saveCurrentTrackToPlaylist(playlistNames));
        }
    }

    @Action(name = "Remove Current Track from Current Playlist", prefix = "Spotify Playlist", description = "Remove Current Track from Current Playlist", categoryId = "BaseCategory")
    private void removeCurrentTrackFromCurrentPlaylist() {
        try {
            CurrentlyPlaying currentPlaying = this.spotifyAPI.getUsersCurrentlyPlayingTrack().build().execute();

            Track currentTrack = null;
            if (currentPlaying != null) {
                if (currentPlaying.getItem().getType() == ModelObjectType.TRACK) {
                    currentTrack = (Track) currentPlaying.getItem();
                }
            }
            Playlist currentPlaylist = null;
            if (currentPlaying != null) {
                if (currentPlaying.getContext().getType() == ModelObjectType.PLAYLIST) {
                    String[] playlistParts = currentPlaying.getContext().getUri().split(":");
                    currentPlaylist = this.spotifyAPI.getPlaylist(playlistParts[playlistParts.length - 1]).build().execute();
                }
            }

            if (currentTrack != null && currentPlaylist != null) {
                JsonArray tracksToRemove = new JsonArray();
                JsonObject trackToRemove = new JsonObject();
                trackToRemove.addProperty("uri", currentTrack.getUri());
                tracksToRemove.add(trackToRemove);
                this.spotifyAPI.removeItemsFromPlaylist(currentPlaylist.getId(), tracksToRemove).build().execute();
                System.out.println("Spotify: Remove Track: [" + currentTrack.getName() + "] from Current Playlist [" + currentPlaylist.getName() + "]");
            }
            else {
                System.out.println("Spotify: Remove Current Track from Current Playlist: No Track or Playlist");
            }
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            this.handleSpotifyWebApiException(spotifyWebApiException, this::removeCurrentTrackFromCurrentPlaylist);
        }
    }

    @Action(name = "Start Resource by ID", prefix = "Spotify Start", description = "Start playing a Resource (Album/Playlist/Artist/Track) by ID", format = "Start Resource with ID {$resourceUri$}", categoryId = "BaseCategory")
    private void startResourceByID(@Data(label = "Resource URI") String resourceUri) {
        try {
            if (resourceUri.startsWith("spotify:track:")) {
                JsonArray uris = new JsonArray();
                uris.add(resourceUri);
                this.spotifyAPI.startResumeUsersPlayback().uris(uris).build().execute();
            }
            else {
                this.spotifyAPI.startResumeUsersPlayback().context_uri(resourceUri).build().execute();
            }
            System.out.println("Spotify: Start Resource by ID: " + resourceUri);
        }
        catch (IOException | ParseException ignored) {}
        catch (SpotifyWebApiException spotifyWebApiException) {
            this.handleSpotifyWebApiException(spotifyWebApiException, () -> this.startResourceByID(resourceUri));
        }
    }

    private synchronized void handleSpotifyWebApiException(SpotifyWebApiException spotifyWebApiException, Runnable runnable) {
        System.out.println("SpotifyWebApiException: " + spotifyWebApiException.getClass().getSimpleName() + " - " + spotifyWebApiException.getMessage());
        if (spotifyWebApiException instanceof UnauthorizedException) {
            try {
                System.out.println("Spotify RefreshToken: " + this.spotifyAPI.getRefreshToken());
                AuthorizationCodeCredentials credentials = this.spotifyAPI.authorizationCodeRefresh().build().execute();

                String oAuthAccessToken = credentials.getAccessToken();
                this.setProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_ACCESS_TOKEN, oAuthAccessToken);
                this.spotifyAPI.setAccessToken(oAuthAccessToken);

                String oAuthRefreshToken = credentials.getRefreshToken();
                if (oAuthRefreshToken != null && !oAuthRefreshToken.isEmpty()) {
                    this.setProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_REFRESH_TOKEN, oAuthRefreshToken);
                    this.spotifyAPI.setRefreshToken(oAuthRefreshToken);
                }
                this.storeProperties();

                runnable.run();
            }
            catch (IOException | ParseException | SpotifyWebApiException exception) {
                if (exception instanceof TooManyRequestsException) {
                    System.out.println(exception.getMessage());
                }
                else {
                    this.removeProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_ACCESS_TOKEN);
                    this.removeProperty(TouchPortalSpotifyPlugin.KEY_SPOTIFY_OAUTH_REFRESH_TOKEN);
                    this.storeProperties();
                    exception.printStackTrace();
                    this.startOAuthProcess();
                }
            }
        }
        else if (spotifyWebApiException instanceof TooManyRequestsException) {
            TooManyRequestsException tooManyRequestsException = (TooManyRequestsException) spotifyWebApiException;
            System.out.println(tooManyRequestsException.getMessage() + ": Retry After " + tooManyRequestsException.getRetryAfter());
            this.startUpdatingStatesAndChoices(tooManyRequestsException.getRetryAfter());
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
    public void onDisconnected(Exception exception) {
        this.scheduledExecutorService.shutdownNow();
        System.exit(0);
    }

    @Override
    public void onReceived(JsonObject jsonMessage) {
    }

    @Override
    public void onInfo(TPInfoMessage tpInfoMessage) {
    }

    @Override
    public void onListChanged(TPListChangeMessage tpListChangeMessage) {
    }

    @Override
    public void onBroadcast(TPBroadcastMessage tpBroadcastMessage) {
    }

    @Override
    public void onSettings(TPSettingsMessage tpSettingsMessage) {
    }

    private enum Categories {
        @Category(name = "Spotify", imagePath = "images/icon-24.png")
        BaseCategory
    }

    private static class StoredDevice {
        public String name;
        public String id;

        public StoredDevice(Device device) {
            this.name = device.getName();
            this.id = device.getId();
        }

        public StoredDevice(String rawDevice) {
            String[] split = rawDevice.split(":SD:");
            this.name = split[0];
            this.id = split[1];
        }

        @Override
        public String toString() {
            return this.name + ":SD:" + this.id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StoredDevice that = (StoredDevice) o;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
}
