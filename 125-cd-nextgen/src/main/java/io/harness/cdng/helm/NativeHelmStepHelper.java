/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.ManifestType.HELM_SUPPORTED_MANIFEST_TYPES;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.K8sHelmCommonStepHelper;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.helm.beans.NativeHelmExecutionPassThroughData;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.K8sStepPassThroughData;
import io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.helm.HelmCmdExecResponseNG;
import io.harness.delegate.task.helm.HelmCommandRequestNG;
import io.harness.delegate.task.helm.HelmFetchFileResult;
import io.harness.delegate.task.helm.HelmValuesFetchResponse;
import io.harness.delegate.task.localstore.LocalStoreFetchFilesResult;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.git.model.FetchFilesResult;
import io.harness.manifest.CustomSourceFile;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;
import software.wings.stencils.DefaultValue;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDP)
@Singleton
public class NativeHelmStepHelper extends K8sHelmCommonStepHelper {
  public static final String RELEASE_NAME = "Release Name";
  public static final String RELEASE_NAME_VALIDATION_REGEX =
      "[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*";
  public static final Pattern releaseNamePattern = Pattern.compile(RELEASE_NAME_VALIDATION_REGEX);
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Inject private CDStepHelper cdStepHelper;
  @DefaultValue("10") private int steadyStateTimeout; // Minutes

