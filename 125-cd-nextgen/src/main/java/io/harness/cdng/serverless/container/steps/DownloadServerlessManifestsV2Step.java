/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless.container.steps;

import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepOutput;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plugin.GitCloneStep;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class DownloadServerlessManifestsV2Step implements AsyncExecutableWithRbac<StepElementParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.DOWNLOAD_SERVERLESS_MANIFESTS.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject GitCloneStep gitCloneStep;

  @Inject private OutcomeService outcomeService;

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Inject private EngineExpressionService engineExpressionService;

  @Inject DownloadManifestsStepHelper downloadManifestsStepHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    ManifestsOutcome manifestsOutcome = downloadManifestsStepHelper.fetchManifestsOutcome(ambiance);

    ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaDirectoryManifestOutcome =
        (ServerlessAwsLambdaManifestOutcome) downloadManifestsStepHelper.getServerlessAwsLambdaDirectoryManifestOutcome(
            manifestsOutcome.values());

    List<String> callbackIds = new ArrayList<>();
    List<String> logKeys = new ArrayList<>();

    GitCloneStepInfo gitCloneStepInfo =
        downloadManifestsStepHelper.getGitCloneStepInfoFromManifestOutcome(serverlessAwsLambdaDirectoryManifestOutcome);

    StepElementParameters stepElementParameters = downloadManifestsStepHelper.getGitStepElementParameters(
        serverlessAwsLambdaDirectoryManifestOutcome, gitCloneStepInfo);

    Ambiance ambiance1 = downloadManifestsStepHelper.buildAmbianceForGitClone(
        ambiance, downloadManifestsStepHelper.getGitCloneStepIdentifier(serverlessAwsLambdaDirectoryManifestOutcome));
    AsyncExecutableResponse samDirectoryAsyncExecutableResponse =
        gitCloneStep.executeAsyncAfterRbac(ambiance1, stepElementParameters, inputPackage);

    callbackIds.addAll(samDirectoryAsyncExecutableResponse.getCallbackIdsList());
    logKeys.addAll(samDirectoryAsyncExecutableResponse.getLogKeysList());

    ValuesManifestOutcome valuesManifestOutcome =
        (ValuesManifestOutcome) downloadManifestsStepHelper.getServerlessAwsLambdaValuesManifestOutcome(
            manifestsOutcome.values());

    if (valuesManifestOutcome != null) {
      GitCloneStepInfo valuesGitCloneStepInfo =
          downloadManifestsStepHelper.getGitCloneStepInfoFromManifestOutcome(valuesManifestOutcome);

      StepElementParameters valuesStepElementParameters =
          downloadManifestsStepHelper.getGitStepElementParameters(valuesManifestOutcome, valuesGitCloneStepInfo);

      Ambiance ambiance2 = downloadManifestsStepHelper.buildAmbianceForGitClone(
          ambiance, downloadManifestsStepHelper.getGitCloneStepIdentifier(valuesManifestOutcome));

      AsyncExecutableResponse valuesAsyncExecutableResponse =
          gitCloneStep.executeAsyncAfterRbac(ambiance2, valuesStepElementParameters, inputPackage);
      callbackIds.addAll(valuesAsyncExecutableResponse.getCallbackIdsList());
      logKeys.addAll(valuesAsyncExecutableResponse.getLogKeysList());
    }

    return AsyncExecutableResponse.newBuilder()
        .addAllCallbackIds(callbackIds)
        .setStatus(samDirectoryAsyncExecutableResponse.getStatus())
        .addAllLogKeys(logKeys)
        .build();
  }

  public String getValuesPathFromValuesManifestOutcome(ValuesManifestOutcome valuesManifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) valuesManifestOutcome.getStore();
    return "/harness/" + valuesManifestOutcome.getIdentifier() + "/" + gitStoreConfig.getPaths().getValue().get(0);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {}

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    ManifestsOutcome manifestsOutcome =
        (ManifestsOutcome) outcomeService.resolveOptional(ambiance, getOutcomeRefObject()).getOutcome();
    ValuesManifestOutcome valuesManifestOutcome =
        (ValuesManifestOutcome) downloadManifestsStepHelper.getServerlessAwsLambdaValuesManifestOutcome(
            manifestsOutcome.values());

    if (valuesManifestOutcome != null) {
      ServerlessValuesYamlDataOutcome.ServerlessValuesYamlDataOutcomeBuilder serverlessValuesYamlDataOutcomeBuilder =
          ServerlessValuesYamlDataOutcome.builder();

      for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
        ResponseData responseData = entry.getValue();
        if (responseData instanceof StepStatusTaskResponseData) {
          StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) responseData;
          if (stepStatusTaskResponseData != null
              && stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus() == StepExecutionStatus.SUCCESS) {
            StepOutput stepOutput = stepStatusTaskResponseData.getStepStatus().getOutput();

            if (stepOutput instanceof StepMapOutput) {
              String valuesYamlPath = getValuesPathFromValuesManifestOutcome(valuesManifestOutcome);

              StepMapOutput stepMapOutput = (StepMapOutput) stepOutput;
              if (stepMapOutput.getMap() != null && stepMapOutput.getMap().size() > 0) {
                String valuesYamlContentBase64 = stepMapOutput.getMap().get(valuesYamlPath);
                String valuesYamlContent = new String(Base64.getDecoder().decode(valuesYamlContentBase64));
                valuesYamlContent = engineExpressionService.renderExpression(ambiance, valuesYamlContent);

                if (!StringUtils.isEmpty(valuesYamlPath) && !StringUtils.isEmpty(valuesYamlContent)) {
                  serverlessValuesYamlDataOutcomeBuilder.valuesYamlPath(valuesYamlPath);
                  serverlessValuesYamlDataOutcomeBuilder.valuesYamlContent(valuesYamlContent);
                  executionSweepingOutputService.consume(ambiance,
                      OutcomeExpressionConstants.SERVERLESS_VALUES_YAML_DATA_OUTCOME,
                      serverlessValuesYamlDataOutcomeBuilder.build(), StepOutcomeGroup.STEP.name());
                }
              }
            }
          }
        }
      }
    }

    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  public RefObject getOutcomeRefObject() {
    return RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS);
  }
}
