/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class EnvironmentExpansionUtils {
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureEntityService infrastructureService;
  private final ObjectMapper objectMapper = new ObjectMapper();

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

  static void processSingleEnvNode(JsonNode value) {
    if (value.isObject() && value.get(SingleEnvironmentExpandedValue.keys.infrastructures) != null) {
      JsonNode infrastructuresNode = value.get(SingleEnvironmentExpandedValue.keys.infrastructures);
      if (infrastructuresNode.isArray() && infrastructuresNode.size() > 0) {
        final List<JsonNode> nodes = new ArrayList<>();
        for (JsonNode jsonNode : infrastructuresNode) {
          nodes.add(processInfrastructureNode(jsonNode));
        }
        ArrayNode finalNode = new ArrayNode(JsonNodeFactory.instance, nodes);
        ((ObjectNode) value).remove(SingleEnvironmentExpandedValue.keys.infrastructures);
        ((ObjectNode) value).set(SingleEnvironmentExpandedValue.keys.infrastructures, finalNode);
      }
    }
  }

  // replace connectorRef by connector spec and also move out infrastructure type and spec to upper level to keep the
  // paths less verbose
  static JsonNode processInfrastructureNode(JsonNode node) {
    if (!node.isObject()) {
      return NullNode.instance;
    }
    ObjectNode infraNode = (ObjectNode) node.get(InfrastructureExpandedValue.keys.infrastructureDefinition);
    ObjectNode connectorNode = (ObjectNode) node.get(InfrastructureExpandedValue.keys.infrastructureConnectorNode);
    ObjectNode spec = (ObjectNode) infraNode.get(YAMLFieldNameConstants.SPEC);
    if (spec.get(YamlTypes.CONNECTOR_REF) != null && connectorNode != null) {
      spec.set(ExpansionConstants.CONNECTOR_PROP_NAME, connectorNode);
      spec.remove(YamlTypes.CONNECTOR_REF);
    }
    ObjectNode finalNode = new ObjectNode(JsonNodeFactory.instance);
    finalNode.set(YAMLFieldNameConstants.TYPE, infraNode.get(YAMLFieldNameConstants.TYPE));
    finalNode.set(YAMLFieldNameConstants.SPEC, infraNode.get(YAMLFieldNameConstants.SPEC));

    return finalNode;
  }

  SingleEnvironmentExpandedValue toSingleEnvironmentExpandedValue(
      ExpansionRequestMetadata metadata, JsonNode envYamlV2Node) {
    final String accountId = metadata.getAccountId();
    final String orgId = metadata.getOrgId();
    final String projectId = metadata.getProjectId();

    final Optional<String> envRefOpt = getEnvRefFromEnvYamlV2Node(envYamlV2Node);
    if (envRefOpt.isEmpty()) {
      return SingleEnvironmentExpandedValue.builder().build();
    }

    if (NGExpressionUtils.matchesGenericExpressionPattern(envRefOpt.get())) {
      log.warn(String.format("Environment Ref %s is an expression. Skipping policy expansion for it", envRefOpt.get()));
      return SingleEnvironmentExpandedValue.builder().environmentRef(envRefOpt.get()).build();
    }

    final Optional<Environment> environmentOpt =
        environmentService.get(accountId, orgId, projectId, envRefOpt.get(), false);
    if (environmentOpt.isEmpty()) {
      log.warn(String.format("Environment %s does not exist", envRefOpt.get()));
      return SingleEnvironmentExpandedValue.builder().build();
    }
    final Environment environment = environmentOpt.get();
    final SingleEnvironmentExpandedValue envExpandedValue =
        buildSingleEnvironmentExpandedValue(envRefOpt.get(), environment);

    envExpandedValue.setInfrastructures(buildInfrastructureList(metadata, environment, envRefOpt.get(), envYamlV2Node));

    return envExpandedValue;
  }

  private static SingleEnvironmentExpandedValue buildSingleEnvironmentExpandedValue(
      String envRef, Environment environment) {
    return SingleEnvironmentExpandedValue.builder()
        .type(environment.getType())
        .identifier(environment.getIdentifier())
        .environmentRef(envRef)
        .accountIdentifier(environment.getAccountId())
        .orgIdentifier(environment.getOrgIdentifier())
        .projectIdentifier(environment.getProjectIdentifier())
        .description(environment.getDescription())
        .tags(convertToMap(environment.getTags()))
        .color(environment.getColor())
        .name(environment.getName())
        .build();
  }

  List<InfrastructureExpandedValue> buildInfrastructureList(
      ExpansionRequestMetadata metadata, Environment environment, String environmentRef, JsonNode envNode) {
    final String accountId = metadata.getAccountId();
    final String orgId = metadata.getOrgId();
    final String projectId = metadata.getProjectId();

    final List<InfrastructureDataBag> infrastructuresData = getInfraRefAndInputs(envNode);
    if (isEmpty(infrastructuresData)) {
      return List.of();
    }

    Map<String, InfrastructureDataBag> infraWithInputs =
        infrastructuresData.stream()
            .filter(i -> i.getInfrastructureInputs() != null && i.getInfrastructureInputs().isObject())
            .collect(Collectors.toMap(InfrastructureDataBag::getIdentifier, Function.identity()));

    final List<String> identifiers = infrastructuresData.stream()
                                         .map(InfrastructureDataBag::getIdentifier)
                                         .filter(Predicate.not(NGExpressionUtils::matchesGenericExpressionPattern))
                                         .collect(Collectors.toList());

    final List<InfrastructureEntity> infrastructures = infrastructureService.getAllInfrastructureFromIdentifierList(
        accountId, orgId, projectId, environmentRef, identifiers);

    if (isEmpty(infrastructures)) {
      return List.of();
    }

    final Map<String, InfrastructureEntity> infraIdToEntityMap =
        infrastructures.stream().collect(Collectors.toMap(InfrastructureEntity::getIdentifier, Function.identity()));

    final List<InfrastructureConfig> validInfrastructures = new ArrayList<>();
    identifiers.forEach(i -> {
      if (infraIdToEntityMap.containsKey(i)) {
        final InfrastructureConfig infrastructureConfig;
        if (infraWithInputs.containsKey(i)) {
          infrastructureConfig = mergeInfraInputs(
              objectMapper, infraIdToEntityMap.get(i), infraWithInputs.get(i).getInfrastructureInputs());
        } else {
          infrastructureConfig = InfrastructureEntityConfigMapper.toInfrastructureConfig(infraIdToEntityMap.get(i));
        }
        validInfrastructures.add(infrastructureConfig);
      }
    });

    final Map<String, ConnectorResponseDTO> connectorRefToDTOMap =
        getConnectorRefToDTOMap(accountId, orgId, projectId, validInfrastructures);

    return validInfrastructures.stream()
        .map(infra -> mapInfrastructureConfigToExpandedValue(infra, environment, connectorRefToDTOMap))
        .collect(Collectors.toList());
  }

  InfrastructureExpandedValue mapInfrastructureConfigToExpandedValue(InfrastructureConfig infraConfig,
      Environment environment, Map<String, ConnectorResponseDTO> connectorRefToDTOMap) {
    final ParameterField<String> connectorReference =
        infraConfig.getInfrastructureDefinitionConfig().getSpec().getConnectorReference();
    final Optional<String> connectorRefOpt =
        ParameterField.isNotNull(connectorReference) && !connectorReference.isExpression()
        ? Optional.of(connectorReference.getValue())
        : Optional.empty();
    return buildInfraExpandedValue(
        objectMapper, environment, infraConfig, connectorRefOpt.map(connectorRefToDTOMap::get).orElse(null));
  }

  Map<String, ConnectorResponseDTO> getConnectorRefToDTOMap(
      String accountId, String orgId, String projectId, List<InfrastructureConfig> finalInfrastructures) {
    Map<String, String> connectorRefToFQNMap =
        finalInfrastructures.stream()
            .filter(i -> i.getInfrastructureDefinitionConfig().getSpec() != null)
            .map(i -> i.getInfrastructureDefinitionConfig().getSpec().getConnectorReference())
            .filter(p -> !p.isExpression())
            .map(ParameterField::getValue)
            .filter(EmptyPredicate::isNotEmpty)
            .collect(Collectors.toMap(
                Function.identity(), ref -> getFullyQualifiedIdentifierFromRef(accountId, orgId, projectId, ref)));

    List<ConnectorResponseDTO> connectors =
        connectorService.listbyFQN(accountId, new ArrayList<>(connectorRefToFQNMap.values()));

    final Map<String, ConnectorResponseDTO> connectorFQNToDTOMap =
        connectors.stream().collect(Collectors.toMap(c -> getFQNFromConnector(accountId, c), Function.identity()));

    return connectorRefToFQNMap.keySet()
        .stream()
        .filter(k -> connectorFQNToDTOMap.containsKey(connectorRefToFQNMap.get(k)))
        .collect(Collectors.toMap(Function.identity(), c -> connectorFQNToDTOMap.get(connectorRefToFQNMap.get(c))));
  }

  private static String getFullyQualifiedIdentifierFromRef(
      String accountId, String orgId, String projectId, String ref) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(ref, accountId, orgId, projectId);
    return FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountId, identifierRef.getOrgIdentifier(),
        identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
  }

  private static String getFQNFromConnector(String accountId, ConnectorResponseDTO connectorResponseDTO) {
    final ConnectorInfoDTO connector = connectorResponseDTO.getConnector();
    return FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountId, connector.getOrgIdentifier(), connector.getProjectIdentifier(), connector.getIdentifier());
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
