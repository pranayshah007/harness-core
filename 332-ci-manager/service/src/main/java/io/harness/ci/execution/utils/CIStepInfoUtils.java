/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.ci.commonconstants.CIExecutionConstants.BASE_AZURE_HOSTNAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.BASE_ECR_HOSTNAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.BASE_GCR_HOSTNAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.HTTPS_URL;
import static io.harness.ci.commonconstants.CIExecutionConstants.PATH_SEPARATOR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIRegistry;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.SecurityStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOGenericStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.common.NGExpressionUtils;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.sto.config.STOImageConfig;
import io.harness.sto.config.STOStepConfig;
import io.harness.sto.utils.STOSettingsUtils;
import io.harness.yaml.core.variables.OutputNGVariable;

import java.util.Collections;
import java.util.List;
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
          stoStepImages.stream().filter(el -> el.getProduct().toString().equals(stepProductName)).findFirst();

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
    CIStepInfoType stepInfoType = step.getNonYamlInfo().getStepInfoType();
    StepImageConfig defaultImageConfig = ciExecutionConfigService.getPluginVersionForK8(stepInfoType, accountId);
    if (stepInfoType == CIStepInfoType.SECURITY) {
      return getSecurityStepImageConfig(step, ciExecutionConfigService, defaultImageConfig);
    }
    return defaultImageConfig;
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
}
