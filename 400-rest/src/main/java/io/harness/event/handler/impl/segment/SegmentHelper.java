/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.handler.impl.segment;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.event.model.EventConstants.ACCOUNT_STATUS;
import static io.harness.event.model.EventConstants.DAYS_LEFT_IN_TRIAL;
import static io.harness.event.model.EventConstants.EMAIL;
import static io.harness.event.model.EventConstants.FIRST_NAME;
import static io.harness.event.model.EventConstants.GROUP_ID;
import static io.harness.event.model.EventConstants.LAST_NAME;
import static io.harness.event.model.EventConstants.OAUTH_PROVIDER;
import static io.harness.event.model.EventConstants.USER_INVITE_URL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.event.handler.impl.Utils;
import io.harness.event.handler.segment.SegmentConfig;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.LicenseInfo;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.segment.analytics.Analytics;
import com.segment.analytics.messages.GroupMessage;
import com.segment.analytics.messages.IdentifyMessage;
import com.segment.analytics.messages.IdentifyMessage.Builder;
import com.segment.analytics.messages.TrackMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 11/20/18
 */
@OwnedBy(PL)
@Singleton
@Slf4j
public class SegmentHelper {
  @Inject private Utils utils;
  private Analytics analytics;

  @Inject
  public SegmentHelper(MainConfiguration config) {
    SegmentConfig segmentConfig = config.getSegmentConfig();
    if (segmentConfig != null && segmentConfig.isEnabled() && isNotEmpty(segmentConfig.getApiKey())) {
      try {
        this.analytics = Analytics.builder(segmentConfig.getApiKey()).build();
      } catch (Exception ex) {
        log.error("Error while initializing Segment configuration", ex);
      }
    }
  }

  public String createOrUpdateIdentity(String userId, String email, String userName, Account account,
      String userInviteUrl, String oauthProvider, Map<String, Boolean> integrations) {
    log.info("Updating identity {} to segment", userId);

    Builder identityBuilder = IdentifyMessage.builder();

    String identity;
    if (isEmpty(userId)) {
      identity = UUIDGenerator.generateUuid();
      identityBuilder.anonymousId(identity);
    } else {
      identity = userId;
      identityBuilder.userId(identity);
    }

    Map<String, String> traits = new HashMap<>();
    traits.put(EMAIL, email);
    String firstName = utils.getFirstName(userName, email);
    traits.put(FIRST_NAME, isNotEmpty(firstName) ? firstName : email);
    String lastName = utils.getLastName(userName, email);
    traits.put(LAST_NAME, isNotEmpty(lastName) ? lastName : "");

    if (account != null) {
      traits.put(GROUP_ID, account.getUuid());

      LicenseInfo licenseInfo = account.getLicenseInfo();
      if (licenseInfo != null) {
        traits.put(ACCOUNT_STATUS, licenseInfo.getAccountStatus());
        traits.put(DAYS_LEFT_IN_TRIAL, utils.getDaysLeft(licenseInfo.getExpiryTime()));
      }
    }

    if (isNotEmpty(userInviteUrl)) {
      traits.put(USER_INVITE_URL, userInviteUrl);
    }

    if (isNotEmpty(oauthProvider)) {
      traits.put(OAUTH_PROVIDER, oauthProvider);
    }

    identityBuilder.traits(traits);
    if (integrations != null) {
      integrations.forEach((k, v) -> {
        boolean value = false;
        if (v != null) {
          value = v.booleanValue();
        }
        identityBuilder.enableIntegration(k, value);
      });
    }
    enqueue(identityBuilder);
    return identity;
  }

  public boolean reportTrackEvent(
      String identity, String event, Map<String, String> properties, Map<String, Boolean> integrations) {
    TrackMessage.Builder track = TrackMessage.builder(event).properties(properties).userId(identity);
    if (integrations != null) {
      integrations.forEach((k, v) -> {
        boolean value = false;
        if (v != null) {
          value = v.booleanValue();
        }
        track.enableIntegration(k, value);
      });
    }
    enqueue(track);
    return true;
  }

  public void reportTrackEvent(List<String> userIds, String event, Map<String, String> properties) {
    userIds.forEach(userId -> reportTrackEvent(userId, event, properties, null));
  }

  public void enqueue(TrackMessage.Builder track) {
    if (analytics != null) {
      analytics.enqueue(track);
      log.debug("Sent Track event to segment");
    } else {
      log.info("Skipping sending track event to segment");
    }
  }

  public void enqueue(GroupMessage.Builder group) {
    if (analytics != null) {
      analytics.enqueue(group);
      log.info("Sent Group event to segment");
    } else {
      log.info("Skipping sending group event to segment");
    }
  }

  public void enqueue(IdentifyMessage.Builder identity) {
    if (analytics != null) {
      analytics.enqueue(identity);
      log.info("Sent Identity event to segment");
    } else {
      log.info("Skipping sending identity to segment");
    }
  }
}
