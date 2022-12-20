package com.github.ChristopheCVB.TouchPortal.Spotify.action;

import com.christophecvb.touchportal.TPAction;
import com.christophecvb.touchportal.TouchPortalPlugin;
import com.christophecvb.touchportal.annotations.Action;
import com.christophecvb.touchportal.annotations.Data;
import com.christophecvb.touchportal.model.TPListChangedMessage;
import com.github.ChristopheCVB.TouchPortal.Spotify.TouchPortalSpotifyPlugin;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.miscellaneous.Device;
import java.io.IOException;
import java.util.logging.Logger;
import org.apache.hc.core5.http.ParseException;

@Action(
    name = "Add Resource ID to Queue",
    format = "Add Resource ID {$resourceUri$} to Queue",
    categoryId = "BaseCategory"
)
public class AddToQueue extends TPAction<TouchPortalSpotifyPlugin> {

  private static final Logger LOGGER = Logger.getLogger(TouchPortalPlugin.class.getName());

  @Data
  private String resourceUri;

  public AddToQueue(TouchPortalSpotifyPlugin touchPortalPlugin) {
    super(touchPortalPlugin);
  }

  @Override
  public void onInvoke() {
    Device lastActiveDevice = this.touchPortalPlugin.getLastActiveDevice();
    if (lastActiveDevice != null) {
      LOGGER.info("Add Resource ID to Queue: " + this.resourceUri);
      try {
        this.touchPortalPlugin.getSpotifyAPI().addItemToUsersPlaybackQueue(this.resourceUri).device_id(lastActiveDevice.getId()).build().execute();
      }
      catch (IOException | ParseException ignored) {
      }
      catch (SpotifyWebApiException spotifyWebApiException) {
        this.touchPortalPlugin.handleSpotifyWebApiException(spotifyWebApiException, this::onInvoke);
      }
    }
    else {
      LOGGER.info("No Device Found");
    }
  }

  @Override
  public void onListChanged(TPListChangedMessage tpListChangedMessage) {}
}
