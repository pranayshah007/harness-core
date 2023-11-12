/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.serviceaccounts.events;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.accesscontrol.commons.events.EventHandler;
import io.harness.accesscontrol.principals.serviceaccounts.HarnessServiceAccountService;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.entity_crud.serviceaccount.ServiceAccountEntityChangeDTO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
@Singleton
public class ServiceAccountEventHandler implements EventHandler {
  private final HarnessServiceAccountService harnessServiceAccountService;

  @Inject
  public ServiceAccountEventHandler(HarnessServiceAccountService harnessServiceAccountService) {
    this.harnessServiceAccountService = harnessServiceAccountService;
  }

  @Override
  public boolean handle(Message message) {
    ServiceAccountEntityChangeDTO entityChangeDTO = null;
    try {
      entityChangeDTO = ServiceAccountEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking EntityChangeDTO for user group event with key {}", message.getId(), e);
    }
    if (Objects.isNull(entityChangeDTO)) {
      return true;
    }
    try {
      HarnessScopeParams params = HarnessScopeParams.builder()
                                      .accountIdentifier(stripToNull(entityChangeDTO.getAccountIdentifier()))
                                      .orgIdentifier(stripToNull(entityChangeDTO.getOrgIdentifier()))
                                      .projectIdentifier(stripToNull(entityChangeDTO.getProjectIdentifier()))
                                      .build();
      Scope scope = ScopeMapper.fromParams(params);
      harnessServiceAccountService.sync(
          stripToNull(entityChangeDTO.getIdentifier()), scope, entityChangeDTO.getUniqueId());
    } catch (Exception e) {
      log.error("Could not process the resource group change event {} due to error", entityChangeDTO, e);
      return false;
    }
    return true;
  }
}
