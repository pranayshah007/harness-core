/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.helpers;

import static io.harness.cdng.pipeline.helpers.ExecutionStrategyTemplates.SSH_WINRM_BASIC_SH_FTL;
import static io.harness.cdng.pipeline.helpers.ExecutionStrategyTemplates.SSH_WINRM_CANARY_SH_FTL;
import static io.harness.cdng.pipeline.helpers.ExecutionStrategyTemplates.SSH_WINRM_ROLLING_SH_FTL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.beans.NGInstanceUnitType;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.steps.matrix.StrategyParameters;

import software.wings.utils.ArtifactType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class CDNGPipelineExecutionStrategyHelper {
  private static String failureStrategiesSnippet;
  private static String setupRuntimePathsScript;
  private static String setupRuntimePathsScriptWar;
  private static String processStopScript;
  private static String portClearedScript;
  private static String processRunScript;
  private static String portListeningScript;
  // canary
  private static String setupRuntimePathsCanaryScript;
  private static String setupRuntimePathsWarCanaryScript;
  private static String processStopCanaryScript;
  private static String portClearedCanaryScript;
  private static String processRunCanaryScript;
  private static String portListeningCanaryScript;

  // rolling
  private static String setupRuntimePathsRollingScript;
  private static String setupRuntimePathsWarRollingScript;
  private static String processStopRollingScript;
  private static String portClearedRollingScript;
  private static String processRunRollingScript;
  private static String portListeningRollingScript;

  // WinRm Basic
  private static String extendArtifactScriptPS;
  private static String createWebsiteScriptPS;
  private static String createAppPoolScriptPS;
  private static String createVirtualDirectoryScriptPS;

  // WinRm Canary
  private static String extendArtifactCanaryScriptPS;
  private static String createWebsiteCanaryScriptPS;
  private static String createAppPoolCanaryScriptPS;
  private static String createVirtualDirectoryCanaryScriptPS;

  static {
    try {
      loadStaticScriptSnippets(Thread.currentThread().getContextClassLoader());
    } catch (IOException e) {
      throw new GeneralException("Error initializing Execution Strategy snippets");
    }
  }

  private static void loadStaticScriptSnippets(ClassLoader classLoader) throws IOException {
    if (isEmpty(failureStrategiesSnippet)) {
      failureStrategiesSnippet = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/default-failure-strategies.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(setupRuntimePathsScript)) {
      setupRuntimePathsScript = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/setup-runtime-paths-script-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(setupRuntimePathsScriptWar)) {
      setupRuntimePathsScriptWar =
          Resources.toString(Objects.requireNonNull(classLoader.getResource(
                                 "snippets/Pipelines/execution/ssh/script/setup-runtime-paths-script-war-bash.yaml")),
              StandardCharsets.UTF_8);
    }
    if (isEmpty(processStopScript)) {
      processStopScript = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/process-stop-script-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(portClearedScript)) {
      portClearedScript = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/port-cleared-script-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(processRunScript)) {
      processRunScript = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/process-run-script-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(portListeningScript)) {
      portListeningScript = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/port-listening-script-bash.yaml")),
          StandardCharsets.UTF_8);
    }

    loadCanaryStaticScriptSnippets(classLoader);
    loadRollingStaticScriptSnippets(classLoader);
  }

  private static void loadCanaryStaticScriptSnippets(ClassLoader classLoader) throws IOException {
    if (isEmpty(setupRuntimePathsCanaryScript)) {
      setupRuntimePathsCanaryScript = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              "snippets/Pipelines/execution/ssh/script/setup-runtime-paths-script-canary-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(setupRuntimePathsWarCanaryScript)) {
      setupRuntimePathsWarCanaryScript = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              "snippets/Pipelines/execution/ssh/script/setup-runtime-paths-script-canary-war-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(processStopCanaryScript)) {
      processStopCanaryScript = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/process-stop-script-canary-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(portClearedCanaryScript)) {
      portClearedCanaryScript = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/port-cleared-script-canary-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(processRunCanaryScript)) {
      processRunCanaryScript = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/process-run-script-canary-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(portListeningCanaryScript)) {
      portListeningCanaryScript =
          Resources.toString(Objects.requireNonNull(classLoader.getResource(
                                 "snippets/Pipelines/execution/ssh/script/port-listening-script-canary-bash.yaml")),
              StandardCharsets.UTF_8);
    }
  }

  private static void loadRollingStaticScriptSnippets(ClassLoader classLoader) throws IOException {
    loadRollingStaticBashScriptSnippets(classLoader);
    loadRollingStaticPowerShellScriptSnippets(classLoader);
  }

  private static void loadRollingStaticBashScriptSnippets(ClassLoader classLoader) throws IOException {
    if (isEmpty(setupRuntimePathsRollingScript)) {
      setupRuntimePathsRollingScript = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              "snippets/Pipelines/execution/ssh/script/setup-runtime-paths-script-rolling-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(setupRuntimePathsWarRollingScript)) {
      setupRuntimePathsWarRollingScript = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              "snippets/Pipelines/execution/ssh/script/setup-runtime-paths-war-script-rolling-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(processStopRollingScript)) {
      processStopRollingScript = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/process-stop-script-rolling-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(portClearedRollingScript)) {
      portClearedRollingScript = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/port-cleared-script-rolling-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(processRunRollingScript)) {
      processRunRollingScript = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/process-run-script-rolling-bash.yaml")),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(portListeningRollingScript)) {
      portListeningRollingScript =
          Resources.toString(Objects.requireNonNull(classLoader.getResource(
                                 "snippets/Pipelines/execution/ssh/script/port-listening-script-rolling-bash.yaml")),
              StandardCharsets.UTF_8);
    }
  }

  private static void loadRollingStaticPowerShellScriptSnippets(ClassLoader classLoader) throws IOException {
    if (isEmpty(extendArtifactScriptPS)) {
      extendArtifactScriptPS =
          Resources.toString(Objects.requireNonNull(classLoader.getResource(
                                 "snippets/Pipelines/execution/ssh/script/extend-artifact-script-powershell.yaml")),
              StandardCharsets.UTF_8);
    }

    if (isEmpty(createWebsiteScriptPS)) {
      createWebsiteScriptPS = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/create-website-script-powershell.yaml")),
          StandardCharsets.UTF_8);
    }

    if (isEmpty(createAppPoolScriptPS)) {
      createAppPoolScriptPS = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/ssh/script/create-apppool-script-powershell.yaml")),
          StandardCharsets.UTF_8);
    }

    if (isEmpty(createVirtualDirectoryScriptPS)) {
      createVirtualDirectoryScriptPS = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              "snippets/Pipelines/execution/ssh/script/create-virtual-directory-script-powershell.yaml")),
          StandardCharsets.UTF_8);
    }

    if (isEmpty(extendArtifactCanaryScriptPS)) {
      extendArtifactCanaryScriptPS = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              "snippets/Pipelines/execution/ssh/script/extend-artifact-canary-script-powershell.yaml")),
          StandardCharsets.UTF_8);
    }

    if (isEmpty(createWebsiteCanaryScriptPS)) {
      createWebsiteCanaryScriptPS = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              "snippets/Pipelines/execution/ssh/script/create-website-canary-script-powershell.yaml")),
          StandardCharsets.UTF_8);
    }

    if (isEmpty(createAppPoolCanaryScriptPS)) {
      createAppPoolCanaryScriptPS = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              "snippets/Pipelines/execution/ssh/script/create-apppool-canary-script-powershell.yaml")),
          StandardCharsets.UTF_8);
    }

    if (isEmpty(createVirtualDirectoryCanaryScriptPS)) {
      createVirtualDirectoryCanaryScriptPS = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              "snippets/Pipelines/execution/ssh/script/create-virtual-directory-canary-script-powershell.yaml")),
          StandardCharsets.UTF_8);
    }
  }

  private void validateStrategyParametersForCanary(StrategyParameters strategyParameters) {
    if (null == strategyParameters.getPhases() || isEmpty(strategyParameters.getPhases())) {
      throw new GeneralException("phases need to be defined, e.g. phases : [10, 50, 100]");
    }
    List<Integer> sortedPhases = Arrays.stream(strategyParameters.getPhases()).sorted().collect(Collectors.toList());
    if (sortedPhases.get(0) <= 0) {
      throw new GeneralException("phases need to be positive");
    }
    if (!sortedPhases.equals(Arrays.asList(strategyParameters.getPhases()))) {
      throw new GeneralException("phases need to be in asc order");
    }
    if (sortedPhases.stream().filter(i -> i > 100).findAny().isPresent()
        && NGInstanceUnitType.PERCENTAGE.equals(strategyParameters.getUnitType())) {
      throw new GeneralException("phase can not be greater than 100");
    }
    if (sortedPhases.stream().distinct().count() != sortedPhases.size()) {
      throw new GeneralException("phase values should be unique");
    }
    if (null == strategyParameters.getUnitType()) {
      throw new GeneralException("unitType needs to be defined, one of <COUNT | PERCENTAGE>");
    }
    if (null == strategyParameters.getArtifactType()) {
      throw new GeneralException("artifactType needs to be defined, e.g. WAR");
    }
  }

  @VisibleForTesting
  protected String generateSshWinRmCanaryYaml(ServiceDefinitionType serviceDefinitionType,
      StrategyParameters strategyParameters, boolean includeVerify) throws IOException {
    validateStrategyParametersForCanary(strategyParameters);
    try (StringWriter stringWriter = new StringWriter()) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      String artifactType = isSSHServiceDefinitionType(serviceDefinitionType)
          ? artifactSSHTypeSuffix(strategyParameters.getArtifactType())
          : artifactWinRmTypeSuffix(strategyParameters.getArtifactType());
      String canarySnippet = Resources.toString(Objects.requireNonNull(classLoader.getResource(format(
                                                    "snippets/Pipelines/execution/ssh/canary/%s-canary-%s%s.yaml",
                                                    serviceDefinitionType.name().toLowerCase(Locale.ROOT), artifactType,
                                                    includeVerify ? "-with-verify" : ""))),
          StandardCharsets.UTF_8);
      String canaryRollbackSnippet = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource(format("snippets/Pipelines/execution/ssh/canary/%s-canary-%s%s-rollback.yaml",
                  serviceDefinitionType.name().toLowerCase(Locale.ROOT), artifactType,
                  includeVerify ? "-with-verify" : ""))),
          StandardCharsets.UTF_8);
      Map<String, Object> templateParams =
          ImmutableMap.<String, Object>builder()
              .put("failureStrategies", failureStrategiesSnippet)
              .put("phases", strategyParameters.getPhases())
              .put("unitType",
                  NGInstanceUnitType.PERCENTAGE.equals(strategyParameters.getUnitType()) ? "Percentage" : "Count")
              .put("canarySnippet", canarySnippet)
              .put("canaryRollbackSnippet", canaryRollbackSnippet)
              .put("setupRuntimePathsScript", setupRuntimePathsCanaryScript)
              .put("setupRuntimePathsScriptWar", setupRuntimePathsWarCanaryScript)
              .put("processStopScript", processStopCanaryScript)
              .put("portClearedScript", portClearedCanaryScript)
              .put("processRunScript", processRunCanaryScript)
              .put("portListeningScript", portListeningCanaryScript)
              .put("extendArtifactScriptPS", extendArtifactCanaryScriptPS)
              .put("createAppPoolScriptPS", createAppPoolCanaryScriptPS)
              .put("createWebsiteScriptPS", createWebsiteCanaryScriptPS)
              .put("createVirtualDirectoryScriptPS", createVirtualDirectoryCanaryScriptPS)
              .build();
      try {
        ExecutionStrategyTemplates.getTemplate(SSH_WINRM_CANARY_SH_FTL).process(templateParams, stringWriter);
      } catch (TemplateException te) {
        throw new GeneralException("Error processing yaml template: " + te.getMessage());
      }
      return stringWriter.toString().trim();
    }
  }

  private void validateStrategyParametersForRolling(StrategyParameters strategyParameters) {
    if (null == strategyParameters.getInstances()) {
      throw new GeneralException("Number of instances needs to be defined, e.g. 10");
    }
    if (strategyParameters.getInstances() <= 0) {
      throw new GeneralException("Number of instances need to be positive");
    }
    if (strategyParameters.getInstances() > 100
        && NGInstanceUnitType.PERCENTAGE.equals(strategyParameters.getUnitType())) {
      throw new GeneralException("Number of instances need to be between 0 and 100");
    }
    if (null == strategyParameters.getArtifactType()) {
      throw new GeneralException("artifactType needs to be defined, e.g. WAR");
    }
  }

  private void validateStrategyParametersForBasic(StrategyParameters strategyParameters) {
    if (null == strategyParameters.getArtifactType()) {
      throw new GeneralException("artifactType needs to be defined, e.g. WAR");
    }
  }

  @VisibleForTesting
  protected String generateSshWinRmRollingYaml(ServiceDefinitionType serviceDefinitionType,
      StrategyParameters strategyParameters, boolean includeVerify) throws IOException {
    validateStrategyParametersForRolling(strategyParameters);
    try (StringWriter stringWriter = new StringWriter()) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      String artifactType = isSSHServiceDefinitionType(serviceDefinitionType)
          ? artifactSSHTypeSuffix(strategyParameters.getArtifactType())
          : artifactWinRmTypeSuffix(strategyParameters.getArtifactType());
      String rollingSnippet = Resources.toString(Objects.requireNonNull(classLoader.getResource(format(
                                                     "snippets/Pipelines/execution/ssh/rolling/%s-rolling-%s%s.yaml",
                                                     serviceDefinitionType.name().toLowerCase(Locale.ROOT),
                                                     artifactType, includeVerify ? "-with-verify" : ""))),
          StandardCharsets.UTF_8);
      Map<String, Object> templateParams =
          ImmutableMap.<String, Object>builder()
              .put("failureStrategies", failureStrategiesSnippet)
              .put("maxConcurrency", 1)
              .put("partitionSize", strategyParameters.getInstances())
              .put("rollingSnippet", rollingSnippet)
              .put("unitType",
                  NGInstanceUnitType.PERCENTAGE.equals(strategyParameters.getUnitType()) ? "Percentage" : "Count")
              .put("setupRuntimePathsScript", setupRuntimePathsRollingScript)
              .put("setupRuntimePathsScriptWar", setupRuntimePathsWarRollingScript)
              .put("processStopScript", processStopRollingScript)
              .put("portClearedScript", portClearedRollingScript)
              .put("processRunScript", processRunRollingScript)
              .put("portListeningScript", portListeningRollingScript)
              .put("extendArtifactScriptPS", extendArtifactCanaryScriptPS)
              .put("createAppPoolScriptPS", createAppPoolCanaryScriptPS)
              .put("createWebsiteScriptPS", createWebsiteCanaryScriptPS)
              .put("createVirtualDirectoryScriptPS", createVirtualDirectoryCanaryScriptPS)
              .build();
      try {
        ExecutionStrategyTemplates.getTemplate(SSH_WINRM_ROLLING_SH_FTL).process(templateParams, stringWriter);
      } catch (TemplateException te) {
        throw new GeneralException("Error processing yaml template: " + te.getMessage());
      }
      return stringWriter.toString().trim();
    }
  }

  @VisibleForTesting
  protected String generateSshWinRmBasicYaml(ServiceDefinitionType serviceDefinitionType,
      StrategyParameters strategyParameters, boolean includeVerify) throws IOException {
    validateStrategyParametersForBasic(strategyParameters);
    try (StringWriter stringWriter = new StringWriter()) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      String artifactType = isSSHServiceDefinitionType(serviceDefinitionType)
          ? artifactSSHTypeSuffix(strategyParameters.getArtifactType())
          : artifactWinRmTypeSuffix(strategyParameters.getArtifactType());
      String basicSnippet = Resources.toString(Objects.requireNonNull(classLoader.getResource(
                                                   format("snippets/Pipelines/execution/ssh/basic/%s-basic-%s%s.yaml",
                                                       serviceDefinitionType.name().toLowerCase(Locale.ROOT),
                                                       artifactType, includeVerify ? "-with-verify" : ""))),
          StandardCharsets.UTF_8);
      Map<String, Object> templateParams = ImmutableMap.<String, Object>builder()
                                               .put("failureStrategies", failureStrategiesSnippet)
                                               .put("basicSnippet", basicSnippet)
                                               .put("setupRuntimePathsScript", setupRuntimePathsScript)
                                               .put("setupRuntimePathsScriptWar", setupRuntimePathsScriptWar)
                                               .put("processStopScript", processStopScript)
                                               .put("portClearedScript", portClearedScript)
                                               .put("processRunScript", processRunScript)
                                               .put("portListeningScript", portListeningScript)
                                               .put("extendArtifactScriptPS", extendArtifactScriptPS)
                                               .put("createAppPoolScriptPS", createAppPoolScriptPS)
                                               .put("createWebsiteScriptPS", createWebsiteScriptPS)
                                               .put("createVirtualDirectoryScriptPS", createVirtualDirectoryScriptPS)
                                               .build();
      try {
        ExecutionStrategyTemplates.getTemplate(SSH_WINRM_BASIC_SH_FTL).process(templateParams, stringWriter);
      } catch (TemplateException te) {
        throw new GeneralException("Error processing yaml template: " + te.getMessage());
      }
      return stringWriter.toString().trim();
    }
  }

  private boolean isSSHServiceDefinitionType(ServiceDefinitionType serviceDefinitionType) {
    return ServiceDefinitionType.SSH.name().equals(serviceDefinitionType.name());
  }

  private String artifactSSHTypeSuffix(ArtifactType artifactType) {
    if (ArtifactType.OTHER.equals(artifactType)) {
      return ArtifactType.WAR.name().toLowerCase(Locale.ROOT);
    } else if (ArtifactType.RPM.equals(artifactType)) {
      return ArtifactType.JAR.name().toLowerCase(Locale.ROOT);
    } else if (ArtifactType.ZIP.equals(artifactType)) {
      return ArtifactType.TAR.name().toLowerCase(Locale.ROOT);
    } else {
      return artifactType.name().toLowerCase(Locale.ROOT);
    }
  }

  private String artifactWinRmTypeSuffix(ArtifactType artifactType) {
    if (ArtifactType.IIS_APP.equals(artifactType)) {
      return ArtifactType.IIS_APP.name().toLowerCase(Locale.ROOT);
    } else if (ArtifactType.IIS_VirtualDirectory.equals(artifactType)) {
      return ArtifactType.IIS_VirtualDirectory.name().toLowerCase(Locale.ROOT);
    } else if (ArtifactType.IIS.equals(artifactType)) {
      return ArtifactType.IIS.name().toLowerCase(Locale.ROOT);
    } else if (ArtifactType.OTHER.equals(artifactType)) {
      return ArtifactType.OTHER.name().toLowerCase(Locale.ROOT);
    }

    throw new InvalidArgumentsException(format("Unsupported artifact type found: %s", artifactType.name()));
  }
}
