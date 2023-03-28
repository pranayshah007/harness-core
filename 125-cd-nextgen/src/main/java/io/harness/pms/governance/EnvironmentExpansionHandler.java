/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.common.NGExpressionUtils;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class EnvironmentExpansionHandler implements JsonExpansionHandler {
  @Inject private EnvironmentService envService;
  @Inject private InfrastructureEntityService infraService;
  @Inject private EnvironmentExpansionUtils utils;
  private final ObjectMapper objectMapper = new ObjectMapper();
  @Override
  public ExpansionResponse expand(JsonNode envJsonNode, ExpansionRequestMetadata metadata, String fqn) {
    final String accountId = metadata.getAccountId();
    final String orgId = metadata.getOrgId();
    final String projectId = metadata.getProjectId();

    final Optional<String> envRefOpt = EnvironmentExpansionUtils.getEnvRefFromEnvYamlV2Node(envJsonNode);
    if (envRefOpt.isEmpty()) {
      return ExpansionResponse.builder().success(false).errorMessage("environmentRef not found in environment").build();
    }

    if (NGExpressionUtils.matchesGenericExpressionPattern(envRefOpt.get())) {
      return ExpansionResponse.builder().success(false).errorMessage("environmentRef is an expression").build();
    }

    final Optional<Environment> environmentOpt = envService.get(accountId, orgId, projectId, envRefOpt.get(), false);
    if (environmentOpt.isEmpty()) {
      return ExpansionResponse.builder()
          .success(false)
          .errorMessage(String.format("Environment %s does not exist", envRefOpt.get()))
          .build();
    }

    final Environment environment = environmentOpt.get();

    if (envJsonNode.isObject()) {
      final Optional<String> infraIdentifierOpt = EnvironmentExpansionUtils.getInfraId(envJsonNode);

      if (infraIdentifierOpt.isEmpty() || EmptyPredicate.isEmpty(infraIdentifierOpt.get())) {
        return ExpansionResponse.builder()
            .success(false)
            .errorMessage("Infrastructure identifier not found in environment")
            .build();
      }

      if (NGExpressionUtils.matchesGenericExpressionPattern(infraIdentifierOpt.get())) {
        return ExpansionResponse.builder()
            .success(false)
            .errorMessage("Infrastructure identifier is an expression")
            .build();
      }

      final Optional<InfrastructureEntity> infraEntity =
          getInfrastructureEntity(accountId, orgId, projectId, envRefOpt.get(), infraIdentifierOpt.get());

      if (infraEntity.isEmpty()) {
        return ExpansionResponse.builder()
            .success(false)
            .errorMessage(String.format("Infrastructure Definition %s does not exist in environment %s",
                infraIdentifierOpt.get(), envRefOpt.get()))
            .build();
      }

      final InfrastructureConfig infrastructureConfig =
          EnvironmentExpansionUtils.getMergedInfrastructure(objectMapper, envJsonNode, infraEntity.get());

      final Optional<ConnectorResponseDTO> connectorDTO = utils.fetchConnector(metadata, infrastructureConfig);

      final ExpandedValue value = EnvironmentExpansionUtils.buildInfraExpandedValue(
          objectMapper, environment, infrastructureConfig, connectorDTO.orElse(null));

      return ExpansionResponse.builder()
          .success(true)
          .key(value.getKey())
          .value(value)
          .placement(ExpansionPlacementStrategy.PARALLEL)
          .build();
    }

    return ExpansionResponse.builder()
        .success(false)
        .errorMessage("Environment cannot be processed for expansion")
        .build();
  }

  private Optional<InfrastructureEntity> getInfrastructureEntity(
      String accountId, String orgId, String projectId, String envId, String infraId) {
    return infraService.get(accountId, orgId, projectId, envId, infraId);
  }
}
