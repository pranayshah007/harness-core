/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.events;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.SERVICE_ACCOUNT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.event.Event;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.mapper.ResourceScopeMapper;
import io.harness.serviceaccount.ServiceAccountDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
public class ServiceAccountUpdateEvent implements Event {
  public static final String SERVICE_ACCOUNT_UPDATED = "ServiceAccountUpdated";
  private ServiceAccountDTO oldServiceAccount;
  private ServiceAccountDTO newServiceAccount;

  public ServiceAccountUpdateEvent(ServiceAccountDTO oldServiceAccount, ServiceAccountDTO newServiceAccount) {
    this.oldServiceAccount = oldServiceAccount;
    this.newServiceAccount = newServiceAccount;
  }

  @Override
  @JsonIgnore
  public ResourceScope getResourceScope() {
    return ResourceScopeMapper.getResourceScope(Scope.of(newServiceAccount.getAccountIdentifier(),
        newServiceAccount.getOrgIdentifier(), newServiceAccount.getProjectIdentifier()));
  }

  @Override
  @JsonIgnore
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, getNewServiceAccount().getName());
    return Resource.builder()
        .identifier(oldServiceAccount.getIdentifier())
        .type(SERVICE_ACCOUNT)
        .labels(labels)
        .uniqueId(oldServiceAccount.getUniqueId())
        .build();
  }

  @Override
  @JsonIgnore
  public String getEventType() {
    return SERVICE_ACCOUNT_UPDATED;
  }
}
