/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateRequest;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.k8s.K8sConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.JsonUtils;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ShellExecutorConfig;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.CustomRepositoryResponse;
import software.wings.helpers.ext.jenkins.CustomRepositoryResponse.CustomRepositoryResponseBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.jayway.jsonpath.DocumentContext;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShellScriptExecutionOnDelegateNG {
  public static final String COMMAND_UNIT = "Execute";
  @Inject private ShellExecutorFactoryNG shellExecutorFactory;
  @Inject private SshExecutorFactoryNG sshExecutorFactoryNG;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private SecretDecryptionService secretDecryptionService;

  public ShellScriptTaskResponseNG executeOnDelegate(
      ShellScriptTaskParametersNG taskParameters, ILogStreamingTaskClient logStreamingTaskClient) {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ShellExecutorConfig shellExecutorConfig = getShellExecutorConfig(taskParameters);
    ScriptProcessExecutor executor =
        shellExecutorFactory.getExecutor(shellExecutorConfig, logStreamingTaskClient, commandUnitsProgress);
    // TODO: check later
    // if (taskParameters.isLocalOverrideFeatureFlag()) {
    //   taskParameters.setScript(delegateLocalConfigService.replacePlaceholdersWithLocalConfig(taskParameters.getScript()));
    // }
    ExecuteCommandResponse executeCommandResponse =
        executor.executeCommandString(taskParameters.getScript(), taskParameters.getOutputVars());
    return ShellScriptTaskResponseNG.builder()
        .executeCommandResponse(executeCommandResponse)
        .status(executeCommandResponse.getStatus())
        .errorMessage(getErrorMessage(executeCommandResponse.getStatus()))
        .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
        .build();
  }

  private ShellExecutorConfig getShellExecutorConfig(ShellScriptTaskParametersNG taskParameters) {
    String kubeConfigFileContent = taskParameters.getScript().contains(K8sConstants.HARNESS_KUBE_CONFIG_PATH)
            && taskParameters.getK8sInfraDelegateConfig() != null
        ? containerDeploymentDelegateBaseHelper.getKubeconfigFileContent(taskParameters.getK8sInfraDelegateConfig())
        : "";

    return ShellExecutorConfig.builder()
        .accountId(taskParameters.getAccountId())
        .executionId(taskParameters.getExecutionId())
        .commandUnitName(COMMAND_UNIT)
        .workingDirectory(taskParameters.getWorkingDirectory())
        .environment(taskParameters.getEnvironmentVariables())
        .kubeConfigContent(kubeConfigFileContent)
        .scriptType(taskParameters.getScriptType())
        .build();
  }

  private String getErrorMessage(CommandExecutionStatus status) {
    switch (status) {
      case QUEUED:
        return "Shell Script execution queued.";
      case FAILURE:
        return "Shell Script execution failed. Please check execution logs.";
      case RUNNING:
        return "Shell Script execution running.";
      case SKIPPED:
        return "Shell Script execution skipped.";
      case SUCCESS:
      default:
        return "";
    }
  }

  public List<BuildDetails> getBuildDetails(
      String artifactResultPath, CustomArtifactDelegateRequest customArtifactDelegateRequest) {
    // Convert to Build details
    List<BuildDetails> buildDetails = new ArrayList<>();
    File file = new File(artifactResultPath);
    CustomRepositoryResponse customRepositoryResponse;
    try {
      if (EmptyPredicate.isNotEmpty(customArtifactDelegateRequest.getArtifactsArrayPath())) {
        JsonNode jsonObject = (JsonNode) JsonUtils.readFromFile(file, JsonNode.class);
        String json = JsonUtils.asJson(jsonObject);
        customRepositoryResponse =
            mapToCustomRepositoryResponse(json, customArtifactDelegateRequest.getArtifactsArrayPath(),
                customArtifactDelegateRequest.getVersionPath(), customArtifactDelegateRequest.getAttributes());
      } else {
        customRepositoryResponse =
            (CustomRepositoryResponse) JsonUtils.readFromFile(file, CustomRepositoryResponse.class);
      }

      List<CustomRepositoryResponse.Result> results = customRepositoryResponse.getResults();
      List<String> buildNumbers = new ArrayList<>();
      if (isNotEmpty(results)) {
        results.forEach(result -> {
          final String buildNo = result.getBuildNo();
          if (isNotEmpty(buildNo)) {
            if (buildNumbers.contains(buildNo)) {
              log.warn(
                  "There is an entry with buildNo {} already exists. So, skipping the result. Please ensure that buildNo is unique across the results",
                  buildNo);
              return;
            }
            buildDetails.add(aBuildDetails()
                                 .withNumber(buildNo)
                                 .withMetadata(result.getMetadata())
                                 .withUiDisplayName("Build# " + buildNo)
                                 .build());
            buildNumbers.add(buildNo);
          } else {
            log.warn("There is an object in output without mandatory build number");
          }
        });
      } else {
        log.warn("Results are empty");
      }
      log.info("Retrieving build details of Custom Repository success");
    } catch (Exception ex) {
      String msg =
          "Failed to transform results to the Custom Repository Response. Please verify if the script output is in the required format. Reason ["
          + ExceptionUtils.getMessage(ex) + "]";
      log.error(msg);
      throw new InvalidArtifactServerException(msg, Level.INFO, USER);
    }
    return buildDetails;
  }

  public CustomRepositoryResponse mapToCustomRepositoryResponse(
      String json, String artifactRoot, String buildNoPath, Map<String, String> map) {
    DocumentContext ctx = JsonUtils.parseJson(json);
    CustomRepositoryResponseBuilder customRepositoryResponse =
        CustomRepositoryResponse.builder();
    List<CustomRepositoryResponse.Result> result = new ArrayList<>();

    LinkedList<LinkedHashMap> children = JsonUtils.jsonPath(ctx, artifactRoot + "[*]");
    for (int i = 0; i < children.size(); i++) {
      Map<String, String> metadata = new HashMap<>();
      CustomRepositoryResponse.Result.ResultBuilder res = CustomRepositoryResponse.Result.builder();
      res.buildNo(JsonUtils.jsonPath(ctx, artifactRoot + "[" + i + "]." + buildNoPath));
      for (Map.Entry<String, String> entry : map.entrySet()) {
        String mappedAttribute = EmptyPredicate.isEmpty(entry.getValue())
            ? entry.getKey().substring(entry.getKey().lastIndexOf('.') + 1)
            : entry.getValue().substring(entry.getValue().lastIndexOf('.') + 1);
        String value = JsonUtils.jsonPath(ctx, artifactRoot + "[" + i + "]." + entry.getKey()).toString();
        metadata.put(mappedAttribute, value);
      }
      res.metadata(metadata);
      result.add(res.build());
    }
    customRepositoryResponse.results(result);
    return customRepositoryResponse.build();
  }
}
