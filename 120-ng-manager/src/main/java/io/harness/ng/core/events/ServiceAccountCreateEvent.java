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
public class ServiceAccountCreateEvent implements Event {
  public static final String SERVICE_ACCOUNT_CREATED = "ServiceAccountCreated";
  private ServiceAccountDTO serviceAccount;

  public ServiceAccountCreateEvent(ServiceAccountDTO serviceAccount) {
    this.serviceAccount = serviceAccount;
  }

  @Override
  @JsonIgnore
  public ResourceScope getResourceScope() {
    return ResourceScopeMapper.getResourceScope(Scope.of(serviceAccount.getAccountIdentifier(),
        serviceAccount.getOrgIdentifier(), serviceAccount.getProjectIdentifier()));
  }

  @Override
  @JsonIgnore
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, serviceAccount.getName());
    return Resource.builder()
        .identifier(serviceAccount.getIdentifier())
        .type(SERVICE_ACCOUNT)
        .labels(labels)
        .uniqueId(serviceAccount.getUniqueId())
        .build();
  }

  @Override
  @JsonIgnore
  public String getEventType() {
    return SERVICE_ACCOUNT_CREATED;
  }
}