  public TaskChainResponse queueNativeHelmTask(StepElementParameters stepElementParameters,
      HelmCommandRequestNG helmCommandRequest, Ambiance ambiance,
      NativeHelmExecutionPassThroughData executionPassThroughData) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {helmCommandRequest})
                            .taskType(TaskType.HELM_COMMAND_TASK_NG.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();

    String taskName = TaskType.HELM_COMMAND_TASK_NG.getDisplayName() + " : " + helmCommandRequest.getCommandName();
    HelmSpecParameters helmSpecParameters = (HelmSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        helmSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(helmSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(true)
        .passThroughData(executionPassThroughData)
        .build();
  }

  public List<String> renderValues(
      ManifestOutcome manifestOutcome, Ambiance ambiance, List<String> valuesFileContents) {
    if (isEmpty(valuesFileContents)) {
      return emptyList();
    }

    return getValuesFileContents(ambiance, valuesFileContents);
  }

  public TaskChainResponse prepareValuesFetchTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      List<ValuesManifestOutcome> aggregatedValuesManifests, List<ManifestOutcome> manifestOutcomes,
      K8sStepPassThroughData k8sStepPassThroughData) {
    ManifestOutcome helmChartManifestOutcome = k8sStepPassThroughData.getManifestOutcome();
    StoreConfig storeConfig = extractStoreConfigFromManifestOutcome(helmChartManifestOutcome);

    if (shouldExecuteCustomFetchTask(storeConfig, manifestOutcomes)) {
      return prepareCustomFetchManifestAndValuesTaskChainResponse(
          storeConfig, ambiance, stepElementParameters, manifestOutcomes, k8sStepPassThroughData);
    }

    if (ManifestStoreType.isInGitSubset(storeConfig.getKind())
        || ManifestStoreType.HARNESS.equals(storeConfig.getKind())) {
      ValuesManifestOutcome valuesManifestOutcome = ValuesManifestOutcome.builder()
                                                        .identifier(helmChartManifestOutcome.getIdentifier())
                                                        .store(storeConfig)
                                                        .build();
      return prepareGitFetchValuesTaskChainResponse(ambiance, stepElementParameters, valuesManifestOutcome,
          aggregatedValuesManifests, k8sStepPassThroughData, storeConfig,
          isEmpty(k8sStepPassThroughData.getLocalStoreFileMapContents())
              && isEmpty(k8sStepPassThroughData.getManifestFiles()),
          true);
    }

    K8sStepPassThroughData deepCopyOfK8sPassThroughData =
        k8sStepPassThroughData.toBuilder().customFetchContent(emptyMap()).zippedManifestFileId("").build();
    return prepareHelmFetchValuesTaskChainResponse(
        ambiance, stepElementParameters, aggregatedValuesManifests, deepCopyOfK8sPassThroughData);
  }

  public TaskChainResponse startChainLink(
      NativeHelmStepExecutor helmStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters) {
    ManifestsOutcome manifestsOutcome = resolveManifestsOutcome(ambiance);
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    ExpressionEvaluatorUtils.updateExpressions(
        manifestsOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));
    cdStepHelper.validateManifestsOutcome(ambiance, manifestsOutcome);

    ManifestOutcome helmChartManifestOutcome = getHelmSupportedManifestOutcome(manifestsOutcome.values());

    return prepareHelmWithValuesManifests(helmStepExecutor, getOrderedManifestOutcome(manifestsOutcome.values()),
        helmChartManifestOutcome, ambiance, stepElementParameters, infrastructureOutcome);
  }

  protected ManifestsOutcome resolveManifestsOutcome(Ambiance ambiance) {
    OptionalOutcome manifestsOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    if (!manifestsOutcome.isFound()) {
      throw new InvalidRequestException("No manifests found.");
    }

    return (ManifestsOutcome) manifestsOutcome.getOutcome();
  }

  private TaskChainResponse prepareHelmWithValuesManifests(NativeHelmStepExecutor nativeHelmStepExecutor,
      List<ManifestOutcome> manifestOutcomes, ManifestOutcome helmChartManifestOutcome, Ambiance ambiance,
      StepElementParameters stepElementParameters, InfrastructureOutcome infrastructureOutcome) {
    List<ValuesManifestOutcome> aggregatedValuesManifests = getAggregatedValuesManifests(manifestOutcomes);
    List<ManifestFiles> manifestFiles = new ArrayList<>();
    Map<String, LocalStoreFetchFilesResult> localStoreFileMapContents = new HashMap<>();
    if (ManifestStoreType.HARNESS.equals(helmChartManifestOutcome.getStore().getKind())
        || isAnyLocalStore(aggregatedValuesManifests)) {
      localStoreFileMapContents.putAll(
          fetchFilesFromLocalStore(ambiance, helmChartManifestOutcome, aggregatedValuesManifests, manifestFiles));
    }

    K8sStepPassThroughData k8sStepPassThroughData = K8sStepPassThroughData.builder()
                                                        .manifestOutcome(helmChartManifestOutcome)
                                                        .infrastructure(infrastructureOutcome)
                                                        .localStoreFileMapContents(localStoreFileMapContents)
                                                        .manifestFiles(manifestFiles)
                                                        .build();
    return prepareValuesFetchTask(ambiance, stepElementParameters, aggregatedValuesManifests,
        valuesAndParamsManifestOutcomes(manifestOutcomes), k8sStepPassThroughData);
  }

  @VisibleForTesting
  public ManifestOutcome getHelmSupportedManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> helmManifests =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> HELM_SUPPORTED_MANIFEST_TYPES.contains(manifestOutcome.getType()))
            .collect(Collectors.toList());
    if (isEmpty(helmManifests)) {
      throw new InvalidRequestException(
          "Manifests are mandatory for Helm step. Select one from " + String.join(", ", HELM_SUPPORTED_MANIFEST_TYPES),
          USER);
    }
    if (helmManifests.size() > 1) {
      throw new InvalidRequestException(
          "There can be only a single manifest. Select one from " + String.join(", ", HELM_SUPPORTED_MANIFEST_TYPES),
          USER);
    }
    return helmManifests.get(0);
  }

  private List<ManifestOutcome> getOrderedManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    return manifestOutcomes.stream()
        .sorted(Comparator.comparingInt(ManifestOutcome::getOrder))
        .collect(Collectors.toCollection(LinkedList::new));
  }

  public TaskChainResponse executeNextLink(NativeHelmStepExecutor nativeHelmStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    K8sStepPassThroughData helmStepPassThroughData = (K8sStepPassThroughData) passThroughData;
    ManifestOutcome helmChartManifest = helmStepPassThroughData.getManifestOutcome();
    ResponseData responseData = responseDataSupplier.get();
    UnitProgressData unitProgressData = null;

    try {
      if (responseData instanceof GitFetchResponse) {
        unitProgressData = ((GitFetchResponse) responseData).getUnitProgressData();
        return handleGitFetchFilesResponse(responseData, nativeHelmStepExecutor, ambiance, stepElementParameters,
            helmStepPassThroughData, helmChartManifest);
      }

      if (responseData instanceof HelmValuesFetchResponse) {
        unitProgressData = ((HelmValuesFetchResponse) responseData).getUnitProgressData();
        return handleHelmValuesFetchResponse(responseData, nativeHelmStepExecutor, ambiance, stepElementParameters,
            helmStepPassThroughData, helmChartManifest);
      }

      if (responseData instanceof CustomManifestValuesFetchResponse) {
        unitProgressData = ((CustomManifestValuesFetchResponse) responseData).getUnitProgressData();
        return handleCustomFetchResponse(
            responseData, ambiance, stepElementParameters, helmStepPassThroughData, helmChartManifest);
      }

    } catch (Exception e) {
      return TaskChainResponse.builder()
          .chainEnd(true)
          .passThroughData(StepExceptionPassThroughData.builder()
                               .errorMessage(ExceptionUtils.getMessage(e))
                               .unitProgressData(cdStepHelper.completeUnitProgressData(
                                   unitProgressData, ambiance, ExceptionUtils.getMessage(e)))
                               .build())
          .build();
    }

    return nativeHelmStepExecutor.executeHelmTask(helmChartManifest, ambiance, stepElementParameters, emptyList(),
        NativeHelmExecutionPassThroughData.builder()
            .infrastructure(helmStepPassThroughData.getInfrastructure())
            .zippedManifestId(helmStepPassThroughData.getZippedManifestFileId())
            .manifestFiles(Arrays.asList(ManifestFiles.builder().build()))
            .build(),
        true, unitProgressData);
  }

  private TaskChainResponse handleGitFetchFilesResponse(ResponseData responseData,
      NativeHelmStepExecutor nativeHelmStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      K8sStepPassThroughData nativeHelmStepPassThroughData, ManifestOutcome helmChartManifest) {
    GitFetchResponse gitFetchResponse = (GitFetchResponse) responseData;
    if (gitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      GitFetchResponsePassThroughData gitFetchResponsePassThroughData =
          GitFetchResponsePassThroughData.builder()
              .errorMsg(gitFetchResponse.getErrorMessage())
              .unitProgressData(gitFetchResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().chainEnd(true).passThroughData(gitFetchResponsePassThroughData).build();
    }

    List<String> valuesFileContents = new ArrayList<>();
    String helmValuesYamlContent = nativeHelmStepPassThroughData.getHelmValuesFileContent();
    if (isNotEmpty(helmValuesYamlContent)) {
      valuesFileContents.add(helmValuesYamlContent);
    }
    Map<String, FetchFilesResult> gitFetchFilesResultMap = gitFetchResponse.getFilesFromMultipleRepo();
    Map<String, HelmFetchFileResult> helmChartValuesFetchFilesResultMap =
        nativeHelmStepPassThroughData.getHelmValuesFileMapContents();
    addValuesFileFromHelmChartManifest(
        helmChartValuesFetchFilesResultMap, valuesFileContents, helmChartManifest.getIdentifier());

    Map<String, Collection<CustomSourceFile>> customFetchContent =
        nativeHelmStepPassThroughData.getCustomFetchContent();
    addValuesFilesFromCustomFetch(customFetchContent, valuesFileContents, helmChartManifest.getIdentifier());

    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap =
        nativeHelmStepPassThroughData.getLocalStoreFileMapContents();
    if (isNotEmpty(gitFetchFilesResultMap) || isNotEmpty(helmChartValuesFetchFilesResultMap)
        || isNotEmpty(customFetchContent) || isNotEmpty(localStoreFetchFilesResultMap)) {
      valuesFileContents.addAll(
          getManifestFilesContents(gitFetchFilesResultMap, nativeHelmStepPassThroughData.getManifestOutcomeList(),
              helmChartValuesFetchFilesResultMap, customFetchContent, localStoreFetchFilesResultMap));
    }
    return nativeHelmStepExecutor.executeHelmTask(helmChartManifest, ambiance, stepElementParameters,
        valuesFileContents,
        NativeHelmExecutionPassThroughData.builder()
            .infrastructure(nativeHelmStepPassThroughData.getInfrastructure())
            .lastActiveUnitProgressData(gitFetchResponse.getUnitProgressData())
            .zippedManifestId(nativeHelmStepPassThroughData.getZippedManifestFileId())
            .manifestFiles(nativeHelmStepPassThroughData.getManifestFiles())
            .build(),
        false, gitFetchResponse.getUnitProgressData());
  }

  private TaskChainResponse handleHelmValuesFetchResponse(ResponseData responseData,
      NativeHelmStepExecutor nativeHelmStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      K8sStepPassThroughData nativeHelmStepPassThroughData, ManifestOutcome helmChartManifest) {
    HelmValuesFetchResponse helmValuesFetchResponse = (HelmValuesFetchResponse) responseData;
    if (helmValuesFetchResponse.getCommandExecutionStatus() != SUCCESS) {
      HelmValuesFetchResponsePassThroughData helmValuesFetchPassTroughData =
          HelmValuesFetchResponsePassThroughData.builder()
              .errorMsg(helmValuesFetchResponse.getErrorMessage())
              .unitProgressData(helmValuesFetchResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().chainEnd(true).passThroughData(helmValuesFetchPassTroughData).build();
    }

    String helmChartIdentifier = helmChartManifest.getIdentifier();
    Map<String, HelmFetchFileResult> helmValuesFetchFilesResultMap =
        helmValuesFetchResponse.getHelmChartValuesFileMapContent();
    String valuesFileContent = helmValuesFetchResponse.getValuesFileContent();
    if (isNotEmpty(valuesFileContent)) {
      helmValuesFetchFilesResultMap = new HashMap<>();
      helmValuesFetchFilesResultMap.put(helmChartIdentifier,
          HelmFetchFileResult.builder().valuesFileContents(new ArrayList<>(Arrays.asList(valuesFileContent))).build());
    }

    List<ValuesManifestOutcome> aggregatedValuesManifest = new ArrayList<>();
    aggregatedValuesManifest.addAll(nativeHelmStepPassThroughData.getValuesManifestOutcomes());
    List<ManifestOutcome> stepOverrides = getStepLevelManifestOutcomes(stepElementParameters);
    if (!isEmpty(stepOverrides)) {
      for (ManifestOutcome manifestOutcome : stepOverrides) {
        aggregatedValuesManifest.add((ValuesManifestOutcome) manifestOutcome);
      }
    }
    if (shouldExecuteGitFetchTask(aggregatedValuesManifest)) {
      return executeValuesFetchTask(ambiance, stepElementParameters, aggregatedValuesManifest,
          helmValuesFetchResponse.getHelmChartValuesFileMapContent(), nativeHelmStepPassThroughData, true);
    } else {
      List<String> valuesFileContents = new ArrayList<>();
      addValuesFileFromHelmChartManifest(helmValuesFetchFilesResultMap, valuesFileContents, helmChartIdentifier);
      return nativeHelmStepExecutor.executeHelmTask(helmChartManifest, ambiance, stepElementParameters,
          valuesFileContents,
          NativeHelmExecutionPassThroughData.builder()
              .infrastructure(nativeHelmStepPassThroughData.getInfrastructure())
              .lastActiveUnitProgressData(helmValuesFetchResponse.getUnitProgressData())
              .zippedManifestId(nativeHelmStepPassThroughData.getZippedManifestFileId())
              .manifestFiles(nativeHelmStepPassThroughData.getManifestFiles())
              .build(),
          false, helmValuesFetchResponse.getUnitProgressData());
    }
  }
  private TaskChainResponse handleCustomFetchResponse(ResponseData responseData, Ambiance ambiance,
      StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData,
      ManifestOutcome k8sManifest) {
    CustomManifestValuesFetchResponse customManifestValuesFetchResponse =
        (CustomManifestValuesFetchResponse) responseData;
    if (customManifestValuesFetchResponse.getCommandExecutionStatus() != SUCCESS) {
      CustomFetchResponsePassThroughData customFetchResponsePassThroughData =
          CustomFetchResponsePassThroughData.builder()
              .errorMsg(customManifestValuesFetchResponse.getErrorMessage())
              .unitProgressData(customManifestValuesFetchResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().chainEnd(true).passThroughData(customFetchResponsePassThroughData).build();
    }

    K8sStepPassThroughData updatedK8sStepPassThroughData =
        k8sStepPassThroughData.toBuilder()
            .customFetchContent(customManifestValuesFetchResponse.getValuesFilesContentMap())
            .zippedManifestFileId(customManifestValuesFetchResponse.getZippedManifestFileId())
            .build();

    if (ManifestStoreType.CUSTOM_REMOTE.equals(k8sManifest.getStore().getKind())) {
      List<ValuesManifestOutcome> aggregatedValuesManifest = new ArrayList<>();

      for (ValuesManifestOutcome valuesManifestOutcome : k8sStepPassThroughData.getValuesManifestOutcomes()) {
        if (ManifestStoreType.isInGitSubset(valuesManifestOutcome.getStore().getKind())) {
          aggregatedValuesManifest.add(valuesManifestOutcome);
        }
      }

      List<ManifestOutcome> stepOverrides = getStepLevelManifestOutcomes(stepElementParameters);
      if (!isEmpty(stepOverrides)) {
        for (ManifestOutcome manifestOutcome : stepOverrides) {
          aggregatedValuesManifest.add((ValuesManifestOutcome) manifestOutcome);
        }
      }

      return executeValuesFetchTask(
          ambiance, stepElementParameters, aggregatedValuesManifest, emptyMap(), updatedK8sStepPassThroughData, true);
    }

    if (ManifestType.HelmChart.equals(k8sManifest.getType())) {
      return prepareHelmFetchValuesTaskChainResponse(ambiance, stepElementParameters,
          k8sStepPassThroughData.getValuesManifestOutcomes(), updatedK8sStepPassThroughData);
    }

    StoreConfig storeConfig = extractStoreConfigFromManifestOutcome(k8sManifest);

    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().identifier(k8sManifest.getIdentifier()).store(storeConfig).build();

    return prepareGitFetchValuesTaskChainResponse(ambiance, stepElementParameters, valuesManifestOutcome,
        k8sStepPassThroughData.getValuesManifestOutcomes(), updatedK8sStepPassThroughData, storeConfig, false, true);
  }

  public static String getErrorMessage(HelmCmdExecResponseNG helmCmdExecResponseNG) {
    return helmCmdExecResponseNG.getErrorMessage() == null ? "" : helmCmdExecResponseNG.getErrorMessage();
  }

  public static StepResponseBuilder getFailureResponseBuilder(
      HelmCmdExecResponseNG helmCmdExecResponseNG, StepResponseBuilder stepResponseBuilder) {
    stepResponseBuilder.status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .setErrorMessage(NativeHelmStepHelper.getErrorMessage(helmCmdExecResponseNG))
                         .build());
    return stepResponseBuilder;
  }

  public StepResponse handleTaskException(
      Ambiance ambiance, NativeHelmExecutionPassThroughData executionPassThroughData, Exception e) throws Exception {
    // Trying to figure out if exception is coming from helm task or it is an exception from delegate service.
    // In the second case we need to close log stream and provide unit progress data as part of response
    if (ExceptionUtils.cause(TaskNGDataException.class, e) != null) {
      throw e;
    }
    UnitProgressData unitProgressData = cdStepHelper.completeUnitProgressData(
        executionPassThroughData.getLastActiveUnitProgressData(), ambiance, ExceptionUtils.getMessage(e));

    FailureData failureData = FailureData.newBuilder()
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .setLevel(Level.ERROR.name())
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(emptyIfNull(ExceptionUtils.getMessage(e)))
                                  .build();

    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .addAllFailureTypes(failureData.getFailureTypesList())
                         .setErrorMessage(failureData.getMessage())
                         .addFailureData(failureData)
                         .build())
        .build();
  }

  public void publishReleaseNameStepDetails(Ambiance ambiance, String releaseName) {
    if (isNotEmpty(releaseName)) {
      sdkGraphVisualizationDataService.publishStepDetailInformation(
          ambiance, NativeHelmReleaseDetailsInfo.builder().releaseName(releaseName).build(), RELEASE_NAME);
    }
  }

  public void addValuesFileFromHelmChartManifest(Map<String, HelmFetchFileResult> helmChartValuesFilesResultMap,
      List<String> valuesFileContents, String helmChartIdentifier) {
    if (isNotEmpty(helmChartValuesFilesResultMap) && helmChartValuesFilesResultMap.containsKey(helmChartIdentifier)) {
      List<String> baseValuesFileContent =
          helmChartValuesFilesResultMap.get(helmChartIdentifier).getValuesFileContents();
      if (isNotEmpty(baseValuesFileContent)) {
        valuesFileContents.addAll(baseValuesFileContent);
      }
    }
  }
}
