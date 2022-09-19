/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.Application;
import software.wings.beans.Event;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.atmosphere.cpr.BroadcasterFactory;

@Singleton
@OwnedBy(CDC)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class EventEmitter {
  @Inject private BroadcasterFactory broadcasterFactory;
  @Inject private AppService appService;

  /**
   * Send.
   *
   * @param channel the channel
   * @param event   the event
   */
  public void send(Channel channel, Event event) {
    if ("*".equals(event.getOrgId()) && !"all".equals(event.getAppId())) {
      Application application = appService.get(event.getAppId());
      event.setOrgId(application.getAccountId());
    }
    broadcasterFactory
        .lookup("/stream/" + channel.getTarget() + "/" + event.getOrgId() + "/" + event.getAppId() + "/"
                + event.getEnvId() + "/" + event.getServiceId() + "/" + channel.getChannelName(),
            true)
        .broadcast(event);
    if (isNotBlank(event.getUuid())) {
      broadcasterFactory
          .lookup("/stream/" + channel.getTarget() + "/" + event.getOrgId() + "/" + event.getAppId() + "/"
                  + event.getEnvId() + "/" + event.getServiceId() + "/" + channel.getChannelName() + "/"
                  + event.getUuid(),
              true)
          .broadcast(event);
    }
  }

  /**
   * The enum Channel.
   */
  public enum Channel {
    /**
     * Artifacts channel.
     */
    ARTIFACTS("artifacts", "ui", ResourceType.ARTIFACT),
    /**
     * Activities channel.
     */
    ACTIVITIES("activities", "ui", ResourceType.ENVIRONMENT),

    private String channelName;
    private ResourceType permission;
    private String target;
    private PermissionType scope = PermissionType.APP;

    Channel(String channelName, String target, ResourceType permission) {
      this.channelName = channelName;
      this.permission = permission;
      this.target = target;
    }

    Channel(String channelName, String target, ResourceType permission, PermissionType scope) {
      this.channelName = channelName;
      this.permission = permission;
      this.scope = scope;
      this.target = target;
    }

    /**
     * Gets channel by channel name.
     *
     * @param channelName the channel name
     * @return the channel by channel name
     */
    public static Channel getChannelByChannelName(String channelName) {
      for (Channel channel : values()) {
        if (channel.getChannelName().equalsIgnoreCase(channelName)) {
          return channel;
        }
      }
      return null;
    }

    /**
     * Getter for property 'channelName'.
     *
     * @return Value for property 'channelName'.
     */
    public String getChannelName() {
      return channelName;
    }

    /**
     * Gets permission.
     *
     * @return the permission
     */
    public ResourceType getPermission() {
      return permission;
    }

    /**
     * Gets scope.
     *
     * @return the scope
     */
    public PermissionType getScope() {
      return scope;
    }

    public String getTarget() {
      return target;
    }
  }
}
