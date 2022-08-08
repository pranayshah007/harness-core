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
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
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
public class EnvironmentRefExpansionHandler implements JsonExpansionHandler {
  @Inject EnvironmentService environmentService;

  @Override
  public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata, String fqn) {
    String accountId = metadata.getAccountId();
    String orgId = metadata.getOrgId();
    String projectId = metadata.getProjectId();
    String scopedEnvId = fieldValue.textValue();
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(scopedEnvId, accountId, orgId, projectId);
    Scope scope = identifierRef.getScope();

    Optional<Environment> optEnvironment;
    switch (scope) {
      case ACCOUNT:
        optEnvironment = getEnvironment(accountId, null, null, identifierRef.getIdentifier());
        break;
      case ORG:
        optEnvironment = getEnvironment(accountId, orgId, null, identifierRef.getIdentifier());
        break;
      case PROJECT:
        optEnvironment = getEnvironment(accountId, orgId, projectId, identifierRef.getIdentifier());
        break;
      default:
        return sendErrorResponseForNotFoundEnv(scopedEnvId);
    }
    if (!optEnvironment.isPresent()) {
      return sendErrorResponseForNotFoundEnv(scopedEnvId);
    }
    Environment environment = optEnvironment.get();
    ExpandedValue value = EnvironmentRefExpandedValue.builder().environment(environment).build();
    return ExpansionResponse.builder()
        .success(true)
        .key(value.getKey())
        .value(value)
        .placement(ExpansionPlacementStrategy.REPLACE)
        .build();
  }

  Optional<Environment> getEnvironment(String accountId, String orgId, String projectId, String envId) {
    return environmentService.get(accountId, orgId, projectId, envId, false);
  }

  ExpansionResponse sendErrorResponseForNotFoundEnv(String env) {
    return ExpansionResponse.builder().success(false).errorMessage("Could not find environment: " + env).build();
  }
}
