/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.governance.DefaultConnectorRefExpansionHandler;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class EnvironmentExpansionHandler implements JsonExpansionHandler {
  @Inject private EnvironmentService envService;
  @Inject private InfrastructureEntityService infraService;
  @Inject private KryoSerializer kryoSerializer;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  @Inject private DefaultConnectorRefExpansionHandler connectorRefExpansionHandler;
  private final ObjectMapper objectMapper = new ObjectMapper();
  @Override
  public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata, String fqn) {
    final String accountId = metadata.getAccountId();
    final String orgId = metadata.getOrgId();
    final String projectId = metadata.getProjectId();

    final Optional<String> envIdOpt = getEnvId(fieldValue);
    if (envIdOpt.isEmpty()) {
      return ExpansionResponse.builder().success(false).errorMessage("environmentRef not found in environment").build();
    }

    if (NGExpressionUtils.matchesGenericExpressionPattern(envIdOpt.get())) {
      return ExpansionResponse.builder().success(false).errorMessage("environmentRef is an expression").build();
    }

    final Optional<Environment> environmentOpt = envService.get(accountId, orgId, projectId, envIdOpt.get(), false);
    if (environmentOpt.isEmpty()) {
      return ExpansionResponse.builder()
          .success(false)
          .errorMessage(String.format("Environment %s does not exist", envIdOpt.get()))
          .build();
    }

    final Environment environment = environmentOpt.get();

    if (fieldValue.isObject()) {
      final Optional<String> infraIdentifierOpt = getInfraId(fieldValue);

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
          getInfrastructureEntity(accountId, orgId, projectId, envIdOpt.get(), infraIdentifierOpt.get());

      if (infraEntity.isEmpty()) {
        return ExpansionResponse.builder()
            .success(false)
            .errorMessage(String.format("Infrastructure Definition %s does not exist in environment %s",
                infraIdentifierOpt.get(), envIdOpt.get()))
            .build();
      }

      final InfrastructureConfig infrastructureConfig = getMergedInfrastructure(fieldValue, infraEntity.get());

      final Optional<ConnectorResponseDTO> connectorDTO = fetchConnector(metadata, infrastructureConfig);

      final ExpandedValue value =
          InfrastructureExpandedValue.builder()
              .infrastructureDefinition(InfrastructureExpandedValue.InfrastructureValue.builder()
                                            .type(infrastructureConfig.getInfrastructureDefinitionConfig().getType())
                                            .spec(infrastructureConfig.getInfrastructureDefinitionConfig().getSpec())
                                            .build())
              .environment(EnvironmentMapper.toBasicInfo(environment))
              .infrastructureConnectorNode(
                  connectorDTO
                      .<JsonNode>map(connectorResponseDTO
                          -> objectMapper.convertValue(connectorResponseDTO.getConnector(), ObjectNode.class))
                      .orElse(null))
              .build();
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

  private Optional<ConnectorResponseDTO> fetchConnector(
      ExpansionRequestMetadata metadata, InfrastructureConfig infrastructureConfig) {
    ParameterField<String> connectorRef =
        infrastructureConfig.getInfrastructureDefinitionConfig().getSpec().getConnectorReference();
    if (ParameterField.isNotNull(connectorRef) && !connectorRef.isExpression()) {
      String accountId = metadata.getAccountId();
      String orgId = metadata.getOrgId();
      String projectId = metadata.getProjectId();
      ByteString gitSyncBranchContext = metadata.getGitSyncBranchContext();
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(connectorRef.getValue(), accountId, orgId, projectId);
      Scope scope = identifierRef.getScope();
      Optional<ConnectorResponseDTO> optConnector;
      switch (scope) {
        case ACCOUNT:
          optConnector = getConnectorDTO(accountId, null, null, identifierRef.getIdentifier());
          break;
        case ORG:
          optConnector = getConnectorDTO(accountId, orgId, null, identifierRef.getIdentifier());
          break;
        case PROJECT:
          optConnector =
              getConnectorDTO(identifierRef.getIdentifier(), accountId, orgId, projectId, gitSyncBranchContext);
          break;
        default:
          throw new InvalidRequestException("No connector found with reference " + identifierRef);
      }
      return optConnector;
    }
    return Optional.empty();
  }

  Optional<ConnectorResponseDTO> getConnectorDTO(String accountId, String orgId, String projectId, String connectorId) {
    return connectorService.get(accountId, orgId, projectId, connectorId);
  }

  Optional<ConnectorResponseDTO> getConnectorDTO(
      String connectorId, String accountId, String orgId, String projectId, ByteString gitSyncBranchContext) {
    try (PmsGitSyncBranchContextGuard ignore =
             new PmsGitSyncBranchContextGuard(deserializeGitSyncBranchContext(gitSyncBranchContext), true)) {
      return getConnectorDTO(accountId, orgId, projectId, connectorId);
    }
  }

  private InfrastructureConfig getMergedInfrastructure(JsonNode fieldValue, InfrastructureEntity infraEntity) {
    final Optional<JsonNode> infraInputs = getInfraInputs(fieldValue);
    if (infraInputs.isPresent()) {
      String mergedYaml = mergeInfraInputs(infraEntity.getYaml(), infraInputs.get());
      infraEntity.setYaml(mergedYaml);
    }

    return InfrastructureEntityConfigMapper.toInfrastructureConfig(infraEntity);
  }

  @SneakyThrows
  private String mergeInfraInputs(String originalYaml, JsonNode inputsNode) {
    if (inputsNode == null || inputsNode.isNull()) {
      return originalYaml;
    }
    Map<String, Object> inputMap = new HashMap<>();
    inputMap.put(YamlTypes.INFRASTRUCTURE_DEF, objectMapper.treeToValue(inputsNode, Map.class));
    return MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        originalYaml, YamlPipelineUtils.writeYamlString(inputMap), false, true);
  }

  private Optional<String> getEnvId(JsonNode fieldValue) {
    JsonNode jsonNode = fieldValue.get(YamlTypes.ENVIRONMENT_REF);
    if (jsonNode != null && jsonNode.isTextual()) {
      return Optional.ofNullable(jsonNode.asText());
    }
    return Optional.empty();
  }

  private Optional<String> getInfraId(JsonNode fieldValue) {
    // handle infra definitions
    final JsonNode infraDefinitionsNode = fieldValue.get(YamlTypes.INFRASTRUCTURE_DEFS);
    if (infraDefinitionsNode != null && infraDefinitionsNode.isArray()) {
      JsonNode infraNode = infraDefinitionsNode.get(0);
      if (infraNode != null && infraNode.isObject()) {
        if (infraNode.get(YamlTypes.IDENTIFIER) != null && infraNode.get(YamlTypes.IDENTIFIER).isTextual()) {
          return Optional.ofNullable(infraNode.get(YamlTypes.IDENTIFIER).asText());
        }
      }
    }

    // handle infradefinition
    final JsonNode infraDefinitionNode = fieldValue.get(YamlTypes.INFRASTRUCTURE_DEF);
    if (infraDefinitionNode != null && infraDefinitionNode.isObject()) {
      JsonNode jsonNode = infraDefinitionNode.get(YamlTypes.IDENTIFIER);
      if (jsonNode.isTextual()) {
        return Optional.ofNullable(jsonNode.asText());
      }
    }

    return Optional.empty();
  }

  private Optional<JsonNode> getInfraInputs(JsonNode fieldValue) {
    // handle infra definitions
    final JsonNode infraDefinitionsNode = fieldValue.get(YamlTypes.INFRASTRUCTURE_DEFS);
    if (infraDefinitionsNode != null && infraDefinitionsNode.isArray()) {
      JsonNode infraNode = infraDefinitionsNode.get(0);
      if (infraNode != null && infraNode.isObject()) {
        if (infraNode.get(YamlTypes.INPUTS) != null) {
          return Optional.ofNullable(infraNode.get(YamlTypes.INPUTS));
        }
      }
    }

    // handle infradefinition
    final JsonNode infraDefinitionNode = fieldValue.get(YamlTypes.INFRASTRUCTURE_DEF);
    if (infraDefinitionNode != null && infraDefinitionNode.isObject()) {
      JsonNode jsonNode = infraDefinitionNode.get(YamlTypes.INPUTS);
      if (jsonNode != null && !jsonNode.isNull() && jsonNode.isObject()) {
        return Optional.of(jsonNode);
      }
    }

    return Optional.empty();
  }

  private Optional<InfrastructureEntity> getInfrastructureEntity(
      String accountId, String orgId, String projectId, String envId, String infraId) {
    return infraService.get(accountId, orgId, projectId, envId, infraId);
  }

  public GitSyncBranchContext deserializeGitSyncBranchContext(ByteString byteString) {
    if (isEmpty(byteString)) {
      return null;
    }
    byte[] bytes = byteString.toByteArray();
    return isEmpty(bytes) ? null : (GitSyncBranchContext) kryoSerializer.asInflatedObject(bytes);
  }
}
