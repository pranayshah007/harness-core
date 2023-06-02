/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.events;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.audit.ResourceTypeConstants.SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.service.entity.ServiceEntity;

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@OwnedBy(CDC)
@Getter
@Builder
@AllArgsConstructor

public class ServiceForceDeleteEvent implements Event {
  private String accountIdentifier;

  private String orgIdentifier;

  private String projectIdentifier;

  private ServiceEntity service;

  @Override
  public ResourceScope getResourceScope() {
    if (isEmpty(service.getOrgIdentifier())) {
      return new AccountScope(accountIdentifier);
    } else if (isEmpty(service.getProjectIdentifier())) {
      return new OrgScope(accountIdentifier, service.getOrgIdentifier());
    }
    return new ProjectScope(accountIdentifier, service.getOrgIdentifier(), service.getProjectIdentifier());
  }

  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, service.getName());
    return Resource.builder().identifier(service.getIdentifier()).type(SERVICE).labels(labels).build();
  }

  @Override
  public String getEventType() {
    return OutboxEventConstants.SERVICE_FORCE_DELETED;
  }
}
