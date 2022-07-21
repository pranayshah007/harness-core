/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.docker;

import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.citasks.docker.helper.CIDockerConstants.DRONE_COMMIT_BRANCH;
import static io.harness.delegate.task.citasks.docker.helper.CIDockerConstants.DRONE_COMMIT_LINK;
import static io.harness.delegate.task.citasks.docker.helper.CIDockerConstants.DRONE_COMMIT_SHA;
import static io.harness.delegate.task.citasks.docker.helper.CIDockerConstants.DRONE_REMOTE_URL;
import static io.harness.delegate.task.citasks.docker.helper.CIDockerConstants.DRONE_SOURCE_BRANCH;
import static io.harness.delegate.task.citasks.docker.helper.CIDockerConstants.DRONE_TARGET_BRANCH;
import static io.harness.delegate.task.citasks.docker.helper.CIDockerConstants.DOCKER_NETWORK_ID;
import static io.harness.delegate.task.citasks.docker.helper.CIDockerConstants.RUN_STEP_KIND;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.ci.docker.CIDockerInitializeTaskParams;
import io.harness.delegate.beans.ci.docker.DockerServiceStatus;
import io.harness.delegate.beans.ci.docker.ExecuteStepDockerResponse;
import io.harness.delegate.beans.ci.docker.ExecuteStepDockerRequest;
import io.harness.delegate.beans.ci.docker.SetupDockerRequest;
import io.harness.delegate.beans.ci.docker.SetupDockerResponse;
import io.harness.delegate.beans.ci.docker.DockerServiceDependency;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.citasks.CIInitializeTaskHandler;
import io.harness.delegate.task.citasks.cik8handler.SecretSpecBuilder;
import io.harness.delegate.task.citasks.cik8handler.helper.SecretVolumesHelper;
import io.harness.delegate.task.citasks.cik8handler.helper.ProxyVariableHelper;
import io.harness.delegate.task.citasks.docker.helper.HttpHelper;
import io.harness.delegate.task.citasks.docker.helper.StepExecutionHelper;
import io.harness.logging.CommandExecutionStatus;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIDockerInitializeTaskHandler implements CIInitializeTaskHandler {
    @NotNull private Type type = Type.DOCKER;
    @Inject private HttpHelper httpHelper;
    @Inject private SecretSpecBuilder secretSpecBuilder;
    @Inject private SecretVolumesHelper secretVolumesHelper;
    @Inject private ProxyVariableHelper proxyVariableHelper;
    @Inject private StepExecutionHelper stepExecutionHelper;

    @Override
    public Type getType() {
        return type;
    }

    public ExecuteStepDockerResponse executeTaskInternal(
            CIInitializeTaskParams ciInitializeTaskParams, ILogStreamingTaskClient logStreamingTaskClient, String taskId) {
        CIDockerInitializeTaskParams ciDockerInitializeTaskParams = (CIDockerInitializeTaskParams) ciInitializeTaskParams;
        log.info(
                "Received request to initialize stage with stage runtime ID {}", ciDockerInitializeTaskParams.getStageRuntimeId());
        ExecuteStepDockerResponse response = callRunnerForSetup(ciDockerInitializeTaskParams, taskId);
        List<DockerServiceStatus> serviceStatuses = new ArrayList<>();
        if (isNotEmpty(ciDockerInitializeTaskParams.getServiceDependencies())) {
            for (DockerServiceDependency serviceDependency : ciDockerInitializeTaskParams.getServiceDependencies()) {
                serviceStatuses.add(startService(serviceDependency, taskId, response.getIpAddress(), ciDockerInitializeTaskParams));
            }
        }
        response.setServiceStatuses(serviceStatuses);
        return response;
    }

    private ExecuteStepDockerResponse callRunnerForSetup(CIDockerInitializeTaskParams ciDockerInitializeTaskParams, String taskId) {
        String errMessage = "";
        try {
            Response<SetupDockerResponse> response =
                    httpHelper.setupStageWithRetries(convertSetup(ciDockerInitializeTaskParams, taskId));
            if (response.isSuccessful()) {
                return ExecuteStepDockerResponse.builder()
                        .ipAddress(response.body().getIpAddress())
                        .errorMessage("")
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .build();
            } else {
                errMessage = format("failed with code: %d, message: %s", response.code(), response.errorBody());
            }
        } catch (Exception e) {
            log.error("Failed to setup Docker in runner", e);
            errMessage = e.toString();
        }

        return ExecuteStepDockerResponse.builder()
                .errorMessage(errMessage)
                .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                .build();
    }

    private SetupDockerRequest convertSetup(CIDockerInitializeTaskParams params, String taskId) {
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

        SetupDockerRequest.TIConfig tiConfig = SetupDockerRequest.TIConfig.builder()
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

        SetupDockerRequest.Config config =
                SetupDockerRequest.Config.builder()
                        .envs(env)
                        .secrets(secrets)
                        .network(SetupDockerRequest.Network.builder()
                                .id(DOCKER_NETWORK_ID + "-" + params.getStageRuntimeId())
                                .build())
                        .logConfig(SetupDockerRequest.LogConfig.builder()
                                .url(params.getLogStreamUrl())
                                .token(params.getLogSvcToken())
                                .accountID(params.getAccountID())
                                .indirectUpload(params.isLogSvcIndirectUpload())
                                .build())
                        .tiConfig(tiConfig)
                        .volumes(getVolumes(params.getVolToMountPath(), params.getStageRuntimeId()))
                        .build();
        return SetupDockerRequest.builder()
                .id(params.getStageRuntimeId())
                .config(config)
                .logKey(params.getLogKey())
                .build();
    }

    private List<SetupDockerRequest.Volume> getVolumes(
            Map<String, String> volToMountPath, String stageRuntimeId) {
        List<SetupDockerRequest.Volume> volumes = new ArrayList<>();
        if (isEmpty(volToMountPath)) {
            return volumes;
        }

        for (Map.Entry<String, String> entry : volToMountPath.entrySet()) {
            String convertedId = stageRuntimeId.replace("-", "");
            convertedId = convertedId.replace("_", "");
            String key = secretVolumesHelper.getSecretKey(convertedId, entry.getValue());
            volumes.add(SetupDockerRequest.Volume.builder()
                    .emptyDir(SetupDockerRequest.EmptyDir.builder().id(key).name(key).build())
                    .build());
        }
        return volumes;
    }

    private List<SetupDockerRequest.Volume> getVolumes(Map<String, String> volToMountPath) {
        List<SetupDockerRequest.Volume> volumes = new ArrayList<>();
        if (isEmpty(volToMountPath)) {
            return volumes;
        }

        for (Map.Entry<String, String> entry : volToMountPath.entrySet()) {
            volumes.add(SetupDockerRequest.Volume.builder()
                    .hostVolume(SetupDockerRequest.HostVolume.builder()
                            .id(entry.getKey())
                            .name(entry.getKey())
                            .path(entry.getValue())
                            .build())
                    .build());
        }
        return volumes;
    }

    private DockerServiceStatus startService(DockerServiceDependency serviceDependency, String taskId, String ipAddress,
                                             CIDockerInitializeTaskParams initializeTaskParams) {
        ExecuteStepDockerRequest request = convertService(serviceDependency, taskId, ipAddress, initializeTaskParams.getPoolID(),
                initializeTaskParams.getWorkingDir(), initializeTaskParams.getVolToMountPath());
        ExecuteStepDockerResponse serviceResponse = stepExecutionHelper.callRunnerForStepExecution(request);
        DockerServiceStatus.Status status = DockerServiceStatus.Status.ERROR;
        if (serviceResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
            status = DockerServiceStatus.Status.RUNNING;
        }
        return DockerServiceStatus.builder()
                .identifier(serviceDependency.getIdentifier())
                .name(serviceDependency.getName())
                .image(serviceDependency.getImage())
                .logKey(serviceDependency.getLogKey())
                .errorMessage(serviceResponse.getErrorMessage())
                .status(status)
                .build();
    }

    private ExecuteStepDockerRequest convertService(DockerServiceDependency params, String taskId, String ipAddress, String poolId,
                                              String workDir, Map<String, String> volToMountPath) {
        ExecuteStepDockerRequest.Config.ConfigBuilder configBuilder =
                ExecuteStepDockerRequest.Config.builder()
                        .id(params.getIdentifier())
                        .name(params.getIdentifier())
                        .logKey(params.getLogKey())
                        .workingDir(workDir)
                        .volumeMounts(stepExecutionHelper.getVolumeMounts(volToMountPath))
                        .image(params.getImage())
                        .pull(params.getPullPolicy())
                        .user(params.getRunAsUser())
                        .envs(params.getEnvVariables())
                        .detach(true)
                        .kind(RUN_STEP_KIND);
        ExecuteStepDockerRequest.ImageAuth imageAuth =
                stepExecutionHelper.getImageAuth(params.getImage(), params.getImageConnector());

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

        return ExecuteStepDockerRequest.builder()
                .correlationID(taskId)
                .poolId(poolId)
                .ipAddress(ipAddress)
                .config(configBuilder.build())
                .build();
    }
}
