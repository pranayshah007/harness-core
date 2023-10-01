/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.helpers;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ExecutionStrategyType;
import io.harness.beans.FeatureName;
import io.harness.cdng.deploymentmetadata.DeploymentMetadataServiceHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.featureFlag.CdEnumFilter;
import io.harness.cdng.infra.beans.ProvisionerType;
import io.harness.cdng.pipeline.StepCategory;
import io.harness.cdng.pipeline.StepData;
import io.harness.cdng.pipeline.steptype.NGStepType;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.steps.matrix.StrategyParameters;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_ECS})
@OwnedBy(CDP)
public class CDNGPipelineConfigurationHelper {
  @Inject private CdEnumFilter enumFilter;
  @Inject private CDNGPipelineExecutionStrategyHelperV2 cdngPipelineExecutionStrategyHelperV2;
  @Inject private CDFeatureFlagHelper featureFlagHelper;
  @Inject private DeploymentMetadataServiceHelper deploymentMetadataServiceHelper;

  @VisibleForTesting static String LIBRARY = "Library";

  private final LoadingCache<ServiceDefinitionType, StepCategory> stepsCache =
      CacheBuilder.newBuilder().build(new CacheLoader<ServiceDefinitionType, StepCategory>() {
        @Override
        public StepCategory load(final ServiceDefinitionType serviceDefinitionType) throws Exception {
          return calculateStepsForServiceDefinitionType(serviceDefinitionType);
        }
      });

  public Map<ServiceDefinitionType, List<ExecutionStrategyType>> getExecutionStrategyList() {
    return Arrays.stream(ServiceDefinitionType.values())
        .collect(Collectors.toMap(
            serviceDefinitionType -> serviceDefinitionType, ServiceDefinitionType::getExecutionStrategies));
  }

