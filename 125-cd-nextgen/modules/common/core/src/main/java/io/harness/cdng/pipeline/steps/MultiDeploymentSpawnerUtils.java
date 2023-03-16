/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.environment.helper.EnvironmentStepsUtils;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.JsonUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MultiDeploymentSpawnerUtils {
  private static final String SERVICE_REF = "serviceRef";
  private static final String SERVICE_INPUTS = "serviceInputs";
  private static final String USE_FROM_STAGE = "useFromStage";

  private static final String ENVIRONMENT_REF = "environmentRef";
  private static final String ENVIRONMENT_INPUTS = "environmentInputs";
  private static final String SERVICE_OVERRIDE_INPUTS = "serviceOverrideInputs";
  private static final String GIT_OPS_CLUSTERS = "gitOpsClusters";
  private static final String INFRA_IDENTIFIER = "identifier";
  private static final String INFRA_INPUTS = "infraInputs";
  private static final String MATRIX_EXPRESSION = "<+matrix.%s>";

  private static final String ENVIRONMENT_REF_EXPRESSION = String.format(MATRIX_EXPRESSION, "environmentRef");
  private static final String ENVIRONMENT_INPUTS_EXPRESSION = String.format(MATRIX_EXPRESSION, "environmentInputs");
  public static final String SERVICE_OVERRIDE_INPUTS_EXPRESSION =
      String.format(MATRIX_EXPRESSION, "serviceOverrideInputs");
  private static final String GIT_OPS_CLUSTERS_EXPRESSION = String.format(MATRIX_EXPRESSION, "gitOpsClusters");

  private static final String INFRA_IDENTIFIER_EXPRESSION = String.format(MATRIX_EXPRESSION, "identifier");
  private static final String INFRA_INPUTS_EXPRESSION = String.format(MATRIX_EXPRESSION, "infraInputs");

  public static final String SERVICE_REF_EXPRESSION = "<+matrix." + SERVICE_REF + ">";
  public static final String SERVICE_INPUTS_EXPRESSION = "<+matrix." + SERVICE_INPUTS + ">";
  public static final String USE_FROM_STAGE_EXPRESSION = "<+matrix." + USE_FROM_STAGE + ">";

  public static final String MULTI_SERVICE_DEPLOYMENT = "MULTI_SERVICE_DEPLOYMENT";
  public static final String MULTI_ENV_DEPLOYMENT = "MULTI_ENV_DEPLOYMENT";
  public static final String MULTI_SERVICE_ENV_DEPLOYMENT = "MULTI_SERVICE_ENV_DEPLOYMENT";

  Map<String, String> getMapFromServiceYaml(ServiceYamlV2 service) {
    Map<String, String> matrixMetadataMap = new HashMap<>();
    matrixMetadataMap.put(SERVICE_REF, service.getServiceRef().getValue());
    if (!ParameterField.isBlank(service.getServiceInputs())
        && EmptyPredicate.isNotEmpty(service.getServiceInputs().getValue())) {
      matrixMetadataMap.put(SERVICE_INPUTS, JsonUtils.asJson(service.getServiceInputs().getValue()));
    }
    if (service.getUseFromStage() != null) {
      matrixMetadataMap.put(USE_FROM_STAGE, JsonUtils.asJson(service.getUseFromStage()));
    }
    return matrixMetadataMap;
  }

  public String getServiceRef(Map<String, String> serviceMap) {
    return serviceMap.get(SERVICE_REF);
  }

  Map<String, String> getMapFromEnvironmentYaml(EnvironmentYamlV2 environmentYamlV2,
      InfraStructureDefinitionYaml infraStructureDefinitionYaml, Scope envGroupScope) {
    Map<String, String> matrixMetadataMap = new HashMap<>();
    matrixMetadataMap.put(ENVIRONMENT_REF,
        EnvironmentStepsUtils.getEnvironmentRef(environmentYamlV2.getEnvironmentRef().getValue(), envGroupScope));
    if (!ParameterField.isBlank(environmentYamlV2.getEnvironmentInputs())
        && EmptyPredicate.isNotEmpty(environmentYamlV2.getEnvironmentInputs().getValue())) {
      matrixMetadataMap.put(ENVIRONMENT_INPUTS, JsonUtils.asJson(environmentYamlV2.getEnvironmentInputs().getValue()));
    }
    if (!ParameterField.isBlank(environmentYamlV2.getServiceOverrideInputs())) {
      matrixMetadataMap.put(
          SERVICE_OVERRIDE_INPUTS, JsonUtils.asJson(environmentYamlV2.getServiceOverrideInputs().getValue()));
    }
    if (!ParameterField.isBlank(environmentYamlV2.getGitOpsClusters())) {
      matrixMetadataMap.put(GIT_OPS_CLUSTERS, JsonUtils.asJson(environmentYamlV2.getGitOpsClusters().getValue()));
    } else {
      if (infraStructureDefinitionYaml == null || infraStructureDefinitionYaml.getIdentifier() == null) {
        throw new InvalidRequestException(String.format(
            "Infrastructure Definition is not provided for environment %s, Please provide infrastructure definition and try again",
            environmentYamlV2.getEnvironmentRef().getValue()));
      }
      matrixMetadataMap.put(INFRA_IDENTIFIER, infraStructureDefinitionYaml.getIdentifier().getValue());
    }
    if (!ParameterField.isBlank(infraStructureDefinitionYaml.getInputs())) {
      matrixMetadataMap.put(INFRA_INPUTS, JsonUtils.asJson(infraStructureDefinitionYaml.getInputs().getValue()));
    }
    return matrixMetadataMap;
  }

  public void addServiceOverridesToMap(Map<String, String> environmentsMap, Map<String, Object> serviceOverrideInputs) {
    if (EmptyPredicate.isNotEmpty(serviceOverrideInputs)) {
      environmentsMap.put(SERVICE_OVERRIDE_INPUTS, JsonUtils.asJson(serviceOverrideInputs));
    }
  }

  public String getUuidForMultiDeployment(DeploymentStageNode node) {
    DeploymentStageConfig config = node.getDeploymentStageConfig();
    if (config.getServices() != null) {
      return config.getServices().getUuid();
    }

    if (!config.getGitOpsEnabled()) {
      if (config.getEnvironments() != null) {
        return config.getEnvironments().getUuid();
      }
      if (config.getEnvironmentGroup() != null) {
        return config.getEnvironmentGroup().getUuid();
      }
    }
    return node.getUuid();
  }

  public boolean hasMultiDeploymentConfigured(DeploymentStageNode node) {
    DeploymentStageConfig config = node.getDeploymentStageConfig();
    return hasMultiDeploymentConfigured(config);
  }

  public boolean hasMultiDeploymentConfigured(DeploymentStageConfig config) {
    if (config.getGitOpsEnabled()) {
      return config.getServices() != null;
    }
    return config.getServices() != null || config.getEnvironments() != null || config.getEnvironmentGroup() != null;
  }

  public void validateMultiServiceInfra(DeploymentStageConfig stageConfig) {
    if (stageConfig.getServices() == null && stageConfig.getEnvironments() == null
        && stageConfig.getEnvironmentGroup() == null) {
      return;
    }
    if (stageConfig.getServices() != null
        && (ParameterField.isNull(stageConfig.getServices().getValues())
            || (!stageConfig.getServices().getValues().isExpression()
                && isEmpty(stageConfig.getServices().getValues().getValue())))) {
      throw new InvalidRequestException("No value of services provided, please provide at least one value of service");
    }
    if (stageConfig.getEnvironments() != null && ParameterField.isNotNull(stageConfig.getEnvironments().getValues())
        && !stageConfig.getEnvironments().getValues().isExpression()) {
      if (ParameterField.isNotNull(stageConfig.getEnvironments().getFilters())
          && EmptyPredicate.isNotEmpty(stageConfig.getEnvironments().getFilters().getValue())) {
        return;
      }
      if (isEmpty(stageConfig.getEnvironments().getValues().getValue())) {
        throw new InvalidRequestException(
            "No value of environments provided, please provide at least one value of environment");
      }
      for (EnvironmentYamlV2 environmentYamlV2 : stageConfig.getEnvironments().getValues().getValue()) {
        if (ParameterField.isNotNull(environmentYamlV2.getFilters())
            && isNotEmpty(environmentYamlV2.getFilters().getValue())) {
          return;
        }
        if (ParameterField.isNull(environmentYamlV2.getInfrastructureDefinitions())) {
          if (environmentYamlV2.getEnvironmentRef().getValue() != null) {
            throw new InvalidRequestException(
                String.format("No value of infrastructures provided for infrastructure [%s], please provide"
                        + " at least one value of environment",
                    environmentYamlV2.getEnvironmentRef().getValue()));
          } else {
            throw new InvalidRequestException(
                "No value of infrastructures provided for infrastructure, please provide at least one value of environment");
          }
        }
        if (!environmentYamlV2.getInfrastructureDefinitions().isExpression()
            && isEmpty(environmentYamlV2.getInfrastructureDefinitions().getValue())) {
          if (environmentYamlV2.getEnvironmentRef().getValue() != null) {
            throw new InvalidRequestException(
                String.format("No value of infrastructures provided for infrastructure [%s], please provide"
                        + " at least one value of environment",
                    environmentYamlV2.getEnvironmentRef().getValue()));
          } else {
            throw new InvalidRequestException(
                "No value of infrastructures provided for infrastructure, please provide at least one value of environment");
          }
        }
      }
    }
  }

  public ServiceYamlV2 getServiceYamlV2Node() {
    return ServiceYamlV2.builder()
        .uuid(UUIDGenerator.generateUuid())
        .serviceRef(ParameterField.createExpressionField(true, SERVICE_REF_EXPRESSION, null, true))
        .serviceInputs(ParameterField.createExpressionField(true, SERVICE_INPUTS_EXPRESSION, null, false))
        .build();
  }

  public EnvironmentYamlV2 getEnvironmentYamlV2Node() {
    InfraStructureDefinitionYaml infraStructureDefinitionYaml =
        InfraStructureDefinitionYaml.builder()
            .identifier(ParameterField.createExpressionField(true, INFRA_IDENTIFIER_EXPRESSION, null, true))
            .inputs(ParameterField.createExpressionField(true, INFRA_INPUTS_EXPRESSION, null, false))
            .build();
    return EnvironmentYamlV2.builder()
        .environmentRef(ParameterField.createExpressionField(true, ENVIRONMENT_REF_EXPRESSION, null, true))
        .environmentInputs(ParameterField.createExpressionField(true, ENVIRONMENT_INPUTS_EXPRESSION, null, false))
        .serviceOverrideInputs(
            ParameterField.createExpressionField(true, SERVICE_OVERRIDE_INPUTS_EXPRESSION, null, false))
        .gitOpsClusters(ParameterField.createExpressionField(true, GIT_OPS_CLUSTERS_EXPRESSION, null, false))
        .infrastructureDefinition(ParameterField.createValueField(infraStructureDefinitionYaml))
        .build();
  }

  public int getEnvCount(List<EnvironmentMapResponse> environmentMapList) {
    Set<String> envs = new HashSet<>();
    for (EnvironmentMapResponse environmentMapResponse : environmentMapList) {
      Map<String, String> envMap = environmentMapResponse.getEnvironmentsMapList();
      if (envMap.containsKey(ENVIRONMENT_REF)) {
        envs.add(envMap.get(ENVIRONMENT_REF));
      }
    }
    return envs.size();
  }
}
