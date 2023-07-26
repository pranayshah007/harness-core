/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless.container.steps;

import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.cdng.aws.sam.DownloadManifestsCommonHelper;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.plugininfoproviders.GitClonePluginInfoProvider;
import io.harness.cdng.plugininfoproviders.ServerlessV2PluginInfoProviderHelper;
import io.harness.cdng.serverless.beans.ServerlessV2ValuesYamlDataOutcome;
import io.harness.cdng.serverless.beans.ServerlessV2ValuesYamlDataOutcome.ServerlessV2ValuesYamlDataOutcomeBuilder;
import io.harness.cdng.serverless.container.steps.outcome.ServerlessV2DirectoryPathsOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepOutput;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plugin.GitCloneStep;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.YamlUtils;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class ServerlessDownloadManifestsStepHelper {
  @Inject private OutcomeService outcomeService;

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Inject private GitClonePluginInfoProvider gitClonePluginInfoProvider;

  @Inject private EngineExpressionService engineExpressionService;

  @Inject private ServerlessV2PluginInfoProviderHelper serverlessV2PluginInfoProviderHelper;

  @Inject private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;

  @Inject DownloadManifestsCommonHelper downloadManifestsCommonHelper;

  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepInputPackage inputPackage, GitCloneStep gitCloneStep) {
    ManifestsOutcome manifestsOutcome = serverlessV2PluginInfoProviderHelper.fetchManifestsOutcome(ambiance);

    AsyncExecutableResponse samDirectoryAsyncExecutableResponse =
        getAsyncExecutableResponseForServerlessAwsLambdaManifest(
            ambiance, inputPackage, gitCloneStep, manifestsOutcome);

    List<String> callbackIds = new ArrayList<>(samDirectoryAsyncExecutableResponse.getCallbackIdsList());
    List<String> logKeys = new ArrayList<>(samDirectoryAsyncExecutableResponse.getLogKeysList());

    ValuesManifestOutcome valuesManifestOutcome =
        (ValuesManifestOutcome) serverlessV2PluginInfoProviderHelper.getServerlessAwsLambdaValuesManifestOutcome(
            manifestsOutcome.values());

    if (valuesManifestOutcome != null) {
      AsyncExecutableResponse valuesAsyncExecutableResponse =
          getAsyncExecutableResponseForValuesManifest(ambiance, inputPackage, gitCloneStep, valuesManifestOutcome);
      callbackIds.addAll(valuesAsyncExecutableResponse.getCallbackIdsList());
      logKeys.addAll(valuesAsyncExecutableResponse.getLogKeysList());
    }

    return AsyncExecutableResponse.newBuilder()
        .addAllCallbackIds(callbackIds)
        .setStatus(samDirectoryAsyncExecutableResponse.getStatus())
        .addAllLogKeys(logKeys)
        .build();
  }

  public AsyncExecutableResponse getAsyncExecutableResponseForServerlessAwsLambdaManifest(
      Ambiance ambiance, StepInputPackage inputPackage, GitCloneStep gitCloneStep, ManifestsOutcome manifestsOutcome) {
    ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaDirectoryManifestOutcome =
        (ServerlessAwsLambdaManifestOutcome) serverlessV2PluginInfoProviderHelper
            .getServerlessAwsLambdaDirectoryManifestOutcome(manifestsOutcome.values());

    GitCloneStepInfo gitCloneStepInfo = downloadManifestsCommonHelper.getGitCloneStepInfoFromManifestOutcome(
        serverlessAwsLambdaDirectoryManifestOutcome);

    StepElementParameters stepElementParameters = downloadManifestsCommonHelper.getGitStepElementParameters(
        serverlessAwsLambdaDirectoryManifestOutcome, gitCloneStepInfo);

    Ambiance ambianceForServerlessAwsLambdaManifest = downloadManifestsCommonHelper.buildAmbianceForGitClone(
        ambiance, downloadManifestsCommonHelper.getGitCloneStepIdentifier(serverlessAwsLambdaDirectoryManifestOutcome));
    return gitCloneStep.executeAsyncAfterRbac(
        ambianceForServerlessAwsLambdaManifest, stepElementParameters, inputPackage);
  }

  public AsyncExecutableResponse getAsyncExecutableResponseForValuesManifest(Ambiance ambiance,
      StepInputPackage inputPackage, GitCloneStep gitCloneStep, ValuesManifestOutcome valuesManifestOutcome) {
    GitCloneStepInfo valuesGitCloneStepInfo =
        downloadManifestsCommonHelper.getGitCloneStepInfoFromManifestOutcomeWithOutputFilePathContents(
            valuesManifestOutcome,
            Collections.singletonList(
                serverlessV2PluginInfoProviderHelper.getValuesPathFromValuesManifestOutcome(valuesManifestOutcome)));

    StepElementParameters valuesStepElementParameters =
        downloadManifestsCommonHelper.getGitStepElementParameters(valuesManifestOutcome, valuesGitCloneStepInfo);

    Ambiance ambianceForValuesManifest = downloadManifestsCommonHelper.buildAmbianceForGitClone(
        ambiance, downloadManifestsCommonHelper.getGitCloneStepIdentifier(valuesManifestOutcome));

    return gitCloneStep.executeAsyncAfterRbac(ambianceForValuesManifest, valuesStepElementParameters, inputPackage);
  }

  public StepResponse handleAsyncResponse(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    ManifestsOutcome manifestsOutcome = serverlessV2PluginInfoProviderHelper.fetchManifestsOutcome(ambiance);

    handleResponseForValuesManifest(ambiance, responseDataMap, manifestsOutcome);

    StepResponse.StepOutcome stepOutcome = handleResponseForServerlessAwsLambdaManifest(manifestsOutcome);

    return StepResponse.builder().stepOutcome(stepOutcome).status(Status.SUCCEEDED).build();
  }

  public StepOutcome handleResponseForServerlessAwsLambdaManifest(ManifestsOutcome manifestsOutcome) {
    ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaDirectoryManifestOutcome =
        (ServerlessAwsLambdaManifestOutcome) serverlessV2PluginInfoProviderHelper
            .getServerlessAwsLambdaDirectoryManifestOutcome(manifestsOutcome.values());
    String serverlessDirectoryPath =
        serverlessV2PluginInfoProviderHelper.getServerlessAwsLambdaDirectoryPathFromManifestOutcome(
            serverlessAwsLambdaDirectoryManifestOutcome);
    ServerlessV2DirectoryPathsOutcome serverlessV2DirectoryPathsOutcome =
        ServerlessV2DirectoryPathsOutcome.builder().serverlessDirectoryPath(serverlessDirectoryPath).build();

    return StepOutcome.builder()
        .outcome(serverlessV2DirectoryPathsOutcome)
        .name(OutcomeExpressionConstants.SERVERLESS_V2_DIRECTORY_PATH_OUTCOME)
        .group(StepCategory.STEP_GROUP.name())
        .build();
  }

  public void handleResponseForValuesManifest(
      Ambiance ambiance, Map<String, ResponseData> responseDataMap, ManifestsOutcome manifestsOutcome) {
    ValuesManifestOutcome valuesManifestOutcome =
        (ValuesManifestOutcome) serverlessV2PluginInfoProviderHelper.getServerlessAwsLambdaValuesManifestOutcome(
            manifestsOutcome.values());

    if (valuesManifestOutcome != null) {
      ServerlessV2ValuesYamlDataOutcomeBuilder serverlessValuesYamlDataOutcomeBuilder =
          ServerlessV2ValuesYamlDataOutcome.builder();

      containerStepExecutionResponseHelper.deserializeResponse(responseDataMap);

      for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
        ResponseData responseData = entry.getValue();
        if (responseData instanceof StepStatusTaskResponseData) {
          StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) responseData;
          if (StepExecutionStatus.SUCCESS == stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus()) {
            StepOutput stepOutput = stepStatusTaskResponseData.getStepStatus().getOutput();

            renderValuesManifestAndSaveToSweepingOutput(
                ambiance, valuesManifestOutcome, serverlessValuesYamlDataOutcomeBuilder, stepOutput);
          }
        }
      }
    }
  }

  public void renderValuesManifestAndSaveToSweepingOutput(Ambiance ambiance,
      ValuesManifestOutcome valuesManifestOutcome,
      ServerlessV2ValuesYamlDataOutcomeBuilder serverlessValuesYamlDataOutcomeBuilder, StepOutput stepOutput) {
    if (stepOutput instanceof StepMapOutput) {
      String valuesYamlPath =
          serverlessV2PluginInfoProviderHelper.getValuesPathFromValuesManifestOutcome(valuesManifestOutcome);

      StepMapOutput stepMapOutput = (StepMapOutput) stepOutput;
      if (EmptyPredicate.isNotEmpty(stepMapOutput.getMap())) {
        String valuesYamlContentBase64 = stepMapOutput.getMap().get(valuesYamlPath);
        String valuesYamlContent = new String(Base64.getDecoder().decode(valuesYamlContentBase64));
        valuesYamlContent = engineExpressionService.renderExpression(ambiance, valuesYamlContent);

        if (StringUtils.isNotEmpty(valuesYamlPath) && StringUtils.isNotEmpty(valuesYamlContent)) {
          serverlessValuesYamlDataOutcomeBuilder.valuesYamlPath(valuesYamlPath);
          serverlessValuesYamlDataOutcomeBuilder.valuesYamlContent(valuesYamlContent);
          executionSweepingOutputService.consume(ambiance,
              OutcomeExpressionConstants.SERVERLESS_VALUES_YAML_DATA_OUTCOME,
              serverlessValuesYamlDataOutcomeBuilder.build(), StepOutcomeGroup.STEP.name());
        }
      }
    }
  }

  public PluginCreationResponseList getPluginInfoList(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;
    try {
      cdAbstractStepNode = getCdAbstractStepNode(stepJsonNode);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }

    List<PluginCreationResponseWrapper> pluginCreationResponseWrapperList = new ArrayList<>();

    ManifestsOutcome manifestsOutcome = serverlessV2PluginInfoProviderHelper.fetchManifestsOutcome(ambiance);

    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        getPluginCreationResponseWrapperForServerlessAwsLambdaManifest(
            request, usedPorts, ambiance, cdAbstractStepNode, manifestsOutcome);

    usedPorts.addAll(pluginCreationResponseWrapper.getResponse().getPluginDetails().getPortUsedList());

    pluginCreationResponseWrapperList.add(pluginCreationResponseWrapper);

    // Values Yaml

    ValuesManifestOutcome valuesManifestOutcome =
        (ValuesManifestOutcome) serverlessV2PluginInfoProviderHelper.getServerlessAwsLambdaValuesManifestOutcome(
            manifestsOutcome.values());

    if (valuesManifestOutcome != null) {
      PluginCreationResponseWrapper valuesPluginCreationResponseWrapper =
          getPluginCreationResponseWrapperForValuesManifest(
              request, usedPorts, ambiance, cdAbstractStepNode, valuesManifestOutcome);
      pluginCreationResponseWrapperList.add(valuesPluginCreationResponseWrapper);
    }

    return PluginCreationResponseList.newBuilder().addAllResponse(pluginCreationResponseWrapperList).build();
  }

  public PluginCreationResponseWrapper getPluginCreationResponseWrapperForValuesManifest(PluginCreationRequest request,
      Set<Integer> usedPorts, Ambiance ambiance, CdAbstractStepNode cdAbstractStepNode,
      ValuesManifestOutcome valuesManifestOutcome) {
    GitCloneStepInfo valuesGitCloneStepInfo =
        downloadManifestsCommonHelper.getGitCloneStepInfoFromManifestOutcomeWithOutputFilePathContents(
            valuesManifestOutcome,
            Collections.singletonList(
                serverlessV2PluginInfoProviderHelper.getValuesPathFromValuesManifestOutcome(valuesManifestOutcome)));

    GitCloneStepNode valuesGitCloneStepNode = downloadManifestsCommonHelper.getGitCloneStepNode(
        valuesManifestOutcome, valuesGitCloneStepInfo, cdAbstractStepNode);

    PluginCreationRequest valuesPluginCreationRequest =
        request.toBuilder().setStepJsonNode(YamlUtils.writeYamlString(valuesGitCloneStepNode)).build();

    return gitClonePluginInfoProvider.getPluginInfo(valuesPluginCreationRequest, usedPorts, ambiance);
  }

  public PluginCreationResponseWrapper getPluginCreationResponseWrapperForServerlessAwsLambdaManifest(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance, CdAbstractStepNode cdAbstractStepNode,
      ManifestsOutcome manifestsOutcome) {
    ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
        (ServerlessAwsLambdaManifestOutcome) serverlessV2PluginInfoProviderHelper
            .getServerlessAwsLambdaDirectoryManifestOutcome(manifestsOutcome.values());

    GitCloneStepInfo gitCloneStepInfo =
        downloadManifestsCommonHelper.getGitCloneStepInfoFromManifestOutcome(serverlessAwsLambdaManifestOutcome);

    GitCloneStepNode gitCloneStepNode = downloadManifestsCommonHelper.getGitCloneStepNode(
        serverlessAwsLambdaManifestOutcome, gitCloneStepInfo, cdAbstractStepNode);

    PluginCreationRequest pluginCreationRequest =
        request.toBuilder().setStepJsonNode(getStepJsonNode(gitCloneStepNode)).build();

    return gitClonePluginInfoProvider.getPluginInfo(pluginCreationRequest, usedPorts, ambiance);
  }

  public RefObject getOutcomeRefObject() {
    return RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS);
  }

  public CdAbstractStepNode getCdAbstractStepNode(String stepJsonNode) throws IOException {
    return YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
  }

  public String getStepJsonNode(GitCloneStepNode gitCloneStepNode) {
    return YamlUtils.writeYamlString(gitCloneStepNode);
  }
}
