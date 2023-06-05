/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.environment.mappers.EnvironmentMapper.toNGEnvironmentConfig;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverridesServiceV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.NGEnvironmentGlobalOverride;
import io.harness.ng.core.environment.services.impl.EnvironmentEntityYamlSchemaHelper;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverride.mapper.NGServiceOverrideEntityConfigMapper;
import io.harness.ng.core.serviceoverride.mapper.ServiceOverridesMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec.ServiceOverridesSpecBuilder;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
public class ServiceOverrideUtilityFacade {
  @Inject private NGSettingsClient ngSettingsClient;
  @Inject private ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Inject private EnvironmentEntityYamlSchemaHelper environmentEntityYamlSchemaHelper;

  @Inject private ServiceOverrideService serviceOverrideService;

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String VARIABLES_NODE = "variables";
  private static final String ENV_OVERRIDES_NODE = "overrides";

  private static final String OVERRIDE_PROJECT_SETTING_IDENTIFIER = "service_override_v2";

  public List<NGVariable> getOverrideVariables(@NonNull NGServiceOverridesEntity entity) {
    // Todo: integrate this with DeploymentStageVariableCreator
    if (Boolean.TRUE.equals(entity.getIsV2())) {
      return entity.getSpec() == null ? new ArrayList<>() : entity.getSpec().getVariables();
    }
    NGServiceOverrideConfig serviceOverrideConfig =
        NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(entity);
    return serviceOverrideConfig.getServiceOverrideInfoConfig().getVariables();
  }

