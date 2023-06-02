/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.service.impl;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_ENTITY;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.user.UserDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
@Singleton
public class UserEntityCrudStreamListener implements MessageListener {
  private final NgUserService ngUserService;

  @Inject
  public UserEntityCrudStreamListener(NgUserService ngUserService) {
    this.ngUserService = ngUserService;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null && metadataMap.get(ACTION) != null) {
        String entityType = metadataMap.get(ENTITY_TYPE);
        if (USER_ENTITY.equals(entityType)) {
          UserDTO userDTO;
          try {
            userDTO = UserDTO.parseFrom(message.getMessage().getData());
          } catch (InvalidProtocolBufferException e) {
            throw new InvalidRequestException(
                String.format("Exception in unpacking UserDTO for key %s", message.getId()), e);
          }
          return handleMessage(userDTO, metadataMap.get(ACTION));
        }
      }
    }
    return true;
  }

  private boolean handleMessage(UserDTO userDTO, String action) {
    String userId = stripToNull(userDTO.getUserId());
    if (Objects.isNull(userId)) {
      log.error("UserId can't be null in the event consumed from entity crud stream");
      return true;
    }
    if (action.equals(UPDATE_ACTION)) {
      UserMetadataDTO user = UserMetadataDTO.builder()
                                 .uuid(stripToNull(userDTO.getUserId()))
                                 .name(stripToNull(userDTO.getName()))
                                 .email(stripToNull(userDTO.getEmail()))
                                 .locked(userDTO.getLocked())
                                 .twoFactorAuthenticationEnabled(userDTO.getIsTwoFactorAuthenticationEnabled())
                                 .build();
      return ngUserService.updateUserMetadata(user);
    }
    return true;
  }
}
