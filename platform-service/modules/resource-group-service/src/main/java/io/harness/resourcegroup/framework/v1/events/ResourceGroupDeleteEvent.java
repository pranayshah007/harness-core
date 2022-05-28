/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v1.events;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.RESOURCE_GROUP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
public class ResourceGroupDeleteEvent implements Event {
  String accountIdentifier;
  ResourceGroupDTO resourceGroup;
  io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO resourceGroupV2;

  public ResourceGroupDeleteEvent(String accountIdentifier, ResourceGroupDTO resourceGroup,
      io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO resourceGroupV2) {
    this.accountIdentifier = accountIdentifier;
    this.resourceGroup = resourceGroup;
    this.resourceGroupV2 = resourceGroupV2;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    if (isEmpty(resourceGroupV2.getOrgIdentifier())) {
      return new AccountScope(accountIdentifier);
    } else if (isEmpty(resourceGroupV2.getProjectIdentifier())) {
      return new OrgScope(accountIdentifier, resourceGroupV2.getOrgIdentifier());
    }
    return new ProjectScope(
        accountIdentifier, resourceGroupV2.getOrgIdentifier(), resourceGroupV2.getProjectIdentifier());
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, resourceGroupV2.getName());
    return Resource.builder().identifier(resourceGroupV2.getIdentifier()).type(RESOURCE_GROUP).labels(labels).build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return "ResourceGroupDeleted";
  }
}
