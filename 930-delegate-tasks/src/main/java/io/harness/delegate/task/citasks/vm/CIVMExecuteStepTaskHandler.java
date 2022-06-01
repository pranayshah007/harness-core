/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.vm;

import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.RUNTEST_STEP_KIND;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.RUN_STEP_KIND;

import static org.apache.commons.lang3.CharUtils.isAsciiAlphanumeric;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.CIExecuteStepTaskParams;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.ci.vm.CIVmExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest.Config.ConfigBuilder;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest.ImageAuth;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest.JunitReport;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest.TestReport;
import io.harness.delegate.beans.ci.vm.steps.VmJunitTestReport;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunTestStep;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.delegate.beans.ci.vm.steps.VmUnitTestReport;
import io.harness.delegate.task.citasks.CIExecuteStepTaskHandler;
import io.harness.delegate.task.citasks.cik8handler.SecretSpecBuilder;
import io.harness.delegate.task.citasks.vm.helper.CIVMConstants;
import io.harness.delegate.task.citasks.vm.helper.StepExecutionHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIVMExecuteStepTaskHandler implements CIExecuteStepTaskHandler {
  @Inject private SecretSpecBuilder secretSpecBuilder;
  @NotNull private Type type = Type.VM;
  @Inject private StepExecutionHelper stepExecutionHelper;

  private static final String DOCKER_REGISTRY_ENV = "PLUGIN_REGISTRY";

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public VmTaskExecutionResponse executeTaskInternal(CIExecuteStepTaskParams ciExecuteStepTaskParams, String taskId) {
    CIVmExecuteStepTaskParams ciVmExecuteStepTaskParams = (CIVmExecuteStepTaskParams) ciExecuteStepTaskParams;
    log.info(
        "Received request to execute step with stage runtime ID {}", ciVmExecuteStepTaskParams.getStageRuntimeId());
    return stepExecutionHelper.callRunnerForStepExecution(convert(ciVmExecuteStepTaskParams, taskId));
  }

  private ExecuteStepRequest convert(CIVmExecuteStepTaskParams params, String taskId) {
    ConfigBuilder configBuilder = ExecuteStepRequest.Config.builder()
                                      .id(getIdentifier(params.getStepRuntimeId()))
                                      .name(params.getStepId())
                                      .logKey(params.getLogKey())
                                      .workingDir(params.getWorkingDir())
                                      .volumeMounts(stepExecutionHelper.getVolumeMounts(params.getVolToMountPath()));
    if (params.getStepInfo().getType() == VmStepInfo.Type.RUN) {
      VmRunStep runStep = (VmRunStep) params.getStepInfo();
      setRunConfig(runStep, configBuilder);
    } else if (params.getStepInfo().getType() == VmStepInfo.Type.PLUGIN) {
      VmPluginStep pluginStep = (VmPluginStep) params.getStepInfo();
      setPluginConfig(pluginStep, configBuilder);
    } else if (params.getStepInfo().getType() == VmStepInfo.Type.RUN_TEST) {
      VmRunTestStep runTestStep = (VmRunTestStep) params.getStepInfo();
      setRunTestConfig(runTestStep, configBuilder);
    }
    if (isNotEmpty(params.getSecrets())) {
      params.getSecrets().forEach(secret -> configBuilder.secret(secret));
    }
    return ExecuteStepRequest.builder()
        .correlationID(taskId)
        .poolId(params.getPoolId())
        .ipAddress(params.getIpAddress())
        .config(configBuilder.build())
        .build();
  }

  private void setRunConfig(VmRunStep runStep, ConfigBuilder configBuilder) {
    List<String> secrets = new ArrayList<>();
    ImageAuth imageAuth = stepExecutionHelper.getImageAuth(runStep.getImage(), runStep.getImageConnector());
    if (imageAuth != null) {
      configBuilder.imageAuth(imageAuth);
      secrets.add(imageAuth.getPassword());
    }
    configBuilder.kind(RUN_STEP_KIND)
        .runConfig(ExecuteStepRequest.RunConfig.builder()
                       .command(Collections.singletonList(runStep.getCommand()))
                       .entrypoint(runStep.getEntrypoint())
                       .build())
        .image(runStep.getImage())
        .pull(runStep.getPullPolicy())
        .user(runStep.getRunAsUser())
        .envs(runStep.getEnvVariables())
        .privileged(runStep.isPrivileged())
        .outputVars(runStep.getOutputVariables())
        .testReport(convertTestReport(runStep.getUnitTestReport()))
        .secrets(secrets)
        .timeout(runStep.getTimeoutSecs());
  }

  private void setPluginConfig(VmPluginStep pluginStep, ConfigBuilder configBuilder) {
    Map<String, String> env = new HashMap<>();
    List<String> secrets = new ArrayList<>();
    if (isNotEmpty(pluginStep.getEnvVariables())) {
      env = pluginStep.getEnvVariables();
    }

    if (pluginStep.getConnector() != null) {
      Map<String, SecretParams> secretVars = secretSpecBuilder.decryptConnectorSecret(pluginStep.getConnector());
      for (Map.Entry<String, SecretParams> entry : secretVars.entrySet()) {
        String secret = new String(decodeBase64(entry.getValue().getValue()));
        String key = entry.getKey();

        // Drone docker plugin does not work with v2 registry
        if (key.equals(DOCKER_REGISTRY_ENV) && secret.equals(CIVMConstants.DOCKER_REGISTRY_V2)) {
          secret = CIVMConstants.DOCKER_REGISTRY_V1;
        }
        env.put(entry.getKey(), secret);
        secrets.add(secret);
      }
    }

    ImageAuth imageAuth = stepExecutionHelper.getImageAuth(pluginStep.getImage(), pluginStep.getImageConnector());
    if (imageAuth != null) {
      configBuilder.imageAuth(imageAuth);
      secrets.add(imageAuth.getPassword());
    }
    configBuilder.kind(RUN_STEP_KIND)
        .runConfig(ExecuteStepRequest.RunConfig.builder().build())
        .image(pluginStep.getImage())
        .pull(pluginStep.getPullPolicy())
        .user(pluginStep.getRunAsUser())
        .secrets(secrets)
        .envs(pluginStep.getEnvVariables())
        .privileged(pluginStep.isPrivileged())
        .testReport(convertTestReport(pluginStep.getUnitTestReport()))
        .timeout(pluginStep.getTimeoutSecs());
  }

  private void setRunTestConfig(VmRunTestStep runTestStep, ConfigBuilder configBuilder) {
    List<String> secrets = new ArrayList<>();
    ImageAuth imageAuth = stepExecutionHelper.getImageAuth(runTestStep.getImage(), runTestStep.getConnector());
    if (imageAuth != null) {
      secrets.add(imageAuth.getPassword());
      configBuilder.imageAuth(imageAuth);
    }
    configBuilder.kind(RUNTEST_STEP_KIND)
        .runTestConfig(ExecuteStepRequest.RunTestConfig.builder()
                           .args(runTestStep.getArgs())
                           .entrypoint(runTestStep.getEntrypoint())
                           .preCommand(runTestStep.getPreCommand())
                           .postCommand(runTestStep.getPostCommand())
                           .buildTool(runTestStep.getBuildTool().toLowerCase())
                           .language(runTestStep.getLanguage().toLowerCase())
                           .packages(runTestStep.getPackages())
                           .namespaces(runTestStep.getNamespaces())
                           .runOnlySelectedTests(runTestStep.isRunOnlySelectedTests())
                           .testAnnotations(runTestStep.getTestAnnotations())
                           .buildEnvironment(runTestStep.getBuildEnvironment())
                           .frameworkVersion(runTestStep.getFrameworkVersion())
                           .build())
        .image(runTestStep.getImage())
        .pull(runTestStep.getPullPolicy())
        .user(runTestStep.getRunAsUser())
        .envs(runTestStep.getEnvVariables())
        .privileged(runTestStep.isPrivileged())
        .outputVars(runTestStep.getOutputVariables())
        .testReport(convertTestReport(runTestStep.getUnitTestReport()))
        .secrets(secrets)
        .timeout(runTestStep.getTimeoutSecs());
  }

  private TestReport convertTestReport(VmUnitTestReport unitTestReport) {
    if (unitTestReport == null) {
      return null;
    }

    if (unitTestReport.getType() != VmUnitTestReport.Type.JUNIT) {
      return null;
    }

    VmJunitTestReport junitTestReport = (VmJunitTestReport) unitTestReport;
    return TestReport.builder()
        .kind("Junit")
        .junitReport(JunitReport.builder().paths(junitTestReport.getPaths()).build())
        .build();
  }

  public String getIdentifier(String identifier) {
    StringBuilder sb = new StringBuilder(15);
    for (char c : identifier.toCharArray()) {
      if (isAsciiAlphanumeric(c)) {
        sb.append(c);
      }
      if (sb.length() == 15) {
        return sb.toString();
      }
    }
    return sb.toString();
  }
}
