/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.network.SafeHttpCall.execute;

import static java.lang.String.format;
import static java.time.Duration.ofMinutes;
import static java.util.Collections.emptyList;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.CustomDeploymentServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.CustomDeploymentInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.customdeployment.FetchInstanceScriptTaskNGRequest;
import io.harness.delegate.task.customdeployment.FetchInstanceScriptTaskNGResponse;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.CustomDeploymentNGInstanceSyncPerpetualTaskParams;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutorConfig;

import software.wings.sm.states.customdeploymentng.InstanceMapperUtils;

import com.google.common.base.Charsets;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_TEMPLATES})
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class CustomDeploymentInstanceSyncPerpetualTaskExecuter implements PerpetualTaskExecutor {
  public static final String COMMAND_UNIT = "Execute";
  public static final String OUTPUT_PATH_KEY = "INSTANCE_OUTPUT_PATH";
  public static final String WORKING_DIRECTORY = "/tmp";
  private static final String SUCCESS_RESPONSE_MSG = "success";
  public static final String INSTANCE_NAME = "instancename";
  private static final long DEFAULT_TIMEOUT_IN_MINUTES = 10;

  @Inject private ShellExecutorFactoryNG shellExecutorFactory;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  static Function<InstanceMapperUtils.HostProperties, CustomDeploymentServerInstanceInfo> jsonMapper = hostProperties
      -> CustomDeploymentServerInstanceInfo.builder()
             .instanceId(hostProperties.getHostName())
             .instanceName(hostProperties.getHostName())
             .properties(hostProperties.getOtherPropeties())
             .build();
  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the CustomDeployment InstanceSync perpetual task executor for task id: {}", taskId);
    CustomDeploymentNGInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), CustomDeploymentNGInstanceSyncPerpetualTaskParams.class);
    return executeCustomDeploymentInstanceSyncTask(taskId, taskParams);
  }

  private PerpetualTaskResponse executeCustomDeploymentInstanceSyncTask(
      PerpetualTaskId taskId, CustomDeploymentNGInstanceSyncPerpetualTaskParams taskParams) {
    List<ServerInstanceInfo> serverInstanceInfos = getServerInstanceInfo(taskId, taskParams);
    log.info("CustomDeployment Instance sync nInstances: {}, task id: {}",
        isEmpty(serverInstanceInfos) ? 0 : serverInstanceInfos.size(), taskId);
    String instanceSyncResponseMsg = publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos);
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfo(
      PerpetualTaskId taskId, CustomDeploymentNGInstanceSyncPerpetualTaskParams taskParams) {
    try {
      final FetchInstanceScriptTaskNGResponse response = executeScript(taskParams, taskId.getId());
      if (CommandExecutionStatus.FAILURE.equals(response.getCommandExecutionStatus())) {
        return Collections.emptyList();
      }
      final List<CustomDeploymentServerInstanceInfo> customDeploymentServerInstanceInfos =
          InstanceMapperUtils.mapJsonToInstanceElements(INSTANCE_NAME, taskParams.getInstanceAttributesMap(),
              taskParams.getInstancesListPath(), response.getOutput(), jsonMapper);
      customDeploymentServerInstanceInfos.forEach(serverInstanceInfo -> {
        serverInstanceInfo.setInstanceFetchScript(taskParams.getScript());
        serverInstanceInfo.setInfrastructureKey(taskParams.getInfrastructureKey());
      });
      return customDeploymentServerInstanceInfos.stream()
          .map(ServerInstanceInfo.class ::cast)
          .collect(Collectors.toList());
    } catch (Exception ex) {
      log.warn("Unable to get list of server instances in perpetual task ", ex);
      return Collections.emptyList();
    }
  }
  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos) {
    CustomDeploymentInstanceSyncPerpetualTaskResponse instanceSyncResponse =
        CustomDeploymentInstanceSyncPerpetualTaskResponse.builder()
            .serverInstanceDetails(serverInstanceInfos)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg =
          format("Failed to publish CustomDeployment instance sync result PerpetualTaskId [%s], accountId [%s]",
              taskId.getId(), accountId);
      log.error(errorMsg + ", serverInstanceInfos: {}", serverInstanceInfos, e);
      return errorMsg;
    }
    return SUCCESS_RESPONSE_MSG;
  }

  private FetchInstanceScriptTaskNGResponse executeScript(
      CustomDeploymentNGInstanceSyncPerpetualTaskParams taskParams, String taskId) {
    String workingDir = null;
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    try {
      String basePath = Paths.get("fetchInstanceScript").toAbsolutePath().toString();
      workingDir = Paths.get(basePath, taskId).toString();
      String outputPath = Paths.get(workingDir, "output.json").toString();

      Map<String, String> variablesMap = new HashMap<>();
      variablesMap.put(OUTPUT_PATH_KEY, outputPath);
      createNewFile(outputPath);

      FetchInstanceScriptTaskNGRequest taskParameters = FetchInstanceScriptTaskNGRequest.builder()
                                                            .accountId(taskParams.getAccountId())
                                                            .executionId(taskId)
                                                            .scriptBody(taskParams.getScript())
                                                            .outputPathKey(OUTPUT_PATH_KEY)
                                                            .variables(variablesMap)
                                                            .build();
      ShellExecutorConfig shellExecutorConfig = getShellExecutorConfig(taskParameters);
      ScriptProcessExecutor executor =
          shellExecutorFactory.getExecutor(shellExecutorConfig, null, commandUnitsProgress);
      ExecuteCommandResponse executeCommandResponse = executor.executeCommandString(
          taskParameters.getScriptBody(), emptyList(), emptyList(), ofMinutes(DEFAULT_TIMEOUT_IN_MINUTES).toMillis());

      String message = String.format("Execution finished with status: %s", executeCommandResponse.getStatus());
      if (executeCommandResponse.getStatus() == CommandExecutionStatus.FAILURE) {
        return FetchInstanceScriptTaskNGResponse.builder()
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage(message)
            .build();
      }
      try {
        return FetchInstanceScriptTaskNGResponse.builder()
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .commandExecutionStatus(SUCCESS)
            .output(new String(Files.readAllBytes(Paths.get(outputPath)), Charsets.UTF_8))
            .build();
      } catch (IOException e) {
        throw new InvalidRequestException("Error occurred while reading output file", e);
      }
    } catch (Exception ex) {
      log.error("Exception Occured While Running Custom Deployment NG Perpetual Task:{}, Message: {}", taskId,
          ExceptionUtils.getMessage(ex));
      return FetchInstanceScriptTaskNGResponse.builder()
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(ex))
          .build();
    } finally {
      try {
        deleteDirectoryAndItsContentIfExists(workingDir);
      } catch (IOException e) {
        log.warn(String.format("Failed to delete working directory: %s", workingDir));
      }
    }
  }

  private ShellExecutorConfig getShellExecutorConfig(FetchInstanceScriptTaskNGRequest taskParameters) {
    return ShellExecutorConfig.builder()
        .accountId(taskParameters.getAccountId())
        .executionId(taskParameters.getExecutionId())
        .commandUnitName(COMMAND_UNIT)
        .environment(taskParameters.getVariables())
        .scriptType(ScriptType.BASH)
        .build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }

  private File createNewFile(String path) {
    File file = new File(path);
    boolean mkdirs = file.getParentFile().mkdirs();
    if (!mkdirs && !file.getParentFile().exists()) {
      throw new InvalidRequestException(String.format("Unable to create directory for output file: %s", path));
    }
    try {
      file.createNewFile();
    } catch (IOException e) {
      throw new InvalidRequestException("Error occurred in creating output file", e);
    }
    return file;
  }
}
