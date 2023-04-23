/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.vm;

import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.vm.CIVMConstants.JUNIT_REPORT_KIND;
import static io.harness.vm.CIVMConstants.RUNTEST_STEP_KIND;
import static io.harness.vm.CIVMConstants.RUN_STEP_KIND;

import static org.apache.commons.lang3.CharUtils.isAsciiAlphanumeric;

import io.harness.connector.ImageCredentials;
import io.harness.connector.ImageSecretBuilder;
import io.harness.connector.SecretSpecBuilder;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.ci.vm.CIVmExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest.Config;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest.Config.ConfigBuilder;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest.ExecuteStepRequestBuilder;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest.ImageAuth;
import io.harness.delegate.beans.ci.vm.steps.VmBackgroundStep;
import io.harness.delegate.beans.ci.vm.steps.VmJunitTestReport;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunTestStep;
import io.harness.delegate.beans.ci.vm.steps.VmServiceDependency;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.delegate.beans.ci.vm.steps.VmUnitTestReport;
import io.harness.k8s.model.ImageDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class VmExecuteStepUtils {
  @Inject private SecretSpecBuilder secretSpecBuilder;
  @Inject private ImageSecretBuilder imageSecretBuilder;
  public static final String IMAGE_PATH_SPLIT_REGEX = ":";
  private static final String DOCKER_REGISTRY_ENV = "PLUGIN_REGISTRY";

  public ExecuteStepRequestBuilder convertStep(CIVmExecuteStepTaskParams params) {
    ConfigBuilder configBuilder = Config.builder()
                                      .id(getIdentifier(params.getStepRuntimeId()))
                                      .name(params.getStepId())
                                      .logKey(params.getLogKey())
                                      .workingDir(params.getWorkingDir())
                                      .volumeMounts(getVolumeMounts(params.getVolToMountPath()));

    List<String> secrets = new ArrayList<>();
    if (params.getStepInfo().getType() == VmStepInfo.Type.RUN) {
      VmRunStep runStep = (VmRunStep) params.getStepInfo();
      secrets = setRunConfig(runStep, configBuilder);
    } else if (params.getStepInfo().getType() == VmStepInfo.Type.PLUGIN) {
      VmPluginStep pluginStep = (VmPluginStep) params.getStepInfo();
      secrets = setPluginConfig(pluginStep, configBuilder);
    } else if (params.getStepInfo().getType() == VmStepInfo.Type.RUN_TEST) {
      VmRunTestStep runTestStep = (VmRunTestStep) params.getStepInfo();
      secrets = setRunTestConfig(runTestStep, configBuilder);
    } else if (params.getStepInfo().getType() == VmStepInfo.Type.BACKGROUND) {
      VmBackgroundStep vmBackgroundStep = (VmBackgroundStep) params.getStepInfo();
      secrets = setBackgroundConfig(vmBackgroundStep, configBuilder);
    }

    if (isNotEmpty(params.getSecrets())) {
      secrets.addAll(params.getSecrets());
    }
    configBuilder.secrets(secrets);

    return ExecuteStepRequest.builder()
        .stageRuntimeID(params.getStageRuntimeId())
        .poolId(params.getPoolId())
        .ipAddress(params.getIpAddress())
        .config(configBuilder.build())
        .infraType(params.getInfraInfo().toString());
  }

  public ExecuteStepRequestBuilder convertService(
      VmServiceDependency params, CIVmInitializeTaskParams initializeTaskParams) {
    String id = params.getIdentifier();
    ConfigBuilder configBuilder = Config.builder()
                                      .id(id)
                                      .name(id)
                                      .logKey(params.getLogKey())
                                      .workingDir(initializeTaskParams.getWorkingDir())
                                      .volumeMounts(getVolumeMounts(initializeTaskParams.getVolToMountPath()))
                                      .image(params.getImage())
                                      .pull(params.getPullPolicy())
                                      .user(params.getRunAsUser())
                                      .envs(params.getEnvVariables())
                                      .detach(true)
                                      .kind(RUN_STEP_KIND);
    ImageAuth imageAuth = getImageAuth(params.getImage(), params.getImageConnector());

    List<String> secrets = new ArrayList<>();
    if (isNotEmpty(params.getSecrets())) {
      secrets.addAll(params.getSecrets());
    }
    if (imageAuth != null) {
      configBuilder.imageAuth(imageAuth);
      secrets.add(imageAuth.getPassword());
    }
    configBuilder.secrets(secrets);

    if (isNotEmpty(params.getPortBindings())) {
      configBuilder.portBindings(params.getPortBindings());
    }

    return ExecuteStepRequest.builder()
        .poolId(initializeTaskParams.getPoolID())
        .config(configBuilder.build())
        .stageRuntimeID(initializeTaskParams.getStageRuntimeId())
        .infraType(initializeTaskParams.getInfraInfo().toString());
  }

  private List<String> setRunConfig(VmRunStep runStep, ConfigBuilder configBuilder) {
    Map<String, String> envs = new HashMap<>();
    List<String> secrets = new ArrayList<>();
    if (isNotEmpty(runStep.getEnvVariables())) {
      envs = runStep.getEnvVariables();
    }

    ImageAuth imageAuth = getImageAuth(runStep.getImage(), runStep.getImageConnector());
    if (imageAuth != null) {
      configBuilder.imageAuth(imageAuth);
      secrets.add(imageAuth.getPassword());
    }

    if (runStep.getConnector() != null) {
      Map<String, SecretParams> secretVars = secretSpecBuilder.decryptConnectorSecret(runStep.getConnector());
      for (Map.Entry<String, SecretParams> entry : secretVars.entrySet()) {
        String secret = new String(decodeBase64(entry.getValue().getValue()));
        String key = entry.getKey();

        // Drone docker plugin does not work with v2 registry
        if (key.equals(DOCKER_REGISTRY_ENV) && secret.equals(CIVMConstants.DOCKER_REGISTRY_V2)) {
          secret = CIVMConstants.DOCKER_REGISTRY_V1;
        }
        envs.put(entry.getKey(), secret);
        secrets.add(secret);
      }
    }

    configBuilder.kind(RUN_STEP_KIND)
        .runConfig(ExecuteStepRequest.RunConfig.builder()
                       .command(Arrays.asList(runStep.getCommand()))
                       .entrypoint(runStep.getEntrypoint())
                       .build())
        .image(runStep.getImage())
        .pull(runStep.getPullPolicy())
        .user(runStep.getRunAsUser())
        .envs(envs)
        .privileged(runStep.isPrivileged())
        .outputVars(runStep.getOutputVariables())
        .testReport(convertTestReport(runStep.getUnitTestReport()))
        .timeout(runStep.getTimeoutSecs());
    return secrets;
  }

  private List<String> setBackgroundConfig(VmBackgroundStep vmBackgroundStep, ConfigBuilder configBuilder) {
    List<String> secrets = new ArrayList<>();
    ImageAuth imageAuth = getImageAuth(vmBackgroundStep.getImage(), vmBackgroundStep.getImageConnector());

    if (imageAuth != null) {
      configBuilder.imageAuth(imageAuth);
      secrets.add(imageAuth.getPassword());
    }

    List<String> command = null;
    if (!isEmpty(vmBackgroundStep.getCommand())) {
      command = Arrays.asList(vmBackgroundStep.getCommand());
    }

    configBuilder.kind(RUN_STEP_KIND)
        .runConfig(ExecuteStepRequest.RunConfig.builder()
                       .command(command)
                       .entrypoint(vmBackgroundStep.getEntrypoint())
                       .build())
        .image(vmBackgroundStep.getImage())
        .pull(vmBackgroundStep.getPullPolicy())
        .user(vmBackgroundStep.getRunAsUser())
        .envs(vmBackgroundStep.getEnvVariables())
        .privileged(vmBackgroundStep.isPrivileged())
        .testReport(convertTestReport(vmBackgroundStep.getUnitTestReport()))
        .detach(true);

    if (isNotEmpty(vmBackgroundStep.getPortBindings())) {
      configBuilder.portBindings(vmBackgroundStep.getPortBindings());
    }

    return secrets;
  }

  private List<String> setPluginConfig(VmPluginStep pluginStep, ExecuteStepRequest.Config.ConfigBuilder configBuilder) {
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

    ExecuteStepRequest.ImageAuth imageAuth = getImageAuth(pluginStep.getImage(), pluginStep.getImageConnector());
    if (imageAuth != null) {
      configBuilder.imageAuth(imageAuth);
      secrets.add(imageAuth.getPassword());
    }
    configBuilder.kind(RUN_STEP_KIND)
        .runConfig(ExecuteStepRequest.RunConfig.builder().build())
        .image(pluginStep.getImage())
        .pull(pluginStep.getPullPolicy())
        .user(pluginStep.getRunAsUser())
        .envs(pluginStep.getEnvVariables())
        .privileged(pluginStep.isPrivileged())
        .testReport(convertTestReport(pluginStep.getUnitTestReport()))
        .timeout(pluginStep.getTimeoutSecs());
    return secrets;
  }

  private List<String> setRunTestConfig(
      VmRunTestStep runTestStep, ExecuteStepRequest.Config.ConfigBuilder configBuilder) {
    List<String> secrets = new ArrayList<>();
    ExecuteStepRequest.ImageAuth imageAuth = getImageAuth(runTestStep.getImage(), runTestStep.getConnector());
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
                           .parallelizeTests(runTestStep.isParallelizeTests())
                           .testSplitStrategy(runTestStep.getTestSplitStrategy())
                           .testGlobs(runTestStep.getTestGlobs())
                           .build())
        .image(runTestStep.getImage())
        .pull(runTestStep.getPullPolicy())
        .user(runTestStep.getRunAsUser())
        .envs(runTestStep.getEnvVariables())
        .privileged(runTestStep.isPrivileged())
        .outputVars(runTestStep.getOutputVariables())
        .testReport(convertTestReport(runTestStep.getUnitTestReport()))
        .timeout(runTestStep.getTimeoutSecs());
    return secrets;
  }

  private ExecuteStepRequest.TestReport convertTestReport(VmUnitTestReport unitTestReport) {
    if (unitTestReport == null) {
      return null;
    }

    if (unitTestReport.getType() != VmUnitTestReport.Type.JUNIT) {
      return null;
    }

    VmJunitTestReport junitTestReport = (VmJunitTestReport) unitTestReport;
    return ExecuteStepRequest.TestReport.builder()
        .kind(JUNIT_REPORT_KIND)
        .junitReport(ExecuteStepRequest.JunitReport.builder().paths(junitTestReport.getPaths()).build())
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

  public ImageAuth getImageAuth(String image, ConnectorDetails imageConnector) {
    if (!StringUtils.isEmpty(image)) {
      ImageDetails imageInfo = getImageInfo(image);
      ImageDetailsWithConnector.builder().imageDetails(imageInfo).imageConnectorDetails(imageConnector).build();
      ImageCredentials imageCredentials = imageSecretBuilder.getImageCredentials(
          ImageDetailsWithConnector.builder().imageConnectorDetails(imageConnector).imageDetails(imageInfo).build());
      if (imageCredentials != null) {
        return ImageAuth.builder()
            .address(imageCredentials.getRegistryUrl())
            .password(imageCredentials.getPassword())
            .username(imageCredentials.getUserName())
            .build();
      }
    }
    return null;
  }

  private ImageDetails getImageInfo(String image) {
    String tag = "";
    String name = image;

    if (image.contains(IMAGE_PATH_SPLIT_REGEX)) {
      String[] subTokens = image.split(IMAGE_PATH_SPLIT_REGEX);
      if (subTokens.length > 1) {
        tag = subTokens[subTokens.length - 1];
        String[] nameparts = Arrays.copyOf(subTokens, subTokens.length - 1);
        name = String.join(IMAGE_PATH_SPLIT_REGEX, nameparts);
      }
    }

    return ImageDetails.builder().name(name).tag(tag).build();
  }

  public List<ExecuteStepRequest.VolumeMount> getVolumeMounts(Map<String, String> volToMountPath) {
    List<ExecuteStepRequest.VolumeMount> volumeMounts = new ArrayList<>();
    if (isEmpty(volToMountPath)) {
      return volumeMounts;
    }

    for (Map.Entry<String, String> entry : volToMountPath.entrySet()) {
      volumeMounts.add(ExecuteStepRequest.VolumeMount.builder().name(entry.getKey()).path(entry.getValue()).build());
    }
    return volumeMounts;
  }
}
