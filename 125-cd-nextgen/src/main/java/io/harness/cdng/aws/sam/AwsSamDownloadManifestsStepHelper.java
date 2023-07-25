/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.cdng.aws.sam.beans.AwsSamValuesYamlDataOutcome;
import io.harness.cdng.aws.sam.beans.AwsSamValuesYamlDataOutcome.AwsSamValuesYamlDataOutcomeBuilder;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.plugininfoproviders.GitClonePluginInfoProvider;
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
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@Slf4j
public class AwsSamDownloadManifestsStepHelper {
  @Inject private OutcomeService outcomeService;

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Inject private EngineExpressionService engineExpressionService;

  @Inject private GitClonePluginInfoProvider gitClonePluginInfoProvider;

  @Inject private DownloadManifestsCommonHelper downloadManifestsCommonHelper;

  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepInputPackage inputPackage, GitCloneStep gitCloneStep) {
    ManifestsOutcome manifestsOutcome = downloadManifestsCommonHelper.fetchManifestsOutcome(ambiance);

    AsyncExecutableResponse samDirectoryAsyncExecutableResponse =
        getAsyncExecutableResponseForAwsSamDirectoryManifest(ambiance, inputPackage, gitCloneStep, manifestsOutcome);

    List<String> callbackIds = new ArrayList<>(samDirectoryAsyncExecutableResponse.getCallbackIdsList());
    List<String> logKeys = new ArrayList<>(samDirectoryAsyncExecutableResponse.getLogKeysList());

    ValuesManifestOutcome valuesManifestOutcome =
        (ValuesManifestOutcome) getAwsSamValuesManifestOutcome(manifestsOutcome.values());

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

  private AsyncExecutableResponse getAsyncExecutableResponseForValuesManifest(Ambiance ambiance,
      StepInputPackage inputPackage, GitCloneStep gitCloneStep, ValuesManifestOutcome valuesManifestOutcome) {
    GitCloneStepInfo valuesGitCloneStepInfo =
        downloadManifestsCommonHelper.getGitCloneStepInfoFromManifestOutcome(valuesManifestOutcome);

    StepElementParameters valuesStepElementParameters =
        downloadManifestsCommonHelper.getGitStepElementParameters(valuesManifestOutcome, valuesGitCloneStepInfo);

    Ambiance ambianceForValuesManifest = downloadManifestsCommonHelper.buildAmbianceForGitClone(
        ambiance, downloadManifestsCommonHelper.getGitCloneStepIdentifier(valuesManifestOutcome));

    return gitCloneStep.executeAsyncAfterRbac(ambianceForValuesManifest, valuesStepElementParameters, inputPackage);
  }

  private AsyncExecutableResponse getAsyncExecutableResponseForAwsSamDirectoryManifest(
      Ambiance ambiance, StepInputPackage inputPackage, GitCloneStep gitCloneStep, ManifestsOutcome manifestsOutcome) {
    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome =
        (AwsSamDirectoryManifestOutcome) getAwsSamDirectoryManifestOutcome(manifestsOutcome.values());

    GitCloneStepInfo gitCloneStepInfo =
        downloadManifestsCommonHelper.getGitCloneStepInfoFromManifestOutcome(awsSamDirectoryManifestOutcome);

    StepElementParameters stepElementParameters =
        downloadManifestsCommonHelper.getGitStepElementParameters(awsSamDirectoryManifestOutcome, gitCloneStepInfo);

    Ambiance ambianceForAwsSamDirectoryManifest = downloadManifestsCommonHelper.buildAmbianceForGitClone(
        ambiance, downloadManifestsCommonHelper.getGitCloneStepIdentifier(awsSamDirectoryManifestOutcome));
    return gitCloneStep.executeAsyncAfterRbac(ambianceForAwsSamDirectoryManifest, stepElementParameters, inputPackage);
  }

  public StepResponse handleAsyncResponse(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    ManifestsOutcome manifestsOutcome =
        (ManifestsOutcome) outcomeService
            .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS))
            .getOutcome();
    handleResponseForValuesManifest(ambiance, responseDataMap, manifestsOutcome);
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private void handleResponseForValuesManifest(
      Ambiance ambiance, Map<String, ResponseData> responseDataMap, ManifestsOutcome manifestsOutcome) {
    ValuesManifestOutcome valuesManifestOutcome =
        (ValuesManifestOutcome) getAwsSamValuesManifestOutcome(manifestsOutcome.values());

    if (valuesManifestOutcome != null) {
      AwsSamValuesYamlDataOutcomeBuilder awsSamValuesYamlDataOutcomeBuilder = AwsSamValuesYamlDataOutcome.builder();

      for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
        ResponseData responseData = entry.getValue();
        if (responseData instanceof StepStatusTaskResponseData) {
          StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) responseData;
          if (StepExecutionStatus.SUCCESS == stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus()) {
            StepOutput stepOutput = stepStatusTaskResponseData.getStepStatus().getOutput();

            renderValuesManifestAndSaveToSweepingOutput(
                ambiance, valuesManifestOutcome, awsSamValuesYamlDataOutcomeBuilder, stepOutput);
          }
        }
      }
    }
  }

  private void renderValuesManifestAndSaveToSweepingOutput(Ambiance ambiance,
      ValuesManifestOutcome valuesManifestOutcome,
      AwsSamValuesYamlDataOutcomeBuilder awsSamValuesYamlDataOutcomeBuilder, StepOutput stepOutput) {
    if (stepOutput instanceof StepMapOutput) {
      String valuesYamlPath = getValuesPathFromValuesManifestOutcome(valuesManifestOutcome);

      StepMapOutput stepMapOutput = (StepMapOutput) stepOutput;
      if (EmptyPredicate.isNotEmpty(stepMapOutput.getMap())) {
        String valuesYamlContentBase64 = stepMapOutput.getMap().get(valuesYamlPath);
        String valuesYamlContent = new String(Base64.getDecoder().decode(valuesYamlContentBase64));
        valuesYamlContent = engineExpressionService.renderExpression(ambiance, valuesYamlContent);

        if (!StringUtils.isEmpty(valuesYamlPath) && !StringUtils.isEmpty(valuesYamlContent)) {
          awsSamValuesYamlDataOutcomeBuilder.valuesYamlPath(valuesYamlPath);
          awsSamValuesYamlDataOutcomeBuilder.valuesYamlContent(valuesYamlContent);
          executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.AWS_SAM_VALUES_YAML_DATA_OUTCOME,
              awsSamValuesYamlDataOutcomeBuilder.build(), StepOutcomeGroup.STEP.name());
        }
      }
    }
  }

  public PluginCreationResponseList getPluginInfoList(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;
    try {
      cdAbstractStepNode = YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }

    List<PluginCreationResponseWrapper> pluginCreationResponseWrapperList = new ArrayList<>();

    ManifestsOutcome manifestsOutcome =
        (ManifestsOutcome) outcomeService
            .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS))
            .getOutcome();

    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        getPluginCreationResponseWrapperForAwsSamDirectoryManifest(
            request, usedPorts, ambiance, cdAbstractStepNode, manifestsOutcome);

    usedPorts.addAll(pluginCreationResponseWrapper.getResponse().getPluginDetails().getPortUsedList());

    pluginCreationResponseWrapperList.add(pluginCreationResponseWrapper);

    // Values Yaml

    ValuesManifestOutcome valuesManifestOutcome =
        (ValuesManifestOutcome) getAwsSamValuesManifestOutcome(manifestsOutcome.values());

    if (valuesManifestOutcome != null) {
      PluginCreationResponseWrapper valuesPluginCreationResponseWrapper =
          getPluginCreationResponseWrapperForValuesManifest(
              request, usedPorts, ambiance, cdAbstractStepNode, valuesManifestOutcome);
      pluginCreationResponseWrapperList.add(valuesPluginCreationResponseWrapper);
    }
    return PluginCreationResponseList.newBuilder().addAllResponse(pluginCreationResponseWrapperList).build();
  }

  private PluginCreationResponseWrapper getPluginCreationResponseWrapperForValuesManifest(PluginCreationRequest request,
      Set<Integer> usedPorts, Ambiance ambiance, CdAbstractStepNode cdAbstractStepNode,
      ValuesManifestOutcome valuesManifestOutcome) {
    GitCloneStepInfo valuesGitCloneStepInfo =
        downloadManifestsCommonHelper.getGitCloneStepInfoFromManifestOutcome(valuesManifestOutcome);

    String valuesYamlPath = getValuesPathFromValuesManifestOutcome(valuesManifestOutcome);

    valuesGitCloneStepInfo.setOutputFilePathsContent(
        ParameterField.<List<String>>builder().value(Collections.singletonList(valuesYamlPath)).build());

    GitCloneStepNode valuesGitCloneStepNode = downloadManifestsCommonHelper.getGitCloneStepNode(
        valuesManifestOutcome, valuesGitCloneStepInfo, cdAbstractStepNode);

    PluginCreationRequest valuesPluginCreationRequest =
        request.toBuilder().setStepJsonNode(YamlUtils.writeYamlString(valuesGitCloneStepNode)).build();

    return gitClonePluginInfoProvider.getPluginInfo(valuesPluginCreationRequest, usedPorts, ambiance);
  }

  private PluginCreationResponseWrapper getPluginCreationResponseWrapperForAwsSamDirectoryManifest(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance, CdAbstractStepNode cdAbstractStepNode,
      ManifestsOutcome manifestsOutcome) {
    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome =
        (AwsSamDirectoryManifestOutcome) getAwsSamDirectoryManifestOutcome(manifestsOutcome.values());

    GitCloneStepInfo gitCloneStepInfo =
        downloadManifestsCommonHelper.getGitCloneStepInfoFromManifestOutcome(awsSamDirectoryManifestOutcome);

    GitCloneStepNode gitCloneStepNode = downloadManifestsCommonHelper.getGitCloneStepNode(
        awsSamDirectoryManifestOutcome, gitCloneStepInfo, cdAbstractStepNode);

    PluginCreationRequest pluginCreationRequest =
        request.toBuilder().setStepJsonNode(YamlUtils.writeYamlString(gitCloneStepNode)).build();

    return gitClonePluginInfoProvider.getPluginInfo(pluginCreationRequest, usedPorts, ambiance);
  }

  public ManifestOutcome getAwsSamDirectoryManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.AwsSamDirectory.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
  }

  public ManifestOutcome getAwsSamValuesManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.VALUES.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
  }

  public String getValuesPathFromValuesManifestOutcome(ValuesManifestOutcome valuesManifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) valuesManifestOutcome.getStore();
    return "/harness/" + valuesManifestOutcome.getIdentifier() + "/" + gitStoreConfig.getPaths().getValue().get(0);
  }
}