  public EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> getMergedServiceOverrideConfigs(String accountId,
      String orgId, String projectId, @NonNull ServiceStepV3Parameters parameters, @NonNull Environment envEntity,
      NGLogCallback logCallback) throws IOException {
    if (ParameterField.isNull(parameters.getEnvRef()) || isEmpty(parameters.getEnvRef().getValue())
        || ParameterField.isNull(parameters.getServiceRef()) || isEmpty(parameters.getServiceRef().getValue())) {
      throw new InvalidRequestException("Environment Ref or Service Ref given for overrides has not been resolved");
    }
    if (ParameterField.isNull(parameters.getInfraId()) || isEmpty(parameters.getInfraId().getValue())) {
      log.warn("Infra Identifier has not been resolved to get service overrides");
    }

    if ((!ParameterField.isNull(parameters.getServiceOverrideInputs())
            && parameters.getServiceOverrideInputs().isExpression())
        || (!ParameterField.isNull(parameters.getEnvInputs()) && parameters.getEnvInputs().isExpression())) {
      throw new InvalidRequestException(String.format(
          "Service Override inputs [%s] or Environment inputs [%s] are not resolved",
          parameters.getServiceOverrideInputs().getExpressionValue(), parameters.getEnvInputs().getExpressionValue()));
    }

    EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> overridesMap = new EnumMap<>(ServiceOverridesType.class);
    String isOverrideV2EnabledValue =
        NGRestUtils
            .getResponse(ngSettingsClient.getSetting(OVERRIDE_PROJECT_SETTING_IDENTIFIER, accountId, orgId, projectId))
            .getValue();

    if (isOverrideV2EnabledValue.equals("true")) {
      Map<ServiceOverridesType, List<NGServiceOverridesEntity>> allTypesOverridesV2 =
          getAllOverridesWithSpecExists(parameters, accountId, orgId, projectId, logCallback);
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> acrossScopeMergedOverrides =
          getMergedOverridesAcrossScope(allTypesOverridesV2);

      if (acrossScopeMergedOverrides.containsKey(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
          && !ParameterField.isNull(parameters.getServiceOverrideInputs())) {
        NGServiceOverrideConfigV2 envServiceOverrideWithMergedInputs =
            mergeOverrideV2Inputs(acrossScopeMergedOverrides.get(ServiceOverridesType.ENV_SERVICE_OVERRIDE),
                parameters.getServiceOverrideInputs().getValue());
        acrossScopeMergedOverrides.put(ServiceOverridesType.ENV_SERVICE_OVERRIDE, envServiceOverrideWithMergedInputs);
      }

      if (acrossScopeMergedOverrides.containsKey(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
          && !ParameterField.isNull(parameters.getEnvInputs())) {
        NGServiceOverrideConfigV2 envGlobalOverrideWithMergedInputs =
            getOverrideConfigMergingEnvInputs(acrossScopeMergedOverrides.get(ServiceOverridesType.ENV_GLOBAL_OVERRIDE),
                parameters.getEnvInputs().getValue());
        acrossScopeMergedOverrides.put(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, envGlobalOverrideWithMergedInputs);
      }
      overridesMap = acrossScopeMergedOverrides;
    } else {
      Optional<NGServiceOverridesEntity> ngServiceOverridesEntity = serviceOverrideService.get(
          accountId, orgId, projectId, parameters.getEnvRef().getValue(), parameters.getServiceRef().getValue());
      if (ngServiceOverridesEntity.isPresent() && isNotEmpty(ngServiceOverridesEntity.get().getYaml())) {
        // Only ENV_SERVICE_OVERRIDE with non-empty yaml are being considered
        // Also safe assumption is that only one override will be present for one env-service mapping
        if (ParameterField.isNull(parameters.getServiceOverrideInputs())) {
          NGServiceOverrideConfigV2 overrideConfig = getOverrideConfigForNoInputsV1(ngServiceOverridesEntity.get());
          overridesMap.put(ServiceOverridesType.ENV_SERVICE_OVERRIDE, overrideConfig);
        } else {
          NGServiceOverrideConfigV2 mergedInputsOverrideConfig =
              mergeOverrideV1Inputs(ngServiceOverridesEntity.get(), parameters.getServiceOverrideInputs().getValue());
          overridesMap.put(ServiceOverridesType.ENV_SERVICE_OVERRIDE, mergedInputsOverrideConfig);
        }
      }

      // For environment global override, environment yaml is being considered
      if (isEmpty(envEntity.getYaml())) {
        setYamlInEnvironment(envEntity);
      }
      if (ParameterField.isNull(parameters.getEnvInputs())) {
        NGServiceOverrideConfigV2 envGlobalOverride = getOverrideConfigForNoInputsV1(envEntity);
        overridesMap.put(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, envGlobalOverride);
      } else {
        NGServiceOverrideConfigV2 mergedInputsEnvGlobalOverride =
            getOverrideConfigV2FromEnvYaml(envEntity, parameters.getEnvInputs().getValue());
        overridesMap.put(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, mergedInputsEnvGlobalOverride);
      }
    }

    return overridesMap;
  }

  private EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> getMergedOverridesAcrossScope(
      Map<ServiceOverridesType, List<NGServiceOverridesEntity>> overridesV2Map) {
    EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> finalMergedOverridesMap =
        new EnumMap<>(ServiceOverridesType.class);
    overridesV2Map.forEach((type, entities) -> {
      if (isNotEmpty(entities)) {
        Optional<NGServiceOverrideConfigV2> finalOverrideConfig =
            serviceOverridesServiceV2.mergeOverridesGroupedByType(entities);
        finalOverrideConfig.ifPresent(
            ngServiceOverrideConfigV2 -> finalMergedOverridesMap.put(type, ngServiceOverrideConfigV2));
      }
    });

    return finalMergedOverridesMap;
  }

  private Map<ServiceOverridesType, List<NGServiceOverridesEntity>> getAllOverridesWithSpecExists(
      ServiceStepV3Parameters parameters, String accountId, String orgId, String projectId, NGLogCallback logCallback) {
    Map<ServiceOverridesType, List<NGServiceOverridesEntity>> overridesForStep = new HashMap<>();
    Map<Scope, NGServiceOverridesEntity> envOverride = serviceOverridesServiceV2.getEnvOverride(
        accountId, orgId, projectId, parameters.getEnvRef().getValue(), logCallback);
    if (isNotEmpty(envOverride)) {
      overridesForStep.put(ServiceOverridesType.ENV_GLOBAL_OVERRIDE, new ArrayList<>(envOverride.values()));
    }

    Map<Scope, NGServiceOverridesEntity> envServiceOverride = serviceOverridesServiceV2.getEnvServiceOverride(accountId,
        orgId, projectId, parameters.getEnvRef().getValue(), parameters.getServiceRef().getValue(), logCallback);
    if (isNotEmpty(envServiceOverride)) {
      overridesForStep.put(ServiceOverridesType.ENV_SERVICE_OVERRIDE, new ArrayList<>(envServiceOverride.values()));
    }

    if (ParameterField.isNotNull(parameters.getInfraId()) && !parameters.getInfraId().isExpression()
        && isNotBlank(parameters.getInfraId().getValue())) {
      Map<Scope, NGServiceOverridesEntity> infraOverride = serviceOverridesServiceV2.getInfraOverride(accountId, orgId,
          projectId, parameters.getEnvRef().getValue(), parameters.getInfraId().getValue(), logCallback);
      if (isNotEmpty(infraOverride)) {
        overridesForStep.put(ServiceOverridesType.INFRA_GLOBAL_OVERRIDE, new ArrayList<>(infraOverride.values()));
      }

      Map<Scope, NGServiceOverridesEntity> infraServiceOverride = serviceOverridesServiceV2.getInfraServiceOverride(
          accountId, orgId, projectId, parameters.getEnvRef().getValue(), parameters.getServiceRef().getValue(),
          parameters.getInfraId().getValue(), logCallback);
      if (isNotEmpty(infraServiceOverride)) {
        overridesForStep.put(
            ServiceOverridesType.INFRA_SERVICE_OVERRIDE, new ArrayList<>(infraServiceOverride.values()));
      }
    }

    return overridesForStep;
  }

  private List<NGServiceOverridesEntity> getInfraServiceOverride(
      String accountId, String environmentRef, String serviceRef, String infraId) {
    Criteria criteria = getBasicCriteriaForOverridesV2(accountId, environmentRef)
                            .and(NGServiceOverridesEntityKeys.serviceRef)
                            .is(serviceRef)
                            .and(NGServiceOverridesEntityKeys.infraIdentifier)
                            .is(infraId);

    return serviceOverridesServiceV2.findAll(criteria);
  }

  @NonNull
  private Criteria getBasicCriteriaForOverridesV2(String accountId, String environmentRef) {
    return new Criteria()
        .and(NGServiceOverridesEntityKeys.accountId)
        .is(accountId)
        .and(NGServiceOverridesEntityKeys.environmentRef)
        .is(environmentRef)
        .and(NGServiceOverridesEntityKeys.spec)
        .exists(true)
        .ne(null);
  }

  private List<NGServiceOverridesEntity> getInfraOverride(String accountId, String environmentRef, String infraId) {
    Criteria criteria = getBasicCriteriaForOverridesV2(accountId, environmentRef)
                            .and(NGServiceOverridesEntityKeys.infraIdentifier)
                            .is(infraId);

    return serviceOverridesServiceV2.findAll(criteria);
  }

  private List<NGServiceOverridesEntity> getEnvServiceOverride(
      String accountId, String environmentRef, String serviceRef) {
    Criteria criteria = getBasicCriteriaForOverridesV2(accountId, environmentRef)
                            .and(NGServiceOverridesEntityKeys.serviceRef)
                            .is(serviceRef);

    return serviceOverridesServiceV2.findAll(criteria);
  }

  private List<NGServiceOverridesEntity> getEnvOverride(String accountId, String environmentRef) {
    Criteria criteria = getBasicCriteriaForOverridesV2(accountId, environmentRef);

    return serviceOverridesServiceV2.findAll(criteria);
  }

  private NGServiceOverrideConfigV2 mergeOverrideV1Inputs(
      NGServiceOverridesEntity entity, Map<String, Object> serviceOverrideInputs) {
    NGServiceOverrideConfig ngServiceOverrideConfig = mergeSvcOverrideInputsV1(entity.getYaml(), serviceOverrideInputs);
    return NGServiceOverrideConfigV2.builder()
        .identifier(entity.getIdentifier())
        .type(entity.getType())
        .spec(ngServiceOverrideConfig == null ? entity.getSpec() : toServiceOverrideSpec(ngServiceOverrideConfig))
        .serviceRef(entity.getServiceRef())
        .environmentRef(entity.getEnvironmentRef())
        .infraId(entity.getInfraIdentifier())
        .build();
  }

  private NGServiceOverrideConfigV2 mergeOverrideV2Inputs(
      NGServiceOverrideConfigV2 overrideConfig, Map<String, Object> serviceOverrideInputs) {
    try {
      String specYamlDummyNodeAdded = getSpecYamlForMerging(overrideConfig);
      Map<String, Object> inputsDummyNodeAdded = addDummyNodeToOverrideInputs(serviceOverrideInputs);
      String mergedYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
          specYamlDummyNodeAdded, YamlPipelineUtils.writeYamlString(inputsDummyNodeAdded), true, true);

      String yamlForSpec = isNotBlank(mergedYaml) ? removeDummyNodeFromYaml(mergedYaml) : null;
      ServiceOverridesSpec specWithMergedInput =
          (isNotBlank(yamlForSpec)) ? toServiceOverrideSpec(yamlForSpec) : overrideConfig.getSpec();
      return NGServiceOverrideConfigV2.builder()
          .identifier(overrideConfig.getIdentifier())
          .type(overrideConfig.getType())
          .spec(specWithMergedInput == null ? overrideConfig.getSpec() : specWithMergedInput)
          .serviceRef(overrideConfig.getServiceRef())
          .environmentRef(overrideConfig.getEnvironmentRef())
          .infraId(overrideConfig.getInfraId())
          .build();
    } catch (Exception e) {
      throw new InvalidRequestException("Unable to process service override spec", e);
    }
  }

  private NGServiceOverrideConfigV2 getOverrideConfigForNoInputsV1(NGServiceOverridesEntity entity) {
    return NGServiceOverrideConfigV2.builder()
        .identifier(entity.getIdentifier())
        .type(entity.getType())
        .spec(toServiceOverrideSpec(ServiceOverridesMapper.toNGServiceOverrideConfig(entity.getYaml())))
        .serviceRef(entity.getServiceRef())
        .environmentRef(entity.getEnvironmentRef())
        .infraId(entity.getInfraIdentifier())
        .build();
  }

  public NGServiceOverrideConfigV2 mergeEnvironmentInputs(NGServiceOverridesEntity envGlobalOverrideEntity,
      @NonNull Environment envEntity, Map<String, Object> envInputs) throws IOException {
    NGServiceOverrideConfigV2 ngServiceOverrideConfigV2;
    if (isEmpty(envInputs)) {
      return getOverrideConfigV2ForNoInputs(envGlobalOverrideEntity, envEntity);
    }

    if (Boolean.TRUE.equals(envEntity.getIsMigratedToOverride())) {
      ngServiceOverrideConfigV2 = getOverrideConfigV2FromOverrideEntity(envGlobalOverrideEntity, envInputs);
    } else {
      ngServiceOverrideConfigV2 = getOverrideConfigV2FromEnvYaml(envEntity, envInputs);
    }

    return ngServiceOverrideConfigV2;
  }

  private NGServiceOverrideConfigV2 getOverrideConfigV2FromEnvYaml(
      @NonNull Environment envEntity, Map<String, Object> envInputs) throws IOException {
    NGServiceOverrideConfigV2 ngServiceOverrideConfigV2;
    if (isEmpty(envEntity.getYaml())) {
      setYamlInEnvironment(envEntity);
    }

    NGEnvironmentInfoConfig ngEnvironmentInfoConfig =
        mergeEnvironmentInputsV1(envEntity.getAccountId(), envEntity.getIdentifier(), envEntity.getYaml(), envInputs)
            .getNgEnvironmentInfoConfig();
    ngServiceOverrideConfigV2 = NGServiceOverrideConfigV2.builder()
                                    .identifier(ngEnvironmentInfoConfig.getIdentifier())
                                    .type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
                                    .spec(toServiceOverrideSpec(ngEnvironmentInfoConfig))
                                    .environmentRef(getEnvironmentRef(envEntity))
                                    .build();
    return ngServiceOverrideConfigV2;
  }

  private NGServiceOverrideConfigV2 getOverrideConfigV2FromOverrideEntity(
      NGServiceOverridesEntity envGlobalOverrideEntity, Map<String, Object> envInputs) throws IOException {
    NGServiceOverrideConfigV2 ngServiceOverrideConfigV2;
    if (envGlobalOverrideEntity == null) {
      throw new InvalidRequestException("Environment is migrated to support overrides V2, but override does not exist");
    }
    String inputStringToMerge = updateInputsAndGetForMerging(envInputs);
    String specYamlToMerge = getSpecYamlForMerging(envGlobalOverrideEntity);

    String mergedYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        specYamlToMerge, inputStringToMerge, true, true);

    String yamlForSpec = isNotBlank(mergedYaml) ? removeDummyNodeFromYaml(mergedYaml) : null;
    ServiceOverridesSpec specWithMergedInput =
        (isNotBlank(yamlForSpec)) ? toServiceOverrideSpec(yamlForSpec) : envGlobalOverrideEntity.getSpec();
    ngServiceOverrideConfigV2 = NGServiceOverrideConfigV2.builder()
                                    .identifier(envGlobalOverrideEntity.getIdentifier())
                                    .environmentRef(envGlobalOverrideEntity.getEnvironmentRef())
                                    .type(envGlobalOverrideEntity.getType())
                                    .spec(specWithMergedInput)
                                    .build();
    return ngServiceOverrideConfigV2;
  }

