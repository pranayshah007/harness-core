/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.eventframework;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SETTINGS;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SETTINGS_CATEGORY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SETTINGS_GROUP_IDENTIFIER;

import static com.google.common.base.MoreObjects.firstNonNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.service.CEViewService;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.entity_crud.settings.SettingsEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingIdentifiers;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CE)
public class CCMSettingsCRUDStreamListener implements MessageListener {
  @Inject private CEViewService ceViewService;

  @Override
  public boolean handleMessage(final Message message) {
    if (Objects.nonNull(message) && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (isPerspectivePreferenceDefaultSettingsUpdateEvent(metadataMap)) {
        log.info("Perspective preference default settings update event, Message: {}", message);
        final SettingsEntityChangeDTO settingsEntityChangeDTO = getSettingsEntityChangeDTO(message);
        ceViewService.updateAllPerspectiveWithPerspectivePreferenceDefaultSettings(
            settingsEntityChangeDTO.getAccountIdentifier().getValue(),
            firstNonNull(settingsEntityChangeDTO.getSettingIdentifiersMap().keySet(), Collections.emptySet()));
      }
    }
    return true;
  }

  private boolean isPerspectivePreferenceDefaultSettingsUpdateEvent(final Map<String, String> metadataMap) {
    return metadataMap != null && SETTINGS.equals(metadataMap.get(ENTITY_TYPE))
        && SettingCategory.CE.name().equals(metadataMap.get(SETTINGS_CATEGORY))
        && SettingIdentifiers.PERSPECTIVE_PREFERENCES_GROUP_IDENTIFIER.equals(
            metadataMap.get(SETTINGS_GROUP_IDENTIFIER));
  }

  private SettingsEntityChangeDTO getSettingsEntityChangeDTO(final Message message) {
    SettingsEntityChangeDTO settingsEntityChangeDTO;
    try {
      settingsEntityChangeDTO = SettingsEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (final InvalidProtocolBufferException ex) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking SettingsEntityChangeDTO for key %s", message.getId()), ex);
    }
    return settingsEntityChangeDTO;
  }
}
