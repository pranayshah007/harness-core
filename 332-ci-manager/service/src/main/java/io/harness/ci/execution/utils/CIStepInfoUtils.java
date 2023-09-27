/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.ci.commonconstants.CIExecutionConstants.BASE_AZURE_HOSTNAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.BASE_ECR_HOSTNAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.BASE_GCR_HOSTNAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.HTTPS_URL;
import static io.harness.ci.commonconstants.CIExecutionConstants.PATH_SEPARATOR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.FeatureName;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIRegistry;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.SecurityStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOGenericStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.execution.execution.CIExecutionConfigService;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.common.NGExpressionUtils;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.ssca.beans.stepinfo.ProvenanceStepInfo;
import io.harness.sto.config.STOImageConfig;
import io.harness.sto.config.STOStepConfig;
import io.harness.sto.utils.STOSettingsUtils;
import io.harness.yaml.core.variables.OutputNGVariable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CIStepInfoUtils {
  public static String getPluginCustomStepImage(
      PluginCompatibleStep step, CIExecutionConfigService ciExecutionConfigService, Type infraType, String accountId) {
    if (infraType == Type.K8) {
      return getK8PluginCustomStepImageConfig(step, ciExecutionConfigService, accountId).getImage();
    } else if (infraType == Type.VM || infraType == Type.DLITE_VM) {
      return getVmPluginCustomStepImageConfig(step, ciExecutionConfigService, accountId);
    }
    return null;
  }

  public static String getPluginVersionForInfra(CIExecutionConfigService ciExecutionConfigService,
      CIStepInfoType stepInfoType, String accountId, Infrastructure infrastructure) {
    if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      return ciExecutionConfigService.getPluginVersionForK8(stepInfoType, accountId).getImage();
    } else {
      return ciExecutionConfigService.getPluginVersionForVM(stepInfoType, accountId);
    }
  }

  public static ParameterField<Boolean> getPrivilegedMode(PluginCompatibleStep step) {
    switch (step.getNonYamlInfo().getStepInfoType()) {
      case SECURITY:
        return ((SecurityStepInfo) step).getPrivileged();
      default:
        return null;
    }
  }

  public static List<String> getOutputVariables(PluginCompatibleStep step) {
    switch (step.getNonYamlInfo().getStepInfoType()) {
      case SECURITY:
        ParameterField<List<OutputNGVariable>> outputVars = ((SecurityStepInfo) step).getOutputVariables();

        if (isNotEmpty(outputVars.getValue())) {
          return outputVars.getValue().stream().map(OutputNGVariable::getName).collect(Collectors.toList());
        }

        return Collections.emptyList();
      default:
        return Collections.emptyList();
    }
  }

  public static ParameterField<ImagePullPolicy> getImagePullPolicy(PluginCompatibleStep step) {
    switch (step.getNonYamlInfo().getStepInfoType()) {
      case SECURITY:
        return ((SecurityStepInfo) step).getImagePullPolicy();
      default:
        return new ParameterField<>();
    }
  }

  public static List<String> getK8PluginCustomStepEntrypoint(
      PluginCompatibleStep step, CIExecutionConfigService ciExecutionConfigService, String accountId, OSType os) {
    StepImageConfig stepImageConfig = getK8PluginCustomStepImageConfig(step, ciExecutionConfigService, accountId);
    if (os == OSType.Windows) {
      return stepImageConfig.getWindowsEntrypoint();
    }
    return stepImageConfig.getEntrypoint();
  }

  public Optional<String> resolveConnectorFromRegistries(List<CIRegistry> registries, String image) {
    if (isEmpty(registries) || isEmpty(image)) {
      return Optional.empty();
    }
    // first try to match the ones having match property
    Optional<String> finalConnector = findConnectorUsingRegex(registries, image);
    if (finalConnector.isPresent()) {
      return finalConnector;
    }
    // choose connector with same type as image
    finalConnector = findConnectorByType(registries, image);
    return finalConnector;
  }

  private Optional<String> findConnectorUsingRegex(List<CIRegistry> registries, String image) {
    String credential =
        registries.stream()
            .filter(r -> {
              String match = r.getMatch();
              return isNotEmpty(match) && NGExpressionUtils.containsPattern(Pattern.compile(match), image);
            })
            .map(CIRegistry::getConnectorIdentifier)
            .findFirst()
            .orElse(null);

    return Optional.ofNullable(credential);
  }

  private Optional<String> findConnectorByType(List<CIRegistry> registries, String image) {
    ConnectorType imageConnectorType = getConnectorTypeFromImage(image);
    String finalIdentifier = registries.stream()
                                 .filter(r -> isEmpty(r.getMatch()) && r.getConnectorType() == imageConnectorType)
                                 .map(CIRegistry::getConnectorIdentifier)
                                 .findFirst()
                                 .orElse(null);
    return Optional.ofNullable(finalIdentifier);
  }

  private static ConnectorType getConnectorTypeFromImage(String image) {
    if (image.startsWith(HTTPS_URL)) {
      image = image.substring(HTTPS_URL.length());
    }

    String[] imageParts = image.split(PATH_SEPARATOR);
    if (imageParts.length > 1) {
      String baseImagePath = imageParts[0];
      if (baseImagePath.endsWith(BASE_GCR_HOSTNAME)) {
        return ConnectorType.GCP;
      } else if (baseImagePath.endsWith(BASE_ECR_HOSTNAME)) {
        return ConnectorType.AWS;
      } else if (baseImagePath.endsWith(BASE_AZURE_HOSTNAME)) {
        return ConnectorType.AZURE;
      }
    }
    return ConnectorType.DOCKER;
  }

  private static StepImageConfig getSecurityStepImageConfig(PluginCompatibleStep step,
      CIExecutionConfigService ciExecutionConfigService, StepImageConfig defaultImageConfig) {
    if (step instanceof STOGenericStepInfo) {
      STOStepConfig stoStepsConfig = ciExecutionConfigService.getCiExecutionServiceConfig().getStoStepConfig();
      STOGenericStepInfo genericStep = (STOGenericStepInfo) step;
      String stepTypeName = step.getStepType().getType().toLowerCase();
      String stepConfigName = STOSettingsUtils.getProductConfigName(genericStep);
      String stepProductName = String.join("_", stepTypeName, stepConfigName);

      List<STOImageConfig> stoStepImages = stoStepsConfig.getImages();
      String defaultTag = stoStepsConfig.getDefaultTag();
      List<String> defaultEntryPoint = stoStepsConfig.getDefaultEntrypoint();
      Optional<STOImageConfig> optionalSTOImageConfig =
          stoStepImages.stream().filter(el -> stepProductName.matches(el.getProduct())).findFirst();

      if (optionalSTOImageConfig.isPresent()) {
        STOImageConfig stepImageConfig = optionalSTOImageConfig.get();
        String tag = stepImageConfig.getTag() == null ? defaultTag : stepImageConfig.getTag();
        List<String> entrypoint =
            stepImageConfig.getEntrypoint() == null ? defaultEntryPoint : stepImageConfig.getEntrypoint();
        return StepImageConfig.builder()
            .image(String.join(":", stepImageConfig.getImage(), tag))
            .entrypoint(entrypoint)
            .build();
      }
    }

    return defaultImageConfig;
  }

  private static StepImageConfig getK8PluginCustomStepImageConfig(
      PluginCompatibleStep step, CIExecutionConfigService ciExecutionConfigService, String accountId) {
    CIStepInfoType stepInfoType = getStepInfoType(step);
    StepImageConfig defaultImageConfig = ciExecutionConfigService.getPluginVersionForK8(stepInfoType, accountId);
    if (stepInfoType == CIStepInfoType.SECURITY) {
      return getSecurityStepImageConfig(step, ciExecutionConfigService, defaultImageConfig);
    }
    return defaultImageConfig;
  }

  private static CIStepInfoType getStepInfoType(PluginCompatibleStep step) {
    CIStepInfoType stepInfoType = step.getNonYamlInfo().getStepInfoType();
    if (stepInfoType == CIStepInfoType.PROVENANCE) {
      ProvenanceStepInfo stepInfo = (ProvenanceStepInfo) step;
      if (stepInfo.getSource() == null) {
        throw new CIStageExecutionException("Provenance source is not provided to fetch image from.");
      }
      switch (stepInfo.getSource().getType()) {
        case DOCKER:
          return CIStepInfoType.PROVENANCE;
        case GCR:
          return CIStepInfoType.PROVENANCE_GCR;
        default:
          throw new CIStageExecutionException(
              "Initialization not handled for provenance subtype of " + stepInfo.getSource().getType());
      }
    }
    return stepInfoType;
  }

  private static String getVmPluginCustomStepImageConfig(
      PluginCompatibleStep step, CIExecutionConfigService ciExecutionConfigService, String accountId) {
    CIStepInfoType stepInfoType = step.getNonYamlInfo().getStepInfoType();
    String defaultImage = ciExecutionConfigService.getPluginVersionForVM(stepInfoType, accountId);
    StepImageConfig defaultImageConfig = StepImageConfig.builder().image(defaultImage).build();
    if (stepInfoType == CIStepInfoType.SECURITY) {
      return getSecurityStepImageConfig(step, ciExecutionConfigService, defaultImageConfig).getImage();
    }
    return defaultImage;
  }

  public static boolean canRunVmStepOnHost(CIStepInfoType ciStepInfoType, StageInfraDetails stageInfraDetails,
      String accountId, CIExecutionConfigService ciExecutionConfigService, CIFeatureFlagService featureFlagService,
      PluginCompatibleStep pluginCompatibleStep) {
    if (stageInfraDetails.getType() != Type.DLITE_VM) {
      return false;
    }
    if (!featureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId)) {
      return false;
    }
    String pluginName = ciExecutionConfigService.getContainerlessPluginNameForVM(ciStepInfoType, pluginCompatibleStep);
    return isNotEmpty(pluginName);
  }

  public static Map<String, String> injectAndResolveLoopingVariables(
      Ambiance ambiance, String accountId, CIFeatureFlagService featureFlagService, Map<String, String> envs) {
    if (isEmpty(envs)) {
      envs = new HashMap<>();
    }
    Optional<Level> optionalStageLevel = AmbianceUtils.getStageLevelFromAmbiance(ambiance);
    Level stepLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    if (optionalStageLevel.isPresent() && stepLevel != null) {
      StrategyMetadata stageStrategyMetadata = optionalStageLevel.get().getStrategyMetadata();
      StrategyMetadata stepStrategyMetadata = stepLevel.getStrategyMetadata();
      int stageCurrentIteration = stageStrategyMetadata.getCurrentIteration();
      int stageTotalIterations =
          stageStrategyMetadata.getTotalIterations() > 0 ? stageStrategyMetadata.getTotalIterations() : 1;
      int stepCurrentIteration = stepStrategyMetadata.getCurrentIteration();
      int stepTotalIterations =
          stepStrategyMetadata.getTotalIterations() > 0 ? stepStrategyMetadata.getTotalIterations() : 1;
      int harnessNodeIndex = 0;
      int harnessNodeTotal = 1;

      // Currently <+strategy.iteration> takes precedence in this manner -> step > sg > stage
      if (stepTotalIterations > 1) {
        harnessNodeIndex = stepCurrentIteration;
        harnessNodeTotal = stepTotalIterations;
      } else if (stageTotalIterations > 1) {
        harnessNodeIndex = stageCurrentIteration;
        harnessNodeTotal = stageTotalIterations;
      }

      envs.putIfAbsent("HARNESS_STAGE_INDEX", String.valueOf(stageCurrentIteration));
      envs.putIfAbsent("HARNESS_STAGE_TOTAL", String.valueOf(stageTotalIterations));
      envs.putIfAbsent("HARNESS_STEP_INDEX", String.valueOf(stepCurrentIteration));
      envs.putIfAbsent("HARNESS_STEP_TOTAL", String.valueOf(stepTotalIterations));
      envs.putIfAbsent("HARNESS_NODE_INDEX", String.valueOf(harnessNodeIndex));
      envs.putIfAbsent("HARNESS_NODE_TOTAL", String.valueOf(harnessNodeTotal));
    }
    return envs;
  }
}
