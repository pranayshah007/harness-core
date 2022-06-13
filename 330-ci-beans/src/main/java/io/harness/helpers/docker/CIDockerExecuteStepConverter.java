package io.harness.helpers.docker;

import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.RUN_STEP_KIND;

import io.harness.delegate.beans.ci.docker.CIDockerExecuteStepRequest;
import io.harness.delegate.beans.ci.docker.CIDockerExecuteTaskParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.delegate.task.citasks.cik8handler.ImageCredentials;
import io.harness.delegate.task.citasks.cik8handler.ImageSecretBuilder;
import io.harness.delegate.task.citasks.cik8handler.SecretSpecBuilder;
import io.harness.delegate.task.citasks.cik8handler.helper.SecretVolumesHelper;
import io.harness.delegate.task.citasks.vm.helper.CIVMConstants;
import io.harness.delegate.task.citasks.vm.helper.StepExecutionHelper;
import io.harness.k8s.model.ImageDetails;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class CIDockerExecuteStepConverter {
  @Inject private ImageSecretBuilder imageSecretBuilder;
  @Inject private SecretSpecBuilder secretSpecBuilder;
  @Inject private SecretVolumesHelper secretVolumesHelper;

  private static final String DOCKER_REGISTRY_ENV = "PLUGIN_REGISTRY";
  public static final String IMAGE_PATH_SPLIT_REGEX = ":";
  // Convert task params into a task request to be sent to the runner
  public CIDockerExecuteStepRequest convert(CIDockerExecuteTaskParams params) {
    CIDockerExecuteStepRequest.Config.ConfigBuilder configBuilder =
        CIDockerExecuteStepRequest.Config.builder()
            .id(params.getStepRuntimeId())
            .stageRuntimeID(params.getStageRuntimeId())
            .name(params.getStepId())
            .logKey(params.getLogKey())
            .workingDir(params.getWorkingDir())
            .volumeMounts(getVolumeMounts(params.getVolToMountPath(), params.getStageRuntimeId()));
    if (params.getStepInfo().getType() == VmStepInfo.Type.RUN) {
      VmRunStep runStep = (VmRunStep) params.getStepInfo();
      setRunConfig(runStep, configBuilder);
    } else if (params.getStepInfo().getType() == VmStepInfo.Type.PLUGIN) {
      VmPluginStep pluginStep = (VmPluginStep) params.getStepInfo();
      setPluginConfig(pluginStep, configBuilder);
    }
    if (isNotEmpty(params.getSecrets())) {
      params.getSecrets().forEach(secret -> configBuilder.secret(secret));
    }
    return CIDockerExecuteStepRequest.builder()
        .correlationID("manualTaskId") // TODO: fix
        .config(configBuilder.build())
        .build();
  }

  public List<CIDockerExecuteStepRequest.VolumeMount> getVolumeMounts(
      Map<String, String> volToMountPath, String stageRuntimeId) {
    List<CIDockerExecuteStepRequest.VolumeMount> volumeMounts = new ArrayList<>();
    if (isEmpty(volToMountPath)) {
      return volumeMounts;
    }
    for (Map.Entry<String, String> entry : volToMountPath.entrySet()) {
      String convertedId = stageRuntimeId.replace("-", "");
      convertedId = convertedId.replace("_", "");
      String key = secretVolumesHelper.getSecretKey(convertedId, entry.getValue());
      volumeMounts.add(CIDockerExecuteStepRequest.VolumeMount.builder().name(key).path(entry.getValue()).build());
    }
    return volumeMounts;
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

  public CIDockerExecuteStepRequest.ImageAuth getImageAuth(String image, ConnectorDetails imageConnector) {
    if (!StringUtils.isEmpty(image)) {
      ImageDetails imageInfo = getImageInfo(image);
      ImageDetailsWithConnector.builder().imageDetails(imageInfo).imageConnectorDetails(imageConnector).build();
      ImageCredentials imageCredentials = imageSecretBuilder.getImageCredentials(
          ImageDetailsWithConnector.builder().imageConnectorDetails(imageConnector).imageDetails(imageInfo).build());
      if (imageCredentials != null) {
        return CIDockerExecuteStepRequest.ImageAuth.builder()
            .address(imageCredentials.getRegistryUrl())
            .password(imageCredentials.getPassword())
            .username(imageCredentials.getUserName())
            .build();
      }
    }
    return null;
  }

  private void setRunConfig(VmRunStep runStep, CIDockerExecuteStepRequest.Config.ConfigBuilder configBuilder) {
    List<String> secrets = new ArrayList<>();
    CIDockerExecuteStepRequest.ImageAuth imageAuth = getImageAuth(runStep.getImage(), runStep.getImageConnector());
    if (imageAuth != null) {
      configBuilder.imageAuth(imageAuth);
      secrets.add(imageAuth.getPassword());
    }
    configBuilder.kind(RUN_STEP_KIND)
        .runConfig(CIDockerExecuteStepRequest.RunConfig.builder()
                       .command(Collections.singletonList(runStep.getCommand()))
                       .entrypoint(runStep.getEntrypoint())
                       .build())
        .image(runStep.getImage())
        .pull(runStep.getPullPolicy())
        .user(runStep.getRunAsUser())
        .envs(runStep.getEnvVariables())
        .privileged(runStep.isPrivileged())
        .outputVars(runStep.getOutputVariables())
        //            .testReport(convertTestReport(runStep.getUnitTestReport())) // TODO (Vistaar): Add test reports
        .secrets(secrets)
        .timeout(runStep.getTimeoutSecs());
  }

  private void setPluginConfig(VmPluginStep pluginStep, CIDockerExecuteStepRequest.Config.ConfigBuilder configBuilder) {
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

    CIDockerExecuteStepRequest.ImageAuth imageAuth =
        getImageAuth(pluginStep.getImage(), pluginStep.getImageConnector());
    if (imageAuth != null) {
      configBuilder.imageAuth(imageAuth);
      secrets.add(imageAuth.getPassword());
    }
    configBuilder.kind(RUN_STEP_KIND)
        .runConfig(CIDockerExecuteStepRequest.RunConfig.builder().build())
        .image(pluginStep.getImage())
        .pull(pluginStep.getPullPolicy())
        .user(pluginStep.getRunAsUser())
        .secrets(secrets)
        .envs(pluginStep.getEnvVariables())
        .privileged(pluginStep.isPrivileged())
        //            .testReport(convertTestReport(pluginStep.getUnitTestReport())) // TODO (Vistaar): Add test reports
        .timeout(pluginStep.getTimeoutSecs());
  }
}
