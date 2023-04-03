/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.merger.helpers.MergeHelper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;

@Singleton
public class EnvironmentExpansionUtils {
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private KryoSerializer kryoSerializer;

  static InfrastructureExpandedValue buildInfraExpandedValue(ObjectMapper objectMapper, Environment environment,
      InfrastructureConfig infrastructureConfig, ConnectorResponseDTO connectorDTO) {
    return InfrastructureExpandedValue.builder()
        .infrastructureDefinition(InfrastructureValue.builder()
                                      .type(infrastructureConfig.getInfrastructureDefinitionConfig().getType())
                                      .spec(infrastructureConfig.getInfrastructureDefinitionConfig().getSpec())
                                      .build())
        .environment(EnvironmentMapper.toBasicInfo(environment))
        .infrastructureConnectorNode(
            connectorDTO != null ? objectMapper.convertValue(connectorDTO.getConnector(), ObjectNode.class) : null)
        .build();
  }

  @Data
  @AllArgsConstructor
  static class InfrastructureDataBag {
    String identifier;
    JsonNode infrastructureInputs;
  }

  static Optional<String> getEnvRefFromEnvYamlV2Node(JsonNode fieldValue) {
    JsonNode jsonNode = fieldValue.get(YamlTypes.ENVIRONMENT_REF);
    if (jsonNode != null && jsonNode.isTextual()) {
      return Optional.ofNullable(jsonNode.asText());
    }
    return Optional.empty();
  }

  static Optional<String> getInfraId(JsonNode fieldValue) {
    // handle infra definitions
    final JsonNode infraDefinitionsNode = fieldValue.get(YamlTypes.INFRASTRUCTURE_DEFS);
    if (infraDefinitionsNode != null && infraDefinitionsNode.isArray()) {
      JsonNode infraNode = infraDefinitionsNode.get(0);
      if (infraNode != null && infraNode.isObject()
          && (infraNode.get(YamlTypes.IDENTIFIER) != null && infraNode.get(YamlTypes.IDENTIFIER).isTextual())) {
        return Optional.ofNullable(infraNode.get(YamlTypes.IDENTIFIER).asText());
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

  static List<InfrastructureDataBag> getInfraRefAndInputs(JsonNode fieldValue) {
    final List<InfrastructureDataBag> dataBag = new ArrayList<>();

    // handle infra definitions
    final JsonNode infraDefinitionsNode = fieldValue.get(YamlTypes.INFRASTRUCTURE_DEFS);
    if (infraDefinitionsNode != null && infraDefinitionsNode.isArray()) {
      for (JsonNode jsonNode : infraDefinitionsNode) {
        getInfraDataBag(jsonNode).ifPresent(dataBag::add);
      }
    }

    final JsonNode infraDefinitionNode = fieldValue.get(YamlTypes.INFRASTRUCTURE_DEF);
    getInfraDataBag(infraDefinitionNode).ifPresent(dataBag::add);

    return dataBag;
  }

  private static Optional<InfrastructureDataBag> getInfraDataBag(JsonNode infraNode) {
    if (infraNode != null && infraNode.isObject()
        && (infraNode.get(YamlTypes.IDENTIFIER) != null && infraNode.get(YamlTypes.IDENTIFIER).isTextual())) {
      String identifier = infraNode.get(YamlTypes.IDENTIFIER).asText();
      if (infraNode.get(YamlTypes.INPUTS) != null) {
        return Optional.of(new InfrastructureDataBag(identifier, infraNode.get(YamlTypes.INPUTS)));
      } else {
        return Optional.of(new InfrastructureDataBag(identifier, null));
      }
    }
    return Optional.empty();
  }

  static Optional<JsonNode> getInfraInputs(JsonNode fieldValue) {
    // handle infra definitions
    final JsonNode infraDefinitionsNode = fieldValue.get(YamlTypes.INFRASTRUCTURE_DEFS);
    if (infraDefinitionsNode != null && infraDefinitionsNode.isArray()) {
      JsonNode infraNode = infraDefinitionsNode.get(0);
      if (infraNode != null && infraNode.isObject() && (infraNode.get(YamlTypes.INPUTS) != null)) {
        return Optional.ofNullable(infraNode.get(YamlTypes.INPUTS));
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

  static InfrastructureConfig getMergedInfrastructure(
      ObjectMapper objectMapper, JsonNode fieldValue, InfrastructureEntity infraEntity) {
    final Optional<JsonNode> infraInputs = getInfraInputs(fieldValue);
    if (infraInputs.isPresent()) {
      return mergeInfraInputs(objectMapper, infraEntity, infraInputs.get());
    }

    return InfrastructureEntityConfigMapper.toInfrastructureConfig(infraEntity);
  }

  @SneakyThrows
  @NotNull
  static InfrastructureConfig mergeInfraInputs(
      ObjectMapper objectMapper, InfrastructureEntity infrastructure, JsonNode inputsNode) {
    if (inputsNode == null || inputsNode.isNull()) {
      return InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructure);
    }
    Map<String, Object> inputMap = new HashMap<>();
    inputMap.put(YamlTypes.INFRASTRUCTURE_DEF, objectMapper.treeToValue(inputsNode, Map.class));
    String mergedYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        infrastructure.getYaml(), YamlPipelineUtils.writeYamlString(inputMap), false, true);
    infrastructure.setYaml(mergedYaml);
    return InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructure);
  }

  Optional<ConnectorResponseDTO> fetchConnector(
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

  private GitSyncBranchContext deserializeGitSyncBranchContext(ByteString byteString) {
    if (isEmpty(byteString)) {
      return null;
    }
    byte[] bytes = byteString.toByteArray();
    return isEmpty(bytes) ? null : (GitSyncBranchContext) kryoSerializer.asInflatedObject(bytes);
  }
}
