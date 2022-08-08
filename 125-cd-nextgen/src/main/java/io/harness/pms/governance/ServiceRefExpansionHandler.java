/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.governance;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.encryption.Scope;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

@OwnedBy(CDC)
@Singleton
public class ServiceRefExpansionHandler implements JsonExpansionHandler {
  @Inject ServiceEntityService serviceEntityService;

  @Override
  public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata, String fqn) {
    String accountId = metadata.getAccountId();
    String orgId = metadata.getOrgId();
    String projectId = metadata.getProjectId();
    String scopedServiceId = fieldValue.textValue();
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(scopedServiceId, accountId, orgId, projectId);
    Scope scope = identifierRef.getScope();
    Optional<ServiceEntity> optService;
    switch (scope) {
      case ACCOUNT:
        optService = getService(accountId, null, null, identifierRef.getIdentifier());
        break;
      case ORG:
        optService = getService(accountId, orgId, null, identifierRef.getIdentifier());
        break;
      case PROJECT:
        optService = getService(accountId, orgId, projectId, identifierRef.getIdentifier());
        break;
      default:
        return sendErrorResponseForNotFoundService(scopedServiceId);
    }

    if (!optService.isPresent()) {
      return sendErrorResponseForNotFoundService(scopedServiceId);
    }
    ServiceEntity service = optService.get();
    ExpandedValue value = ServiceRefExpandedValue.builder().serviceEntity(service).build();
    return ExpansionResponse.builder()
        .success(true)
        .key(value.getKey())
        .value(value)
        .placement(ExpansionPlacementStrategy.REPLACE)
        .build();
  }

  Optional<ServiceEntity> getService(String accountId, String orgId, String projectId, String serviceId) {
    return serviceEntityService.get(accountId, orgId, projectId, serviceId, false);
  }

  ExpansionResponse sendErrorResponseForNotFoundService(String service) {
    return ExpansionResponse.builder().success(false).errorMessage("Could not find service: " + service).build();
  }
}
