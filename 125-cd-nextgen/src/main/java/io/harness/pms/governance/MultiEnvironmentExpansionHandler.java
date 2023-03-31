/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;
import static io.harness.pms.governance.ExpansionConstants.ENVIRONMENTS_PARALLEL_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.environment.yaml.EnvironmentsMetadata;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.common.NGExpressionUtils;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExceptionUtils;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.governance.EnvironmentExpansionUtils.InfrastructureDataBag;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class MultiEnvironmentExpansionHandler implements JsonExpansionHandler {
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureEntityService infrastructureService;
  private final ObjectMapper objectMapper = new ObjectMapper();
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Override
  public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata, String fqn) {
    try {
      JsonNode environments = fieldValue.get(EnvironmentsYaml.keys.values);
      if (!environments.isArray()) {
        return ExpansionResponse.builder().success(false).errorMessage("field is not an array").build();
      }

      List<SingleEnvironmentExpandedValue> values = new ArrayList<>();
      environments.forEach(environmentNode -> values.add(toSingleEnvironmentExpandedValue(metadata, environmentNode)));

      JsonNode metaDataNode = fieldValue.get(EnvironmentsYaml.METADATA);

      final Map<String, Object> metaData = new HashMap<>();
      if (metaDataNode != null && metaDataNode.isObject()) {
        EnvironmentsMetadata environmentsMetadata = objectMapper.treeToValue(metaDataNode, EnvironmentsMetadata.class);
        if (environmentsMetadata != null) {
          metaData.put(ENVIRONMENTS_PARALLEL_KEY, environmentsMetadata.getParallel());
        }
      }

      MultiEnvExpandedValue value = MultiEnvExpandedValue.builder().environments(values).metadata(metaData).build();
      return ExpansionResponse.builder()
          .success(true)
          .placement(ExpansionPlacementStrategy.REPLACE)
          .key(value.getKey())
          .value(value)
          .build();
    } catch (Exception ex) {
      return ExpansionResponse.builder().success(false).errorMessage(ExceptionUtils.getMessage(ex)).build();
    }
  }

  private SingleEnvironmentExpandedValue toSingleEnvironmentExpandedValue(
      ExpansionRequestMetadata metadata, JsonNode envYamlV2Node) {
    final String accountId = metadata.getAccountId();
    final String orgId = metadata.getOrgId();
    final String projectId = metadata.getProjectId();

    final Optional<String> envRefOpt = EnvironmentExpansionUtils.getEnvRefFromEnvYamlV2Node(envYamlV2Node);
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

  private List<InfrastructureExpandedValue> buildInfrastructureList(
      ExpansionRequestMetadata metadata, Environment environment, String environmentRef, JsonNode envNode) {
    final String accountId = metadata.getAccountId();
    final String orgId = metadata.getOrgId();
    final String projectId = metadata.getProjectId();

    final List<InfrastructureDataBag> infrastructuresData = EnvironmentExpansionUtils.getInfraRefAndInputs(envNode);
    if (EmptyPredicate.isEmpty(infrastructuresData)) {
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

    if (EmptyPredicate.isEmpty(infrastructures)) {
      return List.of();
    }

    final Map<String, InfrastructureEntity> infraIdToEntityMap =
        infrastructures.stream().collect(Collectors.toMap(InfrastructureEntity::getIdentifier, Function.identity()));

    final List<InfrastructureConfig> validInfrastructures = new ArrayList<>();
    identifiers.forEach(i -> {
      if (infraIdToEntityMap.containsKey(i)) {
        final InfrastructureConfig infrastructureConfig;
        if (infraWithInputs.containsKey(i)) {
          infrastructureConfig = EnvironmentExpansionUtils.mergeInfraInputs(
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

  private InfrastructureExpandedValue mapInfrastructureConfigToExpandedValue(InfrastructureConfig infraConfig,
      Environment environment, Map<String, ConnectorResponseDTO> connectorRefToDTOMap) {
    final ParameterField<String> connectorReference =
        infraConfig.getInfrastructureDefinitionConfig().getSpec().getConnectorReference();
    final Optional<String> connectorRefOpt =
        ParameterField.isNotNull(connectorReference) && !connectorReference.isExpression()
        ? Optional.of(connectorReference.getValue())
        : Optional.empty();
    return EnvironmentExpansionUtils.buildInfraExpandedValue(
        objectMapper, environment, infraConfig, connectorRefOpt.map(connectorRefToDTOMap::get).orElse(null));
  }

  private Map<String, ConnectorResponseDTO> getConnectorRefToDTOMap(
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
}
