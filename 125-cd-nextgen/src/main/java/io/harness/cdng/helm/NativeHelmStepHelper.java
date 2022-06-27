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
import static io.harness.k8s.manifest.ManifestHelper.getValuesYamlGitFilePath;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static software.wings.beans.appmanifest.ManifestFile.VALUES_YAML_KEY;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.helm.beans.NativeHelmExecutionPassThroughData;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.HelmManifestCommandFlag;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.helm.HelmCmdExecResponseNG;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.delegate.task.helm.HelmCommandRequestNG;
import io.harness.delegate.task.helm.HelmFetchFileConfig;
import io.harness.delegate.task.helm.HelmFetchFileResult;
import io.harness.delegate.task.helm.HelmValuesFetchRequest;
import io.harness.delegate.task.helm.HelmValuesFetchResponse;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.git.model.FetchFilesResult;
import io.harness.helm.HelmSubCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
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
public class NativeHelmStepHelper extends CDStepHelper {
  public static final String RELEASE_NAME = "Release Name";
  public static final String RELEASE_NAME_VALIDATION_REGEX =
      "[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*";
  public static final Pattern releaseNamePattern = Pattern.compile(RELEASE_NAME_VALIDATION_REGEX);
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @DefaultValue("10") private int steadyStateTimeout; // Minutes

  public ManifestDelegateConfig getManifestDelegateConfig(ManifestOutcome manifestOutcome, Ambiance ambiance) {
    if (ManifestType.HelmChart.equals(manifestOutcome.getType())) {
      HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestOutcome;
      String chartName = getParameterFieldValue(helmChartManifestOutcome.getChartName());
      HelmVersion helmVersion =
          getHelmVersionBasedOnFF(helmChartManifestOutcome.getHelmVersion(), AmbianceUtils.getAccountId(ambiance));
      return HelmChartManifestDelegateConfig.builder()
          .storeDelegateConfig(getStoreDelegateConfig(
              helmChartManifestOutcome.getStore(), ambiance, manifestOutcome, manifestOutcome.getType() + " manifest"))
          .chartName(chartName)
          .chartVersion(getParameterFieldValue(helmChartManifestOutcome.getChartVersion()))
          .helmVersion(helmVersion)
          .helmCommandFlag(getDelegateHelmCommandFlag(helmChartManifestOutcome.getCommandFlags()))
          .useRepoFlags(helmVersion != HelmVersion.V2
              && cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.USE_HELM_REPO_FLAGS))
          .checkIncorrectChartVersion(cdFeatureFlagHelper.isEnabled(
              AmbianceUtils.getAccountId(ambiance), FeatureName.HELM_CHART_VERSION_STRICT_MATCH))
          .deleteRepoCacheDir(helmVersion != HelmVersion.V2
              && cdFeatureFlagHelper.isEnabled(
                  AmbianceUtils.getAccountId(ambiance), FeatureName.DELETE_HELM_REPO_CACHE_DIR))
          .build();
    }