  public String getExecutionStrategyYaml(ServiceDefinitionType serviceDefinitionType,
      ExecutionStrategyType executionStrategyType, boolean includeVerify, String deploymentMetaDataYaml,
      String accountIdentifier) throws IOException {
    // Note: Additional condition for GitOps is added because we do not want to show the GitOps Strategy in
    // the UI but also provide the support to UI for default yaml
    ClassLoader classLoader = this.getClass().getClassLoader();
    if (ServiceDefinitionType.getExecutionStrategies(serviceDefinitionType).contains(executionStrategyType)
        || executionStrategyType == ExecutionStrategyType.GITOPS) {
      String executionStrategyTypeValue = executionStrategyType.getDisplayName().toLowerCase();
      if (ServiceDefinitionType.GOOGLE_CLOUD_FUNCTIONS.equals(serviceDefinitionType)) {
        executionStrategyTypeValue = deploymentMetadataServiceHelper.filterStrategyTypeOnDeploymentMetadata(
            serviceDefinitionType, deploymentMetaDataYaml, executionStrategyType);
      }
      if (ServiceDefinitionType.SERVERLESS_AWS_LAMBDA.equals(serviceDefinitionType)
          && ExecutionStrategyType.BASIC.equals(executionStrategyType) && EmptyPredicate.isNotEmpty(accountIdentifier)
          && featureFlagHelper.isEnabled(accountIdentifier, FeatureName.CDS_SERVERLESS_V2)) {
        executionStrategyTypeValue = "basic-plugin";
      }
      return Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              String.format("executionStrategyYaml/%s-%s%s.yaml", serviceDefinitionType.getYamlName().toLowerCase(),
                  executionStrategyTypeValue, includeVerify ? "-with-verify" : ""))),
          StandardCharsets.UTF_8);
    } else {
      throw new GeneralException("Execution Strategy Not supported for given deployment type");
    }
  }

  public String generateExecutionStrategyYaml(String accountIdentifier, ServiceDefinitionType serviceDefinitionType,
      ExecutionStrategyType executionStrategyType, boolean includeVerify, StrategyParameters strategyParameters)
      throws IOException {
    String yamlName = serviceDefinitionType.getYamlName();
    if (ServiceDefinitionType.SSH.getYamlName().equals(yamlName)
        || ServiceDefinitionType.WINRM.getYamlName().equals(yamlName)) {
      return generateSshWinRmExecutionStrategyYaml(
          accountIdentifier, serviceDefinitionType, executionStrategyType, includeVerify, strategyParameters);
    }
    throw new InvalidRequestException(
        String.format("Execution Strategy not supported for service type, yamlName: %s", yamlName));
  }

  private String generateSshWinRmExecutionStrategyYaml(String accountIdentifier,
      ServiceDefinitionType serviceDefinitionType, ExecutionStrategyType executionStrategyType, boolean includeVerify,
      StrategyParameters strategyParameters) throws IOException {
    if (ExecutionStrategyType.CANARY.equals(executionStrategyType)) {
      return cdngPipelineExecutionStrategyHelperV2.generateCanaryYaml(
          accountIdentifier, serviceDefinitionType, includeVerify, strategyParameters);
    } else if (ExecutionStrategyType.ROLLING.equals(executionStrategyType)) {
      return cdngPipelineExecutionStrategyHelperV2.generateRollingYaml(
          accountIdentifier, serviceDefinitionType, includeVerify, strategyParameters);
    } else if (ExecutionStrategyType.BASIC.equals(executionStrategyType)) {
      return cdngPipelineExecutionStrategyHelperV2.generateBasicYaml(
          accountIdentifier, serviceDefinitionType, includeVerify, strategyParameters);
    } else {
      return getExecutionStrategyYaml(
          serviceDefinitionType, executionStrategyType, includeVerify, null, accountIdentifier);
    }
  }

  public String getProvisionerExecutionStrategyYaml(ProvisionerType type) throws IOException {
    if (!ProvisionerType.isSupported(type)) {
      throw new GeneralException(String.format("Provisioner Type: [%s] is not supported", type.getDisplayName()));
    }
    ClassLoader classLoader = this.getClass().getClassLoader();
    return Resources.toString(Objects.requireNonNull(classLoader.getResource(String.format(
                                  "provisionerStrategyYaml/%s.yaml", type.getDisplayName().toLowerCase()))),
        StandardCharsets.UTF_8);
  }

  public List<ServiceDefinitionType> getServiceDefinitionTypes(String accountId) {
    return Arrays.stream(ServiceDefinitionType.values())
        .filter(enumFilter.filter(accountId, FeatureName.NG_SVC_ENV_REDESIGN))
        .collect(Collectors.toList());
  }

  public StepCategory getSteps(ServiceDefinitionType serviceDefinitionType) {
    try {
      return stepsCache.get(serviceDefinitionType);
    } catch (ExecutionException e) {
      throw new GeneralException("Exception occurred while calculating the list of steps: " + e.getMessage());
    }
  }

  private StepCategory calculateStepsForServiceDefinitionType(ServiceDefinitionType serviceDefinitionType) {
    List<NGStepType> filteredNGStepTypes =
        Arrays.stream(NGStepType.values())
            .filter(ngStepType -> NGStepType.getServiceDefinitionTypes(ngStepType).contains(serviceDefinitionType))
            .collect(Collectors.toList());
    StepCategory stepCategory = StepCategory.builder().name(LIBRARY).build();
    for (NGStepType stepType : filteredNGStepTypes) {
      addToTopLevel(stepCategory, stepType);
    }
    return stepCategory;
  }

  public StepCategory getStepsForProvisioners() {
    List<NGStepType> steps = ProvisionerType.getSupportedSteps();
    StepCategory stepCategory = StepCategory.builder().name(LIBRARY).build();
    for (NGStepType stepType : steps) {
      addToTopLevel(stepCategory, stepType);
    }
    return stepCategory;
  }

  private void addToTopLevel(StepCategory stepCategory, NGStepType stepType) {
    String categories = NGStepType.getCategory(stepType);
    String[] categoryArrayName = categories.split("/");
    StepCategory currentStepCategory = stepCategory;
    for (String catogoryName : categoryArrayName) {
      currentStepCategory = currentStepCategory.getOrCreateChildStepCategory(catogoryName);
    }
    currentStepCategory.addStepData(
        StepData.builder().name(NGStepType.getDisplayName(stepType)).type(stepType).build());
  }
}