  private NGServiceOverrideConfigV2 getOverrideConfigMergingEnvInputs(
      NGServiceOverrideConfigV2 overrideConfig, Map<String, Object> envInputs) throws IOException {
    NGServiceOverrideConfigV2 ngServiceOverrideConfigV2;

    String inputStringToMerge = updateInputsAndGetForMerging(envInputs);
    String specYamlToMerge = getSpecYamlForMerging(overrideConfig);

    String mergedYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        specYamlToMerge, inputStringToMerge, true, true);

    String yamlForSpec = isNotBlank(mergedYaml) ? removeDummyNodeFromYaml(mergedYaml) : null;
    ServiceOverridesSpec specWithMergedInput =
        (isNotBlank(yamlForSpec)) ? toServiceOverrideSpec(yamlForSpec) : overrideConfig.getSpec();
    ngServiceOverrideConfigV2 = NGServiceOverrideConfigV2.builder()
                                    .identifier(overrideConfig.getIdentifier())
                                    .environmentRef(overrideConfig.getEnvironmentRef())
                                    .type(overrideConfig.getType())
                                    .spec(specWithMergedInput)
                                    .build();
    return ngServiceOverrideConfigV2;
  }

  private NGServiceOverrideConfigV2 getOverrideConfigV2ForNoInputs(
      NGServiceOverridesEntity envGlobalOverrideEntity, @NonNull Environment envEntity) throws IOException {
    NGServiceOverrideConfigV2 ngServiceOverrideConfigV2;
    if (Boolean.TRUE.equals(envEntity.getIsMigratedToOverride()) || envGlobalOverrideEntity != null) {
      // isMigratedToOverride being true ensures that override entity exist
      if (envGlobalOverrideEntity != null) {
        ngServiceOverrideConfigV2 = NGServiceOverrideConfigV2.builder()
                                        .identifier(envGlobalOverrideEntity.getIdentifier())
                                        .type(envGlobalOverrideEntity.getType())
                                        .spec(envGlobalOverrideEntity.getSpec())
                                        .environmentRef(envGlobalOverrideEntity.getEnvironmentRef())
                                        .build();
      } else {
        throw new InvalidRequestException(
            "Environment is migrated to support overrides V2, but override does not exist");
      }

    } else {
      NGEnvironmentInfoConfig ngEnvironmentInfoConfig =
          getNGEnvironmentConfig(envEntity.getAccountId(), envEntity.getIdentifier(), envEntity.getYaml())
              .getNgEnvironmentInfoConfig();
      ngServiceOverrideConfigV2 = NGServiceOverrideConfigV2.builder()
                                      .identifier(ngEnvironmentInfoConfig.getIdentifier())
                                      .type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
                                      .spec(toServiceOverrideSpec(ngEnvironmentInfoConfig))
                                      .environmentRef(getEnvironmentRef(envEntity))
                                      .build();
    }
    return ngServiceOverrideConfigV2;
  }

  private NGServiceOverrideConfigV2 getOverrideConfigForNoInputsV1(@NonNull Environment envEntity) throws IOException {
    NGEnvironmentInfoConfig ngEnvironmentInfoConfig =
        getNGEnvironmentConfig(envEntity.getAccountId(), envEntity.getIdentifier(), envEntity.getYaml())
            .getNgEnvironmentInfoConfig();
    return NGServiceOverrideConfigV2.builder()
        .identifier(ngEnvironmentInfoConfig.getIdentifier())
        .type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
        .spec(toServiceOverrideSpec(ngEnvironmentInfoConfig))
        .environmentRef(getEnvironmentRef(envEntity))
        .build();
  }

  private String updateInputsAndGetForMerging(Map<String, Object> envInputsValue) {
    if (isEnvV1VarPresent(envInputsValue)) {
      Object envVariables = envInputsValue.get(VARIABLES_NODE);
      envInputsValue.remove(VARIABLES_NODE);
      if (isEnvV1OverridePresent(envInputsValue)) {
        Map<String, Object> overridesValues = (Map<String, Object>) envInputsValue.get(ENV_OVERRIDES_NODE);
        overridesValues.put(VARIABLES_NODE, envVariables);
      } else {
        envInputsValue.put(ENV_OVERRIDES_NODE, Map.of(VARIABLES_NODE, envVariables));
      }
    }

    Map<String, Object> overridesChildrenMap = (Map<String, Object>) envInputsValue.get(ENV_OVERRIDES_NODE);
    Map<String, Object> inputsMapToMerge = addDummyNodeToOverrideInputs(overridesChildrenMap);
    return YamlPipelineUtils.writeYamlString(inputsMapToMerge);
  }

  private NGEnvironmentConfig getNGEnvironmentConfig(String accountId, String identifier, String yaml)
      throws IOException {
    try {
      return YamlUtils.read(yaml, NGEnvironmentConfig.class);
    } catch (Exception ex) {
      environmentEntityYamlSchemaHelper.validateSchema(accountId, yaml);
      log.error(String.format(
          "Environment schema validation succeeded but failed to convert environment yaml to environment config [%s]",
          identifier));
      throw ex;
    }
  }

  private boolean isVarOrOverridePresentInInputs(Map<String, Object> envInputs) {
    return isEnvV1OverridePresent(envInputs) || isEnvV1VarPresent(envInputs);
  }

  private boolean isEnvV1OverridePresent(Map<String, Object> envInputs) {
    return envInputs.get(ENV_OVERRIDES_NODE) != null;
  }

  private boolean isEnvV1VarPresent(Map<String, Object> envInputs) {
    return envInputs.get(VARIABLES_NODE) != null;
  }

  private String removeDummyNodeFromYaml(String mergedYaml) {
    JsonNode jsonNode = readTree(mergedYaml);
    YamlConfig yamlConfig = new YamlConfig(jsonNode.get(YamlTypes.SERVICE_OVERRIDE));
    return yamlConfig.getYaml();
  }

  private Map<String, Object> addDummyNodeToOverrideInputs(Map<String, Object> serviceOverrideInputs) {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put(YamlTypes.SERVICE_OVERRIDE, serviceOverrideInputs);
    return inputs;
  }

  private String getSpecYamlForMerging(NGServiceOverridesEntity entity) throws IOException {
    String specYaml = YamlUtils.writeYamlString(entity.getSpec());
    YamlField yamlField = YamlUtils.readTree(specYaml);
    JsonNode currJsonNode = yamlField.getNode().getCurrJsonNode();
    ObjectNode dummyObjectNode = mapper.createObjectNode();
    dummyObjectNode.set(YamlTypes.SERVICE_OVERRIDE, currJsonNode);
    YamlConfig yamlConfig = new YamlConfig(dummyObjectNode);
    return yamlConfig.getYaml();
  }

  private String getSpecYamlForMerging(NGServiceOverrideConfigV2 overrideConfig) throws IOException {
    String specYaml = YamlUtils.writeYamlString(overrideConfig.getSpec());
    YamlField yamlField = YamlUtils.readTree(specYaml);
    JsonNode currJsonNode = yamlField.getNode().getCurrJsonNode();
    ObjectNode dummyObjectNode = mapper.createObjectNode();
    dummyObjectNode.set(YamlTypes.SERVICE_OVERRIDE, currJsonNode);
    YamlConfig yamlConfig = new YamlConfig(dummyObjectNode);
    return yamlConfig.getYaml();
  }

  private JsonNode readTree(String yaml) {
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      return yamlField.getNode().getCurrJsonNode();
    } catch (IOException e) {
      log.error(String.format("Exception occurred while converting yaml to jsonNode, [%s]", yaml));
      throw new InvalidRequestException("Exception occurred while converting yaml to jsonNode");
    }
  }

  private NGServiceOverrideConfig mergeSvcOverrideInputsV1(
      String originalOverridesV1Yaml, Map<String, Object> serviceOverrideInputs) {
    NGServiceOverrideConfig serviceOverrideConfig = null;

    if (isEmpty(serviceOverrideInputs)) {
      return ServiceOverridesMapper.toNGServiceOverrideConfig(originalOverridesV1Yaml);
    }
    Map<String, Object> serviceOverrideInputYaml = new HashMap<>();
    serviceOverrideInputYaml.put(YamlTypes.SERVICE_OVERRIDE, serviceOverrideInputs);
    final String mergedYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        originalOverridesV1Yaml, YamlPipelineUtils.writeYamlString(serviceOverrideInputYaml), true, true);
    if (isNotEmpty(mergedYaml)) {
      serviceOverrideConfig = ServiceOverridesMapper.toNGServiceOverrideConfig(mergedYaml);
    }
    return serviceOverrideConfig;
  }

  private ServiceOverridesSpec toServiceOverrideSpec(@NonNull NGServiceOverrideConfig configV1) {
    NGServiceOverrideInfoConfig infoConfigV1 = configV1.getServiceOverrideInfoConfig();
    return ServiceOverridesSpec.builder()
        .manifests(infoConfigV1.getManifests())
        .configFiles(infoConfigV1.getConfigFiles())
        .variables(infoConfigV1.getVariables())
        .connectionStrings(infoConfigV1.getConnectionStrings())
        .applicationSettings(infoConfigV1.getApplicationSettings())
        .build();
  }

  private ServiceOverridesSpec toServiceOverrideSpec(@NonNull NGEnvironmentInfoConfig infoConfigV1) {
    ServiceOverridesSpecBuilder builder = ServiceOverridesSpec.builder().variables(infoConfigV1.getVariables());
    if (infoConfigV1.getNgEnvironmentGlobalOverride() != null) {
      NGEnvironmentGlobalOverride ngEnvironmentGlobalOverride = infoConfigV1.getNgEnvironmentGlobalOverride();
      builder.manifests(ngEnvironmentGlobalOverride.getManifests())
          .configFiles(ngEnvironmentGlobalOverride.getConfigFiles())
          .connectionStrings(ngEnvironmentGlobalOverride.getConnectionStrings())
          .applicationSettings(ngEnvironmentGlobalOverride.getApplicationSettings());
    }
    return builder.build();
  }

  private ServiceOverridesSpec toServiceOverrideSpec(String entityYaml) {
    try {
      return YamlPipelineUtils.read(entityYaml, ServiceOverridesSpec.class);
    } catch (IOException e) {
      throw new InvalidRequestException(String.format("Cannot read serviceOverride yaml %s ", entityYaml));
    }
  }

  private void setYamlInEnvironment(Environment environment) {
    NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment);
    environment.setYaml(io.harness.ng.core.environment.mappers.EnvironmentMapper.toYaml(ngEnvironmentConfig));
  }

  private NGEnvironmentConfig mergeEnvironmentInputsV1(
      String accountId, String identifier, String yaml, Map<String, Object> environmentInputs) throws IOException {
    if (isEmpty(environmentInputs)) {
      return getNGEnvironmentConfig(accountId, identifier, yaml);
    }
    Map<String, Object> environmentInputYaml = new HashMap<>();
    environmentInputYaml.put(YamlTypes.ENVIRONMENT_YAML, environmentInputs);
    String resolvedYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        yaml, YamlPipelineUtils.writeYamlString(environmentInputYaml), true, true);

    return getNGEnvironmentConfig(accountId, identifier, resolvedYaml);
  }

  private String getEnvironmentRef(Environment environment) {
    return IdentifierRefHelper.getRefFromIdentifierOrRef(environment.getAccountId(), environment.getOrgIdentifier(),
        environment.getProjectIdentifier(), environment.getIdentifier());
  }
}