    throw new UnsupportedOperationException(format("Unsupported Manifest type: [%s]", manifestOutcome.getType()));
  }

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

  public TaskChainResponse executeValuesFetchTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      InfrastructureOutcome infrastructure, ManifestOutcome helmChartManifestOutcome,
      List<ValuesManifestOutcome> aggregatedValuesManifests,
      Map<String, HelmFetchFileResult> helmChartValuesFileMapContent) {
    List<GitFetchFilesConfig> gitFetchFilesConfigs =
        mapValuesManifestToGitFetchFileConfig(aggregatedValuesManifests, ambiance);
    NativeHelmStepPassThroughData nativeHelmStepPassThroughData =
        NativeHelmStepPassThroughData.builder()
            .helmChartManifestOutcome(helmChartManifestOutcome)
            .manifestOutcomeList(new ArrayList<>(aggregatedValuesManifests))
            .infrastructure(infrastructure)
            .helmValuesFileMapContents(helmChartValuesFileMapContent)
            .build();

    return getGitFetchFileTaskChainResponse(
        ambiance, gitFetchFilesConfigs, stepElementParameters, nativeHelmStepPassThroughData, false);
  }

  public TaskChainResponse prepareValuesFetchTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      InfrastructureOutcome infrastructure, ManifestOutcome helmChartManifestOutcome,
      List<ValuesManifestOutcome> aggregatedValuesManifests) {
    StoreConfig storeConfig = extractStoreConfigFromHelmChartManifestOutcome(helmChartManifestOutcome);
    if (ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      ValuesManifestOutcome valuesManifestOutcome = ValuesManifestOutcome.builder()
                                                        .identifier(helmChartManifestOutcome.getIdentifier())
                                                        .store(storeConfig)
                                                        .build();
      return prepareGitFetchValuesTaskChainResponse(storeConfig, ambiance, stepElementParameters, infrastructure,
          helmChartManifestOutcome, valuesManifestOutcome, aggregatedValuesManifests);
    }

    return prepareHelmFetchValuesTaskChainResponse(
        ambiance, stepElementParameters, infrastructure, helmChartManifestOutcome, aggregatedValuesManifests);
  }

  private TaskChainResponse prepareGitFetchValuesTaskChainResponse(StoreConfig storeConfig, Ambiance ambiance,
      StepElementParameters stepElementParameters, InfrastructureOutcome infrastructure,
      ManifestOutcome helmChartManifestOutcome, ValuesManifestOutcome valuesManifestOutcome,
      List<ValuesManifestOutcome> aggregatedValuesManifests) {
    LinkedList<ValuesManifestOutcome> orderedValuesManifests = new LinkedList<>(aggregatedValuesManifests);
    List<GitFetchFilesConfig> gitFetchFilesConfigs =
        mapValuesManifestToGitFetchFileConfig(aggregatedValuesManifests, ambiance);
    List<ManifestOutcome> stepOverrides = getStepLevelManifestOutcomes(stepElementParameters);

    if (!isEmpty(stepOverrides)) {
      for (ManifestOutcome manifestOutcome : stepOverrides) {
        if (ManifestStoreType.isInGitSubset(manifestOutcome.getStore().getKind())) {
          gitFetchFilesConfigs.add(getGitFetchFilesConfig(
              ambiance, manifestOutcome.getStore(), manifestOutcome.getIdentifier(), manifestOutcome));
          orderedValuesManifests.add((ValuesManifestOutcome) manifestOutcome);
        } else if (ManifestStoreType.INLINE.equals(manifestOutcome.getStore().getKind())) {
          orderedValuesManifests.add((ValuesManifestOutcome) manifestOutcome);
        }
      }
    }

    gitFetchFilesConfigs.addAll(
        mapHelmValuesManifestToGitFetchFileConfig(valuesManifestOutcome, ambiance, helmChartManifestOutcome));
    List<GitFetchFilesConfig> gitFetchFileConfigFromInheritFromManifest =
        mapValuesManifestsToGitFetchFileConfig(ambiance, aggregatedValuesManifests, helmChartManifestOutcome);
    if (isNotEmpty(gitFetchFileConfigFromInheritFromManifest)) {
      gitFetchFilesConfigs.addAll(gitFetchFileConfigFromInheritFromManifest);
    }
    orderedValuesManifests.addFirst(valuesManifestOutcome);

    NativeHelmStepPassThroughData nativeHelmStepPassThroughData =
        NativeHelmStepPassThroughData.builder()
            .helmChartManifestOutcome(helmChartManifestOutcome)
            .manifestOutcomeList(new ArrayList<>(orderedValuesManifests))
            .infrastructure(infrastructure)
            .build();

    return getGitFetchFileTaskChainResponse(
        ambiance, gitFetchFilesConfigs, stepElementParameters, nativeHelmStepPassThroughData, true);
  }

  private List<GitFetchFilesConfig> mapHelmValuesManifestToGitFetchFileConfig(
      ValuesManifestOutcome valuesManifestOutcome, Ambiance ambiance, ManifestOutcome helmChartManifestOutcome) {
    String validationMessage = format("Values YAML with Id [%s]", valuesManifestOutcome.getIdentifier());
    return getValuesGitFetchFilesConfig(ambiance, valuesManifestOutcome.getIdentifier(),
        valuesManifestOutcome.getStore(), validationMessage, helmChartManifestOutcome);
  }

  private List<GitFetchFilesConfig> mapValuesManifestToGitFetchFileConfig(
      List<ValuesManifestOutcome> aggregatedValuesManifests, Ambiance ambiance) {
    return aggregatedValuesManifests.stream()
        .filter(valuesManifestOutcome -> ManifestStoreType.isInGitSubset(valuesManifestOutcome.getStore().getKind()))
        .map(valuesManifestOutcome
            -> getGitFetchFilesConfig(ambiance, valuesManifestOutcome.getStore(),
                format("Values YAML with Id [%s]", valuesManifestOutcome.getIdentifier()), valuesManifestOutcome))
        .collect(Collectors.toList());
  }

  private List<GitFetchFilesConfig> mapValuesManifestsToGitFetchFileConfig(Ambiance ambiance,
      List<ValuesManifestOutcome> aggregatedValuesManifests, ManifestOutcome helmChartManifestOutcome) {
    if (isEmpty(aggregatedValuesManifests)) {
      return emptyList();
    }
    return aggregatedValuesManifests.stream()
        .filter(valuesManifestOutcome
            -> ManifestStoreType.InheritFromManifest.equals(valuesManifestOutcome.getStore().getKind()))
        .map(valuesManifestOutcome
            -> getPathsFromInheritFromManifestStoreConfig(ambiance,
                format("Values YAML with Id [%s]", valuesManifestOutcome.getIdentifier()), valuesManifestOutcome,
                (GitStoreConfig) helmChartManifestOutcome.getStore()))
        .collect(Collectors.toList());
  }

  private TaskChainResponse prepareHelmFetchValuesTaskChainResponse(Ambiance ambiance,
      StepElementParameters stepElementParameters, InfrastructureOutcome infrastructure,
      ManifestOutcome helmChartManifestOutcome, List<ValuesManifestOutcome> aggregatedValuesManifests) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    HelmChartManifestDelegateConfig helmManifest =
        (HelmChartManifestDelegateConfig) getManifestDelegateConfig(helmChartManifestOutcome, ambiance);

    List<HelmFetchFileConfig> helmFetchFileConfigList =
        mapHelmChartManifestsToHelmFetchFileConfig(helmChartManifestOutcome.getIdentifier(),
            getParameterFieldValue(((HelmChartManifestOutcome) helmChartManifestOutcome).getValuesPaths()),
            helmChartManifestOutcome.getType());

    helmFetchFileConfigList.addAll(mapValuesManifestsToHelmFetchFileConfig(aggregatedValuesManifests));
    HelmValuesFetchRequest helmValuesFetchRequest = HelmValuesFetchRequest.builder()
                                                        .accountId(accountId)
                                                        .helmChartManifestDelegateConfig(helmManifest)
                                                        .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                                        .closeLogStream(!isAnyRemoteStore(aggregatedValuesManifests))
                                                        .helmFetchFileConfigList(helmFetchFileConfigList)
                                                        .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.HELM_VALUES_FETCH_NG.name())
                                  .parameters(new Object[] {helmValuesFetchRequest})
                                  .build();

    String taskName = TaskType.HELM_VALUES_FETCH_NG.getDisplayName();

    HelmSpecParameters helmSpecParameters = (HelmSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        helmSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(helmSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));

    NativeHelmStepPassThroughData nativeHelmStepPassThroughData =
        NativeHelmStepPassThroughData.builder()
            .helmChartManifestOutcome(helmChartManifestOutcome)
            .manifestOutcomeList(new ArrayList<>(aggregatedValuesManifests))
            .infrastructure(infrastructure)
            .build();

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(nativeHelmStepPassThroughData)
        .build();
  }

  private TaskChainResponse getGitFetchFileTaskChainResponse(Ambiance ambiance,
      List<GitFetchFilesConfig> gitFetchFilesConfigs, StepElementParameters stepElementParameters,
      NativeHelmStepPassThroughData nativeHelmStepPassThroughData, boolean shouldOpenLogStream) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    GitFetchRequest gitFetchRequest = GitFetchRequest.builder()
                                          .gitFetchFilesConfigs(gitFetchFilesConfigs)
                                          .shouldOpenLogStream(shouldOpenLogStream)
                                          .closeLogStream(true)
                                          .accountId(accountId)
                                          .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.GIT_FETCH_NEXT_GEN_TASK.name())
                                  .parameters(new Object[] {gitFetchRequest})
                                  .build();

    String taskName = TaskType.GIT_FETCH_NEXT_GEN_TASK.getDisplayName();
    HelmSpecParameters helmSpecParameters = (HelmSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        helmSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(helmSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(nativeHelmStepPassThroughData)
        .build();
  }

  private StoreConfig extractStoreConfigFromHelmChartManifestOutcome(ManifestOutcome manifestOutcome) {
    if (manifestOutcome.getType() == ManifestType.HelmChart) {
      HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestOutcome;
      return helmChartManifestOutcome.getStore();
    }

    throw new UnsupportedOperationException(format("Unsupported Manifest type: [%s]", manifestOutcome.getType()));
  }

  private List<GitFetchFilesConfig> getValuesGitFetchFilesConfig(Ambiance ambiance, String identifier,
      StoreConfig store, String validationMessage, ManifestOutcome manifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) store;
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = getConnector(connectorId, ambiance);
    validateManifest(store.getKind(), connectorDTO, validationMessage);

    HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestOutcome;
    List<GitFetchFilesConfig> gitFetchFilesConfigList = new ArrayList<>();
    String folderPath = getParameterFieldValue(gitStoreConfig.getFolderPath());
    populateGitFetchFilesConfigListWithValuesPaths(gitFetchFilesConfigList, gitStoreConfig, manifestOutcome,
        connectorDTO, ambiance, identifier, true, Arrays.asList(getValuesYamlGitFilePath(folderPath, VALUES_YAML_KEY)));
    populateGitFetchFilesConfigListWithValuesPaths(gitFetchFilesConfigList, gitStoreConfig, manifestOutcome,
        connectorDTO, ambiance, identifier, false, getParameterFieldValue(helmChartManifestOutcome.getValuesPaths()));
    return gitFetchFilesConfigList;
  }

  public TaskChainResponse startChainLink(
      NativeHelmStepExecutor helmStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters) {
    ManifestsOutcome manifestsOutcome = resolveManifestsOutcome(ambiance);
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    ExpressionEvaluatorUtils.updateExpressions(
        manifestsOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));
    validateManifestsOutcome(ambiance, manifestsOutcome);

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
    List<ValuesManifestOutcome> aggregatedValuesManifests = CDStepHelper.getAggregatedValuesManifests(manifestOutcomes);

    if (isNotEmpty(aggregatedValuesManifests) && !isAnyRemoteStore(aggregatedValuesManifests)) {
      List<String> valuesFileContentsForLocalStore = getValuesFileContentsForLocalStore(aggregatedValuesManifests);
      return nativeHelmStepExecutor.executeHelmTask(helmChartManifestOutcome, ambiance, stepElementParameters,
          valuesFileContentsForLocalStore,
          NativeHelmExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true, null);
    }

    return prepareValuesFetchTask(
        ambiance, stepElementParameters, infrastructureOutcome, helmChartManifestOutcome, aggregatedValuesManifests);
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

  private List<String> getValuesFileContentsForLocalStore(List<ValuesManifestOutcome> aggregatedValuesManifests) {
    // TODO: implement when local store is available
    return emptyList();
  }

  private List<ManifestOutcome> getOrderedManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    return manifestOutcomes.stream()
        .sorted(Comparator.comparingInt(ManifestOutcome::getOrder))
        .collect(Collectors.toCollection(LinkedList::new));
  }

  private boolean isAnyRemoteStore(@NotEmpty List<ValuesManifestOutcome> aggregatedValuesManifests) {
    return aggregatedValuesManifests.stream().anyMatch(valuesManifest
        -> ManifestStoreType.isInGitSubset(valuesManifest.getStore().getKind())
            || ManifestStoreType.InheritFromManifest.equals(valuesManifest.getStore().getKind()));
  }

  public TaskChainResponse executeNextLink(NativeHelmStepExecutor nativeHelmStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    NativeHelmStepPassThroughData helmStepPassThroughData = (NativeHelmStepPassThroughData) passThroughData;
    ManifestOutcome helmChartManifest = helmStepPassThroughData.getHelmChartManifestOutcome();
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

    } catch (Exception e) {
      return TaskChainResponse.builder()
          .chainEnd(true)
          .passThroughData(
              StepExceptionPassThroughData.builder()
                  .errorMessage(ExceptionUtils.getMessage(e))
                  .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, ExceptionUtils.getMessage(e)))
                  .build())
          .build();
    }

    return nativeHelmStepExecutor.executeHelmTask(helmChartManifest, ambiance, stepElementParameters, emptyList(),
        NativeHelmExecutionPassThroughData.builder()
            .infrastructure(helmStepPassThroughData.getInfrastructure())
            .build(),
        true, unitProgressData);
  }

  private TaskChainResponse handleGitFetchFilesResponse(ResponseData responseData,
      NativeHelmStepExecutor nativeHelmStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      NativeHelmStepPassThroughData nativeHelmStepPassThroughData, ManifestOutcome helmChartManifest) {
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

    if (isNotEmpty(gitFetchFilesResultMap) || isNotEmpty(helmChartValuesFetchFilesResultMap)) {
      valuesFileContents.addAll(getManifestFilesContents(gitFetchFilesResultMap,
          nativeHelmStepPassThroughData.getManifestOutcomeList(), helmChartValuesFetchFilesResultMap));
    }
    return nativeHelmStepExecutor.executeHelmTask(helmChartManifest, ambiance, stepElementParameters,
        valuesFileContents,
        NativeHelmExecutionPassThroughData.builder()
            .infrastructure(nativeHelmStepPassThroughData.getInfrastructure())
            .lastActiveUnitProgressData(gitFetchResponse.getUnitProgressData())
            .build(),
        false, gitFetchResponse.getUnitProgressData());
  }

  private TaskChainResponse handleHelmValuesFetchResponse(ResponseData responseData,
      NativeHelmStepExecutor nativeHelmStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      NativeHelmStepPassThroughData nativeHelmStepPassThroughData, ManifestOutcome helmChartManifest) {
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
    if (isNotEmpty(aggregatedValuesManifest)) {
      return executeValuesFetchTask(ambiance, stepElementParameters, nativeHelmStepPassThroughData.getInfrastructure(),
          nativeHelmStepPassThroughData.getHelmChartManifestOutcome(), aggregatedValuesManifest,
          helmValuesFetchResponse.getHelmChartValuesFileMapContent());
    } else {
      List<String> valuesFileContents = new ArrayList<>();
      addValuesFileFromHelmChartManifest(helmValuesFetchFilesResultMap, valuesFileContents, helmChartIdentifier);
      return nativeHelmStepExecutor.executeHelmTask(helmChartManifest, ambiance, stepElementParameters,
          valuesFileContents,
          NativeHelmExecutionPassThroughData.builder()
              .infrastructure(nativeHelmStepPassThroughData.getInfrastructure())
              .lastActiveUnitProgressData(helmValuesFetchResponse.getUnitProgressData())
              .build(),
          false, helmValuesFetchResponse.getUnitProgressData());
    }
  }

  private HelmCommandFlag getDelegateHelmCommandFlag(List<HelmManifestCommandFlag> commandFlags) {
    if (commandFlags == null) {
      return HelmCommandFlag.builder().valueMap(new HashMap<>()).build();
    }

    Map<HelmSubCommandType, String> commandsValueMap = new HashMap<>();
    for (HelmManifestCommandFlag commandFlag : commandFlags) {
      commandsValueMap.put(commandFlag.getCommandType().getSubCommandType(), commandFlag.getFlag().getValue());
    }

    return HelmCommandFlag.builder().valueMap(commandsValueMap).build();
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
    UnitProgressData unitProgressData = completeUnitProgressData(
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

  private void populateGitFetchFilesConfigListWithValuesPaths(List<GitFetchFilesConfig> gitFetchFilesConfigList,
      GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome, ConnectorInfoDTO connectorDTO, Ambiance ambiance,
      String identifier, boolean succeedIfFileNotFound, List<String> gitFileValuesPaths) {
    if (isNotEmpty(gitFileValuesPaths)) {
      GitStoreDelegateConfig gitStoreDelegateConfig =
          getGitStoreDelegateConfig(gitStoreConfig, connectorDTO, manifestOutcome, gitFileValuesPaths, ambiance);
      gitFetchFilesConfigList.add(getGitFetchFilesConfigFromBuilder(
          identifier, manifestOutcome.getType(), succeedIfFileNotFound, gitStoreDelegateConfig));
    }
  }
}
