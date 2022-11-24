/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.events;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.CONNECTOR;
import static io.harness.connector.ConnectorEvent.CONNECTOR_FORCE_DELETED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
public class ConnectorForceDeleteEvent implements Event {
  private String accountIdentifier;
  private ConnectorInfoDTO connectorDTO;

  public ConnectorForceDeleteEvent(String accountIdentifier, ConnectorInfoDTO connectorDTO) {
    this.accountIdentifier = accountIdentifier;
    this.connectorDTO = connectorDTO;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    if (isNotEmpty(connectorDTO.getOrgIdentifier())) {
      if (isEmpty(connectorDTO.getProjectIdentifier())) {
        return new OrgScope(accountIdentifier, connectorDTO.getOrgIdentifier());
      } else {
        return new ProjectScope(
            accountIdentifier, connectorDTO.getOrgIdentifier(), connectorDTO.getProjectIdentifier());
      }
    }
    return new AccountScope(accountIdentifier);
  }
  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, connectorDTO.getName());
    return Resource.builder().identifier(connectorDTO.getIdentifier()).type(CONNECTOR).labels(labels).build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return CONNECTOR_FORCE_DELETED;
  }
}
