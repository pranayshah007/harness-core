/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.users.events;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.accesscontrol.commons.events.EventHandler;
import io.harness.accesscontrol.principals.users.HarnessUserService;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams.HarnessScopeParamsBuilder;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.usermembership.UserMembershipDTO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
@Singleton
public class UserMembershipEventHandler implements EventHandler {
  private final HarnessUserService harnessUserService;

  @Inject
  public UserMembershipEventHandler(HarnessUserService harnessUserService) {
    this.harnessUserService = harnessUserService;
  }

  @Override
  public boolean handle(Message message) {
    UserMembershipDTO userMembershipDTO = null;
    try {
      userMembershipDTO = UserMembershipDTO.parseFrom(message.getMessage().getData());
      log.info("Handling message {} parsed userMembershipDTO  {} in UserMembershipEventHandler", message.getId(),
          userMembershipDTO);
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking EntityChangeDTO for user group event with key {}", message.getId(), e);
    }
    if (Objects.isNull(userMembershipDTO)) {
      return true;
    }
    try {
      String userId = stripToNull(userMembershipDTO.getUserId());
      io.harness.eventsframework.schemas.usermembership.Scope eventsScope = userMembershipDTO.getScope();

      HarnessScopeParamsBuilder builder =
          HarnessScopeParams.builder().accountIdentifier(stripToNull(eventsScope.getAccountIdentifier()));
      builder.orgIdentifier(stripToNull(eventsScope.getOrgIdentifier()));
      builder.projectIdentifier(stripToNull(eventsScope.getProjectIdentifier()));

      Scope scope = ScopeMapper.fromParams(builder.build());
      if (isNotEmpty(userId)) {
        harnessUserService.sync(userId, scope);
      }
    } catch (Exception e) {
      log.error("Could not process the user membership delete event {} due to error", userMembershipDTO, e);
      return false;
    }
    return true;
  }
}
