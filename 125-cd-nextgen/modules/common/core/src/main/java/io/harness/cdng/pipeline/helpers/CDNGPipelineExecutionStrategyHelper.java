/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.pipeline.helpers.ExecutionStrategyTemplates.SSH_WINRM_BASIC_SH_FTL;
import static io.harness.cdng.pipeline.helpers.ExecutionStrategyTemplates.SSH_WINRM_CANARY_SH_FTL;
import static io.harness.cdng.pipeline.helpers.ExecutionStrategyTemplates.SSH_WINRM_ROLLING_SH_FTL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.NGInstanceUnitType;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.GeneralException;
import io.harness.steps.matrix.StrategyParameters;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@OwnedBy(CDP)
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
  private static String setupRuntimePathsScriptPS;

  // WinRm Canary
  private static String extendArtifactCanaryScriptPS;
  private static String createWebsiteCanaryScriptPS;
  private static String createAppPoolCanaryScriptPS;
  private static String createVirtualDirectoryCanaryScriptPS;

  // WinRm Rolling
  private static String setupRuntimePathsScriptRollingPS;

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
    if (isEmpty(setupRuntimePathsScriptPS)) {
      setupRuntimePathsScriptPS =
          Resources.toString(Objects.requireNonNull(classLoader.getResource(
                                 "snippets/Pipelines/execution/ssh/script/setup-runtime-paths-script-powershell.yaml")),
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

    if (isEmpty(setupRuntimePathsScriptRollingPS)) {
      setupRuntimePathsScriptRollingPS = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              "snippets/Pipelines/execution/ssh/script/setup-runtime-paths-script-rolling-powershell.yaml")),
          StandardCharsets.UTF_8);
    }
  }

  @VisibleForTesting
  protected String generateSshWinRmCanaryYaml(ServiceDefinitionType serviceDefinitionType,
      StrategyParameters strategyParameters, boolean includeVerify) throws IOException {
    StrategyValidator.validateStrategyParametersForCanary(strategyParameters);
    try (StringWriter stringWriter = new StringWriter()) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      String artifactType = ExecutionStrategyUtils.resolveArtifactTypeSuffix(serviceDefinitionType, strategyParameters);
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
              .put("setupRuntimePathsScriptPS", setupRuntimePathsScriptPS)
              .build();
      try {
        ExecutionStrategyTemplates.getTemplate(SSH_WINRM_CANARY_SH_FTL).process(templateParams, stringWriter);
      } catch (TemplateException te) {
        throw new GeneralException("Error processing yaml template: " + te.getMessage());
      }
      return stringWriter.toString().trim();
    }
  }

  @VisibleForTesting
  protected String generateSshWinRmRollingYaml(ServiceDefinitionType serviceDefinitionType,
      StrategyParameters strategyParameters, boolean includeVerify) throws IOException {
    StrategyValidator.validateStrategyParametersForRolling(strategyParameters);
    try (StringWriter stringWriter = new StringWriter()) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      String artifactType = ExecutionStrategyUtils.resolveArtifactTypeSuffix(serviceDefinitionType, strategyParameters);
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
              .put("setupRuntimePathsScriptRollingPS", setupRuntimePathsScriptRollingPS)
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
    StrategyValidator.validateStrategyParametersForBasic(strategyParameters);
    try (StringWriter stringWriter = new StringWriter()) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      String artifactType = ExecutionStrategyUtils.resolveArtifactTypeSuffix(serviceDefinitionType, strategyParameters);
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
                                               .put("setupRuntimePathsScriptPS", setupRuntimePathsScriptPS)
                                               .build();
      try {
        ExecutionStrategyTemplates.getTemplate(SSH_WINRM_BASIC_SH_FTL).process(templateParams, stringWriter);
      } catch (TemplateException te) {
        throw new GeneralException("Error processing yaml template: " + te.getMessage());
      }
      return stringWriter.toString().trim();
    }
  }
}
