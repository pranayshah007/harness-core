package io.harness.helpers.docker;

import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
// TODO (Vistaar): Rename vm variables to common variables
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.DRONE_COMMIT_BRANCH;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.DRONE_COMMIT_LINK;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.DRONE_COMMIT_SHA;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.DRONE_REMOTE_URL;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.DRONE_SOURCE_BRANCH;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.DRONE_TARGET_BRANCH;
import static io.harness.delegate.task.citasks.vm.helper.CIVMConstants.NETWORK_ID;

import io.harness.delegate.beans.ci.docker.CIDockerInitializeTaskParams;
import io.harness.delegate.beans.ci.docker.CIDockerInitializeTaskRequest;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.task.citasks.cik8handler.SecretSpecBuilder;
import io.harness.delegate.task.citasks.cik8handler.helper.SecretVolumesHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIDockerInitializeStepConverter {
  @Inject SecretVolumesHelper secretVolumesHelper;
  @Inject private SecretSpecBuilder secretSpecBuilder;
  public CIDockerInitializeTaskRequest convert(CIDockerInitializeTaskParams params) {
    Map<String, String> env = new HashMap<>();
    List<String> secrets = new ArrayList<>();
    if (isNotEmpty(params.getSecrets())) {
      secrets.addAll(params.getSecrets());
    }
    if (isNotEmpty(params.getEnvironment())) {
      env = params.getEnvironment();
    }

    if (params.getGitConnector() != null) {
      Map<String, SecretParams> secretVars = secretSpecBuilder.decryptGitSecretVariables(params.getGitConnector());
      log.info("secretVars: ", secretVars);
      for (Map.Entry<String, SecretParams> entry : secretVars.entrySet()) {
        String secret = new String(decodeBase64(entry.getValue().getValue()));
        env.put(entry.getKey(), secret);
        secrets.add(secret);
      }
    }

    CIDockerInitializeTaskRequest.TIConfig tiConfig = CIDockerInitializeTaskRequest.TIConfig.builder()
                                                          .url(params.getTiUrl())
                                                          .token(params.getTiSvcToken())
                                                          .accountID(params.getAccountID())
                                                          .orgID(params.getOrgID())
                                                          .projectID(params.getProjectID())
                                                          .pipelineID(params.getPipelineID())
                                                          .stageID(params.getStageID())
                                                          .buildID(params.getBuildID())
                                                          .repo(env.getOrDefault(DRONE_REMOTE_URL, ""))
                                                          .sha(env.getOrDefault(DRONE_COMMIT_SHA, ""))
                                                          .sourceBranch(env.getOrDefault(DRONE_SOURCE_BRANCH, ""))
                                                          .targetBranch(env.getOrDefault(DRONE_TARGET_BRANCH, ""))
                                                          .commitBranch(env.getOrDefault(DRONE_COMMIT_BRANCH, ""))
                                                          .commitLink(env.getOrDefault(DRONE_COMMIT_LINK, ""))
                                                          .build();

    CIDockerInitializeTaskRequest.Config config =
        CIDockerInitializeTaskRequest.Config.builder()
            .envs(env)
            .secrets(secrets)
            .network(CIDockerInitializeTaskRequest.Network.builder()
                         .id(NETWORK_ID + "-" + params.getStageRuntimeId())
                         .build())
            .logConfig(CIDockerInitializeTaskRequest.LogConfig.builder()
                           .url(params.getLogStreamUrl())
                           .token(params.getLogSvcToken())
                           .accountID(params.getAccountID())
                           .indirectUpload(params.isLogSvcIndirectUpload())
                           .build())
            .tiConfig(tiConfig)
            .volumes(getVolumes(params.getVolToMountPath(), params.getStageRuntimeId()))
            .build();
    return CIDockerInitializeTaskRequest.builder()
        .id(params.getStageRuntimeId())
        .config(config)
        .logKey(params.getLogKey())
        .build();
  }

  private List<CIDockerInitializeTaskRequest.Volume> getVolumes(
      Map<String, String> volToMountPath, String stageRuntimeId) {
    List<CIDockerInitializeTaskRequest.Volume> volumes = new ArrayList<>();
    if (isEmpty(volToMountPath)) {
      return volumes;
    }

    for (Map.Entry<String, String> entry : volToMountPath.entrySet()) {
      String convertedId = stageRuntimeId.replace("-", "");
      convertedId = convertedId.replace("_", "");
      String key = secretVolumesHelper.getSecretKey(convertedId, entry.getValue());
      volumes.add(CIDockerInitializeTaskRequest.Volume.builder()
                      .emptyDir(CIDockerInitializeTaskRequest.EmptyDir.builder().id(key).name(key).build())
                      .build());
    }
    return volumes;
  }
}
