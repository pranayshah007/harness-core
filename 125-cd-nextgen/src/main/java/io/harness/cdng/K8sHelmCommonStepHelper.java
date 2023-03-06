/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.filestore.utils.FileStoreNodeUtils.mapFileNodes;
import static io.harness.k8s.manifest.ManifestHelper.getValuesYamlGitFilePath;
import static io.harness.k8s.manifest.ManifestHelper.values_filename;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.appmanifest.ManifestFile.VALUES_YAML_KEY;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.beans.FeatureName;
import io.harness.beans.FileReference;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.helm.HelmSpecParameters;
import io.harness.cdng.k8s.K8sApplyStepParameters;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.cdng.k8s.K8sSpecParameters;
import io.harness.cdng.k8s.K8sStepPassThroughData;
import io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.mappers.ManifestOutcomeMapper;
import io.harness.cdng.manifest.yaml.CustomRemoteStoreConfig;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.HelmManifestCommandFlag;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.InheritFromManifestStoreConfig;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.OciHelmChartConfig;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftParamManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.CustomRemoteStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.LocalFileStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfigType;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.delegate.task.helm.HelmFetchFileConfig;
import io.harness.delegate.task.helm.HelmFetchFileResult;
import io.harness.delegate.task.helm.HelmValuesFetchRequest;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sManifestDelegateConfig;
import io.harness.delegate.task.k8s.KustomizeManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.delegate.task.k8s.OpenshiftManifestDelegateConfig;
import io.harness.delegate.task.localstore.LocalStoreFetchFilesResult;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.delegate.task.manifests.request.CustomManifestFetchConfig;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.helm.HelmSubCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.manifest.CustomManifestSource;
import io.harness.manifest.CustomSourceFile;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.TaskType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.hibernate.validator.constraints.NotEmpty;

public class K8sHelmCommonStepHelper {
  private static final Set<String> VALUES_YAML_SUPPORTED_MANIFEST_TYPES =
      ImmutableSet.of(ManifestType.K8Manifest, ManifestType.HelmChart);
  protected static final Set<String> HELM_CHART_REPO_STORE_TYPES =
      ImmutableSet.of(ManifestStoreType.S3, ManifestStoreType.GCS, ManifestStoreType.HTTP, ManifestStoreType.OCI);
  protected static final String SUB_CHARTS_FOLDER = "charts/";
  @Inject protected CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private K8sEntityHelper k8sEntityHelper;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private FileStoreService fileStoreService;
  @Inject protected OutcomeService outcomeService;
  @Inject @Named("referenceFalseKryoSerializer") protected KryoSerializer referenceFalseKryoSerializer;
  @Inject protected StepHelper stepHelper;
  @Inject protected CDStepHelper cdStepHelper;

  public static final String MANIFEST_OUTCOME_INCOMPATIBLE_ERROR_MESSAGE =
      "Incompatible manifest store type. Cannot convert manifest outcome to HelmChartManifestOutcome.";
  public static final String RELEASE_NAME_VALIDATION_REGEX =
      "[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*";

  public static final Pattern releaseNamePattern = Pattern.compile(RELEASE_NAME_VALIDATION_REGEX);

  protected List<ManifestOutcome> valuesAndParamsManifestOutcomes(List<ManifestOutcome> manifestOutcomeList) {
    return manifestOutcomeList.stream()
        .filter(manifestOutcome
            -> ManifestType.VALUES.equals(manifestOutcome.getType())
                || ManifestType.OpenshiftParam.equals(manifestOutcome.getType()))
        .collect(Collectors.toList());
  }

  protected TaskChainResponse prepareGitFetchValuesTaskChainResponse(Ambiance ambiance,
      StepElementParameters stepElementParameters, ValuesManifestOutcome valuesManifestOutcome,
      List<ValuesManifestOutcome> aggregatedValuesManifests, K8sStepPassThroughData k8sStepPassThroughData,
      StoreConfig storeConfig) {
    LinkedList<ValuesManifestOutcome> orderedValuesManifests = new LinkedList<>(aggregatedValuesManifests);
    orderedValuesManifests.addFirst(valuesManifestOutcome);
    List<GitFetchFilesConfig> gitFetchFilesConfigs =
        mapValuesManifestToGitFetchFileConfig(aggregatedValuesManifests, ambiance);
    ManifestOutcome k8sManifestOutcome = k8sStepPassThroughData.getManifestOutcome();

    if (ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      gitFetchFilesConfigs.addAll(
          mapK8sOrHelmValuesManifestToGitFetchFileConfig(valuesManifestOutcome, ambiance, k8sManifestOutcome));
    }

    List<GitFetchFilesConfig> gitFetchFileConfigFromInheritFromManifest =
        mapValuesManifestsToGitFetchFileConfig(ambiance, aggregatedValuesManifests, k8sManifestOutcome);
    if (isNotEmpty(gitFetchFileConfigFromInheritFromManifest)) {
      gitFetchFilesConfigs.addAll(gitFetchFileConfigFromInheritFromManifest);
    }
    K8sStepPassThroughData updatedK8sStepPassThroughData =
        k8sStepPassThroughData.toBuilder().manifestOutcomeList(new ArrayList<>(orderedValuesManifests)).build();

    return getGitFetchFileTaskChainResponse(
        ambiance, gitFetchFilesConfigs, stepElementParameters, updatedK8sStepPassThroughData);
  }

  protected List<GitFetchFilesConfig> mapValuesManifestsToGitFetchFileConfig(
      Ambiance ambiance, List<ValuesManifestOutcome> aggregatedValuesManifests, ManifestOutcome k8sManifestOutcome) {
    if (isEmpty(aggregatedValuesManifests)) {
      return emptyList();
    }
    return aggregatedValuesManifests.stream()
        .filter(valuesManifestOutcome
            -> ManifestStoreType.InheritFromManifest.equals(valuesManifestOutcome.getStore().getKind()))
        .map(valuesManifestOutcome
            -> getPathsFromInheritFromManifestStoreConfig(ambiance,
                format("Values YAML with Id [%s]", valuesManifestOutcome.getIdentifier()), valuesManifestOutcome,
                (GitStoreConfig) k8sManifestOutcome.getStore()))
        .collect(Collectors.toList());
  }

  protected TaskChainResponse prepareCustomFetchManifestAndValuesTaskChainResponse(StoreConfig storeConfig,
      Ambiance ambiance, StepElementParameters stepElementParameters, List<ManifestOutcome> paramsOrValuesManifests,
      K8sStepPassThroughData k8sStepPassThroughData) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    ManifestOutcome manifestOutcome = k8sStepPassThroughData.getManifestOutcome();
    ParameterField<List<TaskSelectorYaml>> stepLevelSelectors = null;
    List<String> commandUnits = null;
    if (stepElementParameters.getSpec() instanceof K8sSpecParameters) {
      stepLevelSelectors = ((K8sSpecParameters) stepElementParameters.getSpec()).getDelegateSelectors();
      commandUnits = ((K8sSpecParameters) stepElementParameters.getSpec()).getCommandUnits();
    } else {
      stepLevelSelectors = ((HelmSpecParameters) stepElementParameters.getSpec()).getDelegateSelectors();
      commandUnits = ((HelmSpecParameters) stepElementParameters.getSpec()).getCommandUnits();
    }
    List<TaskSelectorYaml> delegateSelectors = new ArrayList<>();

    if (!isEmpty(stepLevelSelectors.getValue())) {
      delegateSelectors.addAll(getParameterFieldValue(stepLevelSelectors));
    }

    CustomManifestSource customManifestSource = null;

    List<CustomManifestFetchConfig> fetchFilesList = new ArrayList<>();

    for (ManifestOutcome valuesOrParamManifestOutcome : paramsOrValuesManifests) {
      if (ManifestStoreType.CUSTOM_REMOTE.equals(valuesOrParamManifestOutcome.getStore().getKind())) {
        CustomRemoteStoreConfig store = (CustomRemoteStoreConfig) valuesOrParamManifestOutcome.getStore();
        fetchFilesList.add(CustomManifestFetchConfig.builder()
                               .key(valuesOrParamManifestOutcome.getIdentifier())
                               .required(true)
                               .defaultSource(false)
                               .customManifestSource(CustomManifestSource.builder()
                                                         .script(store.getExtractionScript().getValue())
                                                         .filePaths(Arrays.asList(store.getFilePath().getValue()))
                                                         .accountId(accountId)
                                                         .build())
                               .build());
        if (!isEmpty(store.getDelegateSelectors().getValue())) {
          delegateSelectors.addAll(getParameterFieldValue(store.getDelegateSelectors()));
        }
      }
    }

    if (ManifestStoreType.CUSTOM_REMOTE.equals(storeConfig.getKind())) {
      CustomRemoteStoreConfig customRemoteStoreConfig = (CustomRemoteStoreConfig) storeConfig;

      customManifestSource = CustomManifestSource.builder()
                                 .script(customRemoteStoreConfig.getExtractionScript().getValue())
                                 .filePaths(Arrays.asList(customRemoteStoreConfig.getFilePath().getValue()))
                                 .accountId(accountId)
                                 .build();

      if (!isEmpty(customRemoteStoreConfig.getDelegateSelectors().getValue())) {
        delegateSelectors.addAll(getParameterFieldValue(customRemoteStoreConfig.getDelegateSelectors()));
      }

      List<String> manifestOverridePaths = getManifestOverridePaths(manifestOutcome);

      // adding override paths defined in the manifest
      if (!isEmpty(manifestOverridePaths)) {
        fetchFilesList.add(0,
            CustomManifestFetchConfig.builder()
                .key(manifestOutcome.getIdentifier())
                .required(true)
                .defaultSource(true)
                .customManifestSource(
                    CustomManifestSource.builder().filePaths(manifestOverridePaths).accountId(accountId).build())
                .build());
      }

      // adding default override path
      if (ManifestType.K8Manifest.equals(manifestOutcome.getType())
          || ManifestType.HelmChart.equals(manifestOutcome.getType())) {
        fetchFilesList.add(0,
            CustomManifestFetchConfig.builder()
                .key(manifestOutcome.getIdentifier())
                .required(false)
                .defaultSource(true)
                .customManifestSource(CustomManifestSource.builder()
                                          .filePaths(Arrays.asList(
                                              getValuesYamlGitFilePath(customRemoteStoreConfig.getFilePath().getValue(),
                                                  getDefaultOverridePath(manifestOutcome.getType()))))
                                          .accountId(accountId)
                                          .build())
                .build());
      }
    }

    CustomManifestValuesFetchParams customManifestValuesFetchRequest =
        CustomManifestValuesFetchParams.builder()
            .fetchFilesList(fetchFilesList)
            .activityId(ambiance.getStageExecutionId())
            .commandUnitName("Fetch Files")
            .accountId(accountId)
            .shouldOpenLogStream(k8sStepPassThroughData.getShouldOpenFetchFilesStream())
            .shouldCloseLogStream(k8sStepPassThroughData.isShouldCloseFetchFilesStream())
            .customManifestSource(customManifestSource)
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.CUSTOM_MANIFEST_VALUES_FETCH_TASK_NG.name())
                                  .parameters(new Object[] {customManifestValuesFetchRequest})
                                  .build();

    String taskName = TaskType.CUSTOM_MANIFEST_VALUES_FETCH_TASK_NG.getDisplayName();

    final TaskRequest taskRequest =
        TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer, commandUnits, taskName,
            TaskSelectorYaml.toTaskSelector(CollectionUtils.emptyIfNull(delegateSelectors)),
            stepHelper.getEnvironmentType(ambiance));

    K8sStepPassThroughData updatedK8sStepPassThroughData =
        k8sStepPassThroughData.toBuilder().manifestOutcomeList(new ArrayList<>(paramsOrValuesManifests)).build();

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(updatedK8sStepPassThroughData)
        .build();
  }

  protected TaskChainResponse getGitFetchFileTaskChainResponse(Ambiance ambiance,
      List<GitFetchFilesConfig> gitFetchFilesConfigs, StepElementParameters stepElementParameters,
      K8sStepPassThroughData k8sStepPassThroughData) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    GitFetchRequest gitFetchRequest = GitFetchRequest.builder()
                                          .gitFetchFilesConfigs(gitFetchFilesConfigs)
                                          .shouldOpenLogStream(k8sStepPassThroughData.getShouldOpenFetchFilesStream())
                                          .closeLogStream(k8sStepPassThroughData.isShouldCloseFetchFilesStream())
                                          .accountId(accountId)
                                          .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.GIT_FETCH_NEXT_GEN_TASK.name())
                                  .parameters(new Object[] {gitFetchRequest})
                                  .build();

    String taskName = TaskType.GIT_FETCH_NEXT_GEN_TASK.getDisplayName();
    ParameterField<List<TaskSelectorYaml>> stepLevelSelectors = null;
    List<String> commandUnits = null;
    if (stepElementParameters.getSpec() instanceof K8sSpecParameters) {
      stepLevelSelectors = ((K8sSpecParameters) stepElementParameters.getSpec()).getDelegateSelectors();
      commandUnits = ((K8sSpecParameters) stepElementParameters.getSpec()).getCommandUnits();
    } else {
      stepLevelSelectors = ((HelmSpecParameters) stepElementParameters.getSpec()).getDelegateSelectors();
      commandUnits = ((HelmSpecParameters) stepElementParameters.getSpec()).getCommandUnits();
    }
    final TaskRequest taskRequest =
        TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer, commandUnits, taskName,
            TaskSelectorYaml.toTaskSelector(CollectionUtils.emptyIfNull(getParameterFieldValue(stepLevelSelectors))),
            stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(k8sStepPassThroughData)
        .build();
  }

  public List<GitFetchFilesConfig> mapValuesManifestToGitFetchFileConfig(
      List<ValuesManifestOutcome> aggregatedValuesManifests, Ambiance ambiance) {
    return aggregatedValuesManifests.stream()
        .filter(valuesManifestOutcome -> ManifestStoreType.isInGitSubset(valuesManifestOutcome.getStore().getKind()))
        .map(valuesManifestOutcome
            -> getGitFetchFilesConfig(ambiance, valuesManifestOutcome.getStore(),
                format("Values YAML with Id [%s]", valuesManifestOutcome.getIdentifier()), valuesManifestOutcome))
        .collect(Collectors.toList());
  }
  public TaskChainResponse executeValuesFetchTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      List<ValuesManifestOutcome> aggregatedValuesManifests,
      Map<String, HelmFetchFileResult> helmChartValuesFileContentMap, K8sStepPassThroughData k8sStepPassThroughData) {
    List<GitFetchFilesConfig> gitFetchFilesConfigs =
        mapValuesManifestToGitFetchFileConfig(aggregatedValuesManifests, ambiance);
    K8sStepPassThroughData updatedK8sStepPassThroughData =
        k8sStepPassThroughData.toBuilder()
            .manifestOutcomeList(new ArrayList<>(aggregatedValuesManifests))
            .helmValuesFileMapContents(helmChartValuesFileContentMap)
            .build();

    return getGitFetchFileTaskChainResponse(
        ambiance, gitFetchFilesConfigs, stepElementParameters, updatedK8sStepPassThroughData);
  }

  protected TaskChainResponse prepareHelmFetchValuesTaskChainResponse(Ambiance ambiance,
      StepElementParameters stepElementParameters, List<ValuesManifestOutcome> aggregatedValuesManifests,
      K8sStepPassThroughData k8sStepPassThroughData) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    HelmChartManifestOutcome helmChartManifestOutcome =
        (HelmChartManifestOutcome) k8sStepPassThroughData.getManifestOutcome();
    HelmChartManifestDelegateConfig helmManifest = (HelmChartManifestDelegateConfig) getManifestDelegateConfig(
        k8sStepPassThroughData.getManifestOutcome(), ambiance);

    List<HelmFetchFileConfig> helmFetchFileConfigList = mapHelmChartManifestsToHelmFetchFileConfig(
        helmChartManifestOutcome.getIdentifier(), getParameterFieldValue(helmChartManifestOutcome.getValuesPaths()),
        helmChartManifestOutcome.getType(), helmManifest.getSubChartName());

    helmFetchFileConfigList.addAll(mapValuesManifestsToHelmFetchFileConfig(aggregatedValuesManifests));
    HelmValuesFetchRequest helmValuesFetchRequest =
        HelmValuesFetchRequest.builder()
            .accountId(accountId)
            .helmChartManifestDelegateConfig(helmManifest)
            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
            .helmFetchFileConfigList(helmFetchFileConfigList)
            .openNewLogStream(k8sStepPassThroughData.getShouldOpenFetchFilesStream())
            .closeLogStream(k8sStepPassThroughData.isShouldCloseFetchFilesStream())
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.HELM_VALUES_FETCH_NG.name())
                                  .parameters(new Object[] {helmValuesFetchRequest})
                                  .build();

    String taskName = TaskType.HELM_VALUES_FETCH_NG.getDisplayName();

    ParameterField<List<TaskSelectorYaml>> stepLevelSelectors = null;
    List<String> commandUnits = null;
    if (stepElementParameters.getSpec() instanceof K8sSpecParameters) {
      stepLevelSelectors = ((K8sSpecParameters) stepElementParameters.getSpec()).getDelegateSelectors();
      commandUnits = ((K8sSpecParameters) stepElementParameters.getSpec()).getCommandUnits();
    } else {
      stepLevelSelectors = ((HelmSpecParameters) stepElementParameters.getSpec()).getDelegateSelectors();
      commandUnits = ((HelmSpecParameters) stepElementParameters.getSpec()).getCommandUnits();
    }

    final TaskRequest taskRequest =
        TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer, commandUnits, taskName,
            TaskSelectorYaml.toTaskSelector(CollectionUtils.emptyIfNull(getParameterFieldValue(stepLevelSelectors))),
            stepHelper.getEnvironmentType(ambiance));

    K8sStepPassThroughData updatedK8sStepPassThroughData =
        k8sStepPassThroughData.toBuilder().manifestOutcomeList(new ArrayList<>(aggregatedValuesManifests)).build();

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(updatedK8sStepPassThroughData)
        .build();
  }

  protected boolean isAnyLocalStore(@NotEmpty List<? extends ManifestOutcome> aggregatedValuesManifests) {
    return aggregatedValuesManifests.stream().anyMatch(
        valuesManifest -> ManifestStoreType.HARNESS.equals(valuesManifest.getStore().getKind()));
  }

  private List<GitFetchFilesConfig> mapK8sOrHelmValuesManifestToGitFetchFileConfig(
      ValuesManifestOutcome valuesManifestOutcome, Ambiance ambiance, ManifestOutcome k8sManifestOutcome) {
    String validationMessage = format("Values YAML with Id [%s]", valuesManifestOutcome.getIdentifier());
    return getValuesGitFetchFilesConfig(ambiance, valuesManifestOutcome.getIdentifier(),
        valuesManifestOutcome.getStore(), validationMessage, k8sManifestOutcome);
  }

  protected List<GitFetchFilesConfig> getValuesGitFetchFilesConfig(Ambiance ambiance, String identifier,
      StoreConfig store, String validationMessage, ManifestOutcome k8sManifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) store;
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);
    cdStepHelper.validateManifest(store.getKind(), connectorDTO, validationMessage);
    List<String> valuesPaths;
    String folderPath;
    if (ManifestType.K8Manifest.equals(k8sManifestOutcome.getType())) {
      K8sManifestOutcome manifestOutcome = (K8sManifestOutcome) k8sManifestOutcome;
      valuesPaths = getParameterFieldValue(manifestOutcome.getValuesPaths());
      folderPath = getParameterFieldValue(gitStoreConfig.getPaths()).get(0);
    } else {
      HelmChartManifestOutcome manifestOutcome = (HelmChartManifestOutcome) k8sManifestOutcome;
      valuesPaths = getParameterFieldValue(manifestOutcome.getValuesPaths());
      folderPath = getParameterFieldValue(gitStoreConfig.getFolderPath());
      String subChartName = getParameterFieldValue(manifestOutcome.getSubChartName());
      if (isNotEmpty(subChartName)) {
        folderPath = Paths.get(folderPath, SUB_CHARTS_FOLDER, subChartName).toString();
      }
    }
    List<GitFetchFilesConfig> gitFetchFilesConfigList = new ArrayList<>();
    populateGitFetchFilesConfigListWithValuesPaths(gitFetchFilesConfigList, gitStoreConfig, k8sManifestOutcome,
        connectorDTO, ambiance, identifier, true, Arrays.asList(getValuesYamlGitFilePath(folderPath, VALUES_YAML_KEY)));
    populateGitFetchFilesConfigListWithValuesPaths(gitFetchFilesConfigList, gitStoreConfig, k8sManifestOutcome,
        connectorDTO, ambiance, identifier, false, valuesPaths);
    return gitFetchFilesConfigList;
  }

  public void addValuesFilesFromCustomFetch(Map<String, Collection<CustomSourceFile>> customFetchContent,
      List<String> valuesFileContents, String k8sManifestIdentifier) {
    if (isNotEmpty(customFetchContent) && customFetchContent.containsKey(k8sManifestIdentifier)) {
      Collection<CustomSourceFile> customSourceFiles = customFetchContent.get(k8sManifestIdentifier);
      for (CustomSourceFile customSourceFile : customSourceFiles) {
        valuesFileContents.add(customSourceFile.getFileContent());
      }
    }
  }

  protected void populateGitFetchFilesConfigListWithValuesPaths(List<GitFetchFilesConfig> gitFetchFilesConfigList,
      GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome, ConnectorInfoDTO connectorDTO, Ambiance ambiance,
      String identifier, boolean succeedIfFileNotFound, List<String> gitFileValuesPaths) {
    if (isNotEmpty(gitFileValuesPaths)) {
      GitStoreDelegateConfig gitStoreDelegateConfig = cdStepHelper.getGitStoreDelegateConfig(
          gitStoreConfig, connectorDTO, manifestOutcome, gitFileValuesPaths, ambiance);
      gitFetchFilesConfigList.add(cdStepHelper.getGitFetchFilesConfigFromBuilder(
          identifier, manifestOutcome.getType(), succeedIfFileNotFound, gitStoreDelegateConfig));
    }
  }

  public ManifestDelegateConfig getManifestDelegateConfigWrapper(
      String zippedManifestId, ManifestOutcome manifestOutcome, Ambiance ambiance, List<ManifestFiles> manifestFiles) {
    ManifestDelegateConfig manifestDelegateConfig = getManifestDelegateConfig(manifestOutcome, ambiance);

    if (StoreDelegateConfigType.CUSTOM_REMOTE.equals(manifestDelegateConfig.getStoreDelegateConfig().getType())) {
      ((CustomRemoteStoreDelegateConfig) manifestDelegateConfig.getStoreDelegateConfig())
          .getCustomManifestSource()
          .setZippedManifestFileId(zippedManifestId);
    }

    if (StoreDelegateConfigType.HARNESS.equals(manifestDelegateConfig.getStoreDelegateConfig().getType())) {
      ((LocalFileStoreDelegateConfig) manifestDelegateConfig.getStoreDelegateConfig()).setManifestFiles(manifestFiles);
    }

    return manifestDelegateConfig;
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
  public ManifestDelegateConfig getManifestDelegateConfig(ManifestOutcome manifestOutcome, Ambiance ambiance) {
    switch (manifestOutcome.getType()) {
      case ManifestType.K8Manifest:
        K8sManifestOutcome k8sManifestOutcome = (K8sManifestOutcome) manifestOutcome;
        return K8sManifestDelegateConfig.builder()
            .storeDelegateConfig(getStoreDelegateConfig(
                k8sManifestOutcome.getStore(), ambiance, manifestOutcome, manifestOutcome.getType()))
            .build();

      case ManifestType.HelmChart:
        HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestOutcome;
        HelmVersion helmVersion =
            getHelmVersionBasedOnFF(helmChartManifestOutcome.getHelmVersion(), AmbianceUtils.getAccountId(ambiance));
        return HelmChartManifestDelegateConfig.builder()
            .storeDelegateConfig(getStoreDelegateConfig(helmChartManifestOutcome.getStore(), ambiance, manifestOutcome,
                manifestOutcome.getType() + " manifest"))
            .chartName(getParameterFieldValue(helmChartManifestOutcome.getChartName()))
            .chartVersion(getParameterFieldValue(helmChartManifestOutcome.getChartVersion()))
            .helmVersion(helmVersion)
            .helmCommandFlag(getDelegateHelmCommandFlag(helmChartManifestOutcome.getCommandFlags()))
            .useCache(helmVersion != HelmVersion.V2
                && !cdFeatureFlagHelper.isEnabled(
                    AmbianceUtils.getAccountId(ambiance), FeatureName.DISABLE_HELM_REPO_YAML_CACHE))
            .checkIncorrectChartVersion(true)
            .useRepoFlags(helmVersion != HelmVersion.V2)
            .deleteRepoCacheDir(helmVersion != HelmVersion.V2)
            .skipApplyHelmDefaultValues(cdFeatureFlagHelper.isEnabled(
                AmbianceUtils.getAccountId(ambiance), FeatureName.CDP_SKIP_DEFAULT_VALUES_YAML_NG))
            .subChartName(
                cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.NG_CDS_HELM_SUB_CHARTS)
                    ? getParameterFieldValue(helmChartManifestOutcome.getSubChartName())
                    : "")
            .build();

      case ManifestType.Kustomize:
        KustomizeManifestOutcome kustomizeManifestOutcome = (KustomizeManifestOutcome) manifestOutcome;
        StoreConfig storeConfig = kustomizeManifestOutcome.getStore();
        if (ManifestStoreType.HARNESS.equals(storeConfig.getKind())) {
          LocalFileStoreDelegateConfig localFileStoreDelegateConfig =
              (LocalFileStoreDelegateConfig) getStoreDelegateConfig(
                  storeConfig, ambiance, kustomizeManifestOutcome, manifestOutcome.getType() + " manifest");
          return KustomizeManifestDelegateConfig.builder()
              .storeDelegateConfig(localFileStoreDelegateConfig)
              .pluginPath(getParameterFieldValue(kustomizeManifestOutcome.getPluginPath()))
              .kustomizeDirPath(".")
              .kustomizeYamlFolderPath(kustomizeYamlFolderPathNotNullCheck(kustomizeManifestOutcome)
                      ? getParameterFieldValue(
                          getParameterFieldValue(kustomizeManifestOutcome.getOverlayConfiguration())
                              .getKustomizeYamlFolderPath())
                      : null)
              .build();
        } else if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
          throw new UnsupportedOperationException(
              format("Kustomize Manifest is not supported for store type: [%s]", storeConfig.getKind()));
        }
        GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
        return KustomizeManifestDelegateConfig.builder()
            .storeDelegateConfig(getStoreDelegateConfig(kustomizeManifestOutcome.getStore(), ambiance, manifestOutcome,
                manifestOutcome.getType() + " manifest"))
            .kustomizeYamlFolderPath(kustomizeYamlFolderPathNotNullCheck(kustomizeManifestOutcome)
                    ? getParameterFieldValue(getParameterFieldValue(kustomizeManifestOutcome.getOverlayConfiguration())
                                                 .getKustomizeYamlFolderPath())
                    : null)
            .pluginPath(getParameterFieldValue(kustomizeManifestOutcome.getPluginPath()))
            .kustomizeDirPath(getParameterFieldValue(gitStoreConfig.getFolderPath()))
            .build();

      case ManifestType.OpenshiftTemplate:
        OpenshiftManifestOutcome openshiftManifestOutcome = (OpenshiftManifestOutcome) manifestOutcome;
        return OpenshiftManifestDelegateConfig.builder()
            .storeDelegateConfig(getStoreDelegateConfig(openshiftManifestOutcome.getStore(), ambiance, manifestOutcome,
                manifestOutcome.getType() + " manifest"))
            .build();

      default:
        throw new UnsupportedOperationException(format("Unsupported Manifest type: [%s]", manifestOutcome.getType()));
    }
  }
  public HelmVersion getHelmVersionBasedOnFF(HelmVersion helmVersion, String accountId) {
    if (helmVersion == HelmVersion.V2) {
      return helmVersion;
    }
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.HELM_VERSION_3_8_0) == true ? HelmVersion.V380
                                                                                            : HelmVersion.V3;
  }

  public boolean kustomizeYamlFolderPathNotNullCheck(KustomizeManifestOutcome kustomizeManifestOutcome) {
    return ParameterField.isNotNull(kustomizeManifestOutcome.getOverlayConfiguration())
        && ParameterField.isNotNull(
            getParameterFieldValue(kustomizeManifestOutcome.getOverlayConfiguration()).getKustomizeYamlFolderPath());
  }

  public GitFetchFilesConfig getGitFetchFilesConfig(
      Ambiance ambiance, StoreConfig store, String validationMessage, ManifestOutcome manifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) store;
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);
    cdStepHelper.validateManifest(store.getKind(), connectorDTO, validationMessage);

    List<String> gitFilePaths = getPathsBasedOnManifest(gitStoreConfig, manifestOutcome.getType());
    GitStoreDelegateConfig gitStoreDelegateConfig =
        cdStepHelper.getGitStoreDelegateConfig(gitStoreConfig, connectorDTO, manifestOutcome, gitFilePaths, ambiance);

    return cdStepHelper.getGitFetchFilesConfigFromBuilder(
        manifestOutcome.getIdentifier(), manifestOutcome.getType(), false, gitStoreDelegateConfig);
  }

  public GitFetchFilesConfig getPathsFromInheritFromManifestStoreConfig(
      Ambiance ambiance, String validationMessage, ManifestOutcome manifestOutcome, GitStoreConfig gitStoreConfig) {
    InheritFromManifestStoreConfig inheritFromManifestStoreConfig =
        (InheritFromManifestStoreConfig) manifestOutcome.getStore();
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);
    cdStepHelper.validateManifest(gitStoreConfig.getKind(), connectorDTO, validationMessage);
    List<String> gitFilePaths = new ArrayList<>(getParameterFieldValue(inheritFromManifestStoreConfig.getPaths()));

    GitStoreDelegateConfig gitStoreDelegateConfig =
        cdStepHelper.getGitStoreDelegateConfig(gitStoreConfig, connectorDTO, manifestOutcome, gitFilePaths, ambiance);
    return GitFetchFilesConfig.builder()
        .identifier(manifestOutcome.getIdentifier())
        .manifestType(manifestOutcome.getType())
        .gitStoreDelegateConfig(gitStoreDelegateConfig)
        .build();
  }

  public static HelmFetchFileConfig getInheritFromManifestFetchFileConfig(
      String identifier, String manifestType, InheritFromManifestStoreConfig inheritFromManifestStoreConfig) {
    List<String> filePaths = new ArrayList<>(getParameterFieldValue(inheritFromManifestStoreConfig.getPaths()));

    return HelmFetchFileConfig.builder().identifier(identifier).manifestType(manifestType).filePaths(filePaths).build();
  }

  // Aggregated Manifest methods
  public static List<ValuesManifestOutcome> getAggregatedValuesManifests(
      @NotEmpty List<ManifestOutcome> manifestOutcomeList) {
    List<ValuesManifestOutcome> aggregateValuesManifests = new ArrayList<>();

    List<ValuesManifestOutcome> serviceValuesManifests =
        manifestOutcomeList.stream()
            .filter(manifestOutcome -> ManifestType.VALUES.equals(manifestOutcome.getType()))
            .map(manifestOutcome -> (ValuesManifestOutcome) manifestOutcome)
            .collect(Collectors.toList());

    if (isNotEmpty(serviceValuesManifests)) {
      aggregateValuesManifests.addAll(serviceValuesManifests);
    }
    return aggregateValuesManifests;
  }

  public static List<HelmFetchFileConfig> mapValuesManifestsToHelmFetchFileConfig(
      List<ValuesManifestOutcome> aggregatedValuesManifests) {
    if (isEmpty(aggregatedValuesManifests)) {
      return emptyList();
    }
    return aggregatedValuesManifests.stream()
        .filter(valuesManifestOutcome
            -> ManifestStoreType.InheritFromManifest.equals(valuesManifestOutcome.getStore().getKind()))
        .map(valuesManifestOutcome
            -> getInheritFromManifestFetchFileConfig(valuesManifestOutcome.getIdentifier(),
                valuesManifestOutcome.getType(), (InheritFromManifestStoreConfig) valuesManifestOutcome.getStore()))
        .collect(Collectors.toList());
  }

  public String getDefaultOverridePath(String manifestType) {
    if (VALUES_YAML_SUPPORTED_MANIFEST_TYPES.contains(manifestType)) {
      return VALUES_YAML_KEY;
    }
    return "";
  }

  public boolean shouldExecuteCustomFetchTask(StoreConfig storeConfig, List<ManifestOutcome> manifestOutcomes) {
    boolean retVal = false;
    for (ManifestOutcome manifestOutcome : manifestOutcomes) {
      retVal = retVal || ManifestStoreType.CUSTOM_REMOTE.equals(manifestOutcome.getStore().getKind());
    }
    retVal = retVal || ManifestStoreType.CUSTOM_REMOTE.equals(storeConfig.getKind());
    return retVal;
  }

  public boolean shouldExecuteGitFetchTask(List<? extends ManifestOutcome> manifestOutcomes) {
    boolean retVal = false;
    for (ManifestOutcome manifestOutcome : manifestOutcomes) {
      retVal = retVal || ManifestStoreType.isInGitSubset(manifestOutcome.getStore().getKind());
    }
    return retVal;
  }

  public List<String> getManifestOverridePaths(ManifestOutcome manifestOutcome) {
    if (ManifestType.K8Manifest.equals(manifestOutcome.getType())) {
      if (((K8sManifestOutcome) manifestOutcome).getValuesPaths().getValue() != null) {
        return ((K8sManifestOutcome) manifestOutcome).getValuesPaths().getValue();
      }

    }

    else if (ManifestType.HelmChart.equals(manifestOutcome.getType())) {
      if (((HelmChartManifestOutcome) manifestOutcome).getValuesPaths().getValue() != null) {
        return ((HelmChartManifestOutcome) manifestOutcome).getValuesPaths().getValue();
      }
    }

    else if (ManifestType.OpenshiftTemplate.equals(manifestOutcome.getType())) {
      if (((OpenshiftManifestOutcome) manifestOutcome).getParamsPaths().getValue() != null) {
        return ((OpenshiftManifestOutcome) manifestOutcome).getParamsPaths().getValue();
      }
    }

    return emptyList();
  }

  public StoreDelegateConfig getStoreDelegateConfig(
      StoreConfig storeConfig, Ambiance ambiance, ManifestOutcome manifestOutcome, String validationErrorMessage) {
    if (ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
      ConnectorInfoDTO connectorDTO =
          cdStepHelper.getConnector(getParameterFieldValue(gitStoreConfig.getConnectorRef()), ambiance);
      cdStepHelper.validateManifest(storeConfig.getKind(), connectorDTO, validationErrorMessage);
      List<String> gitFilePaths;
      if (manifestOutcome.getType().equals(ManifestType.Kustomize)) {
        gitFilePaths = getKustomizeManifestBasePath(gitStoreConfig, manifestOutcome);
      } else {
        gitFilePaths = getPathsBasedOnManifest(gitStoreConfig, manifestOutcome.getType());
      }

      return cdStepHelper.getGitStoreDelegateConfig(
          gitStoreConfig, connectorDTO, manifestOutcome, gitFilePaths, ambiance);
    }

    if (ManifestStoreType.HARNESS.equals(storeConfig.getKind())) {
      HarnessStore harnessStore = (HarnessStore) storeConfig;
      String scopedFilePath = harnessStore.getFiles().getValue().get(0);
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      FileReference fileReference = FileReference.of(scopedFilePath, ngAccess.getAccountIdentifier(),
          ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
      Optional<FileStoreNodeDTO> optionalFileStoreNodeDTO =
          fileStoreService.getWithChildrenByPath(fileReference.getAccountIdentifier(), fileReference.getOrgIdentifier(),
              fileReference.getProjectIdentifier(), fileReference.getPath(), false);
      if (optionalFileStoreNodeDTO.isPresent()) {
        FileStoreNodeDTO manifestFileDirectory = optionalFileStoreNodeDTO.get();
        if (NGFileType.FOLDER.equals(manifestFileDirectory.getType())) {
          return LocalFileStoreDelegateConfig.builder()
              .folder(manifestFileDirectory.getName())
              .filePaths(Arrays.asList(manifestFileDirectory.getPath().substring(1)))
              .manifestType(manifestOutcome.getType())
              .manifestIdentifier(manifestOutcome.getIdentifier())
              .build();
        }
      }
      return LocalFileStoreDelegateConfig.builder()
          .filePaths(harnessStore.getFiles().getValue())
          .manifestType(manifestOutcome.getType())
          .manifestIdentifier(manifestOutcome.getIdentifier())
          .build();
    }

    if (ManifestStoreType.CUSTOM_REMOTE.equals(storeConfig.getKind())) {
      CustomRemoteStoreConfig customRemoteStoreConfig = (CustomRemoteStoreConfig) storeConfig;

      return CustomRemoteStoreDelegateConfig.builder()
          .customManifestSource(CustomManifestSource.builder()
                                    .filePaths(Arrays.asList(customRemoteStoreConfig.getFilePath().getValue()))
                                    .script(customRemoteStoreConfig.getExtractionScript().getValue())
                                    .accountId(AmbianceUtils.getAccountId(ambiance))
                                    .build())
          .build();
    }

    if (ManifestStoreType.HTTP.equals(storeConfig.getKind())) {
      HttpStoreConfig httpStoreConfig = (HttpStoreConfig) storeConfig;
      ConnectorInfoDTO helmConnectorDTO =
          cdStepHelper.getConnector(getParameterFieldValue(httpStoreConfig.getConnectorRef()), ambiance);
      cdStepHelper.validateManifest(storeConfig.getKind(), helmConnectorDTO, validationErrorMessage);
      Preconditions.checkArgument(
          manifestOutcome instanceof HelmChartManifestOutcome, MANIFEST_OUTCOME_INCOMPATIBLE_ERROR_MESSAGE);

      return HttpHelmStoreDelegateConfig.builder()
          .repoName(getRepoName(ambiance, helmConnectorDTO.getIdentifier(), (HelmChartManifestOutcome) manifestOutcome))
          .repoDisplayName(helmConnectorDTO.getName())
          .httpHelmConnector((HttpHelmConnectorDTO) helmConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(
              k8sEntityHelper.getEncryptionDataDetails(helmConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
          .build();
    }

    if (ManifestStoreType.OCI.equals(storeConfig.getKind())) {
      OciHelmChartConfig ociStoreConfig = (OciHelmChartConfig) storeConfig;
      ConnectorInfoDTO helmConnectorDTO =
          cdStepHelper.getConnector(getParameterFieldValue(ociStoreConfig.getConnectorReference()), ambiance);
      cdStepHelper.validateManifest(storeConfig.getKind(), helmConnectorDTO, validationErrorMessage);
      Preconditions.checkArgument(
          manifestOutcome instanceof HelmChartManifestOutcome, MANIFEST_OUTCOME_INCOMPATIBLE_ERROR_MESSAGE);

      return OciHelmStoreDelegateConfig.builder()
          .repoName(getRepoName(ambiance, helmConnectorDTO.getIdentifier(), (HelmChartManifestOutcome) manifestOutcome))
          .basePath(getParameterFieldValue(ociStoreConfig.getBasePath()))
          .repoDisplayName(helmConnectorDTO.getName())
          .ociHelmConnector((OciHelmConnectorDTO) helmConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(
              k8sEntityHelper.getEncryptionDataDetails(helmConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
          .helmOciEnabled(true)
          .build();
    }

    if (ManifestStoreType.S3.equals(storeConfig.getKind())) {
      S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
      ConnectorInfoDTO awsConnectorDTO =
          cdStepHelper.getConnector(getParameterFieldValue(s3StoreConfig.getConnectorRef()), ambiance);
      cdStepHelper.validateManifest(storeConfig.getKind(), awsConnectorDTO, validationErrorMessage);
      Preconditions.checkArgument(
          manifestOutcome instanceof HelmChartManifestOutcome, MANIFEST_OUTCOME_INCOMPATIBLE_ERROR_MESSAGE);

      return S3HelmStoreDelegateConfig.builder()
          .repoName(getRepoName(ambiance, awsConnectorDTO.getIdentifier(), (HelmChartManifestOutcome) manifestOutcome))
          .repoDisplayName(awsConnectorDTO.getName())
          .bucketName(getParameterFieldValue(s3StoreConfig.getBucketName()))
          .region(getParameterFieldValue(s3StoreConfig.getRegion()))
          .folderPath(getParameterFieldValue(s3StoreConfig.getFolderPath()))
          .awsConnector((AwsConnectorDTO) awsConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(
              k8sEntityHelper.getEncryptionDataDetails(awsConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
          .useLatestChartMuseumVersion(cdFeatureFlagHelper.isEnabled(
              AmbianceUtils.getAccountId(ambiance), FeatureName.USE_LATEST_CHARTMUSEUM_VERSION))
          .build();
    }

    if (ManifestStoreType.GCS.equals(storeConfig.getKind())) {
      GcsStoreConfig gcsStoreConfig = (GcsStoreConfig) storeConfig;
      ConnectorInfoDTO gcpConnectorDTO =
          cdStepHelper.getConnector(getParameterFieldValue(gcsStoreConfig.getConnectorRef()), ambiance);
      cdStepHelper.validateManifest(storeConfig.getKind(), gcpConnectorDTO, validationErrorMessage);
      Preconditions.checkArgument(
          manifestOutcome instanceof HelmChartManifestOutcome, MANIFEST_OUTCOME_INCOMPATIBLE_ERROR_MESSAGE);

      return GcsHelmStoreDelegateConfig.builder()
          .repoName(getRepoName(ambiance, gcpConnectorDTO.getIdentifier(), (HelmChartManifestOutcome) manifestOutcome))
          .repoDisplayName(gcpConnectorDTO.getName())
          .bucketName(getParameterFieldValue(gcsStoreConfig.getBucketName()))
          .folderPath(getParameterFieldValue(gcsStoreConfig.getFolderPath()))
          .gcpConnector((GcpConnectorDTO) gcpConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(
              k8sEntityHelper.getEncryptionDataDetails(gcpConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
          .useLatestChartMuseumVersion(cdFeatureFlagHelper.isEnabled(
              AmbianceUtils.getAccountId(ambiance), FeatureName.USE_LATEST_CHARTMUSEUM_VERSION))
          .build();
    }

    throw new UnsupportedOperationException(format("Unsupported Store Config type: [%s]", storeConfig.getKind()));
  }

  public String getRepoName(Ambiance ambiance, String connectorId, HelmChartManifestOutcome manifestOutcome) {
    /*
      going forward, we will be creating default cache based on connectorId unless FF is enabled
      in which case we will create based on executionId
      details here: https://harness.atlassian.net/wiki/spaces/CDP/pages/21134344193/Helm+FFs+cleanup
     */
    boolean isNotHelmV2 = manifestOutcome != null && HelmVersion.V2 != manifestOutcome.getHelmVersion();
    boolean useCache = isNotHelmV2
        && !cdFeatureFlagHelper.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.DISABLE_HELM_REPO_YAML_CACHE);
    String repoId = useCache ? connectorId : ambiance.getPlanExecutionId();

    return convertBase64UuidToCanonicalForm(repoId);
  }

  public List<String> getPathsBasedOnManifest(GitStoreConfig gitstoreConfig, String manifestType) {
    List<String> paths = new ArrayList<>();
    switch (manifestType) {
      case ManifestType.HelmChart:
        paths.add(getParameterFieldValue(gitstoreConfig.getFolderPath()));
        break;
      case ManifestType.Kustomize:
        // Set as repository root
        paths.add("/");
        break;

      default:
        paths.addAll(getParameterFieldValue(gitstoreConfig.getPaths()));
    }

    return paths;
  }

  public List<String> getKustomizeManifestBasePath(GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome) {
    List<String> paths = new ArrayList<>();
    KustomizeManifestOutcome kustomizeManifestOutcome = (KustomizeManifestOutcome) manifestOutcome;
    if (kustomizeYamlFolderPathNotNullCheck(kustomizeManifestOutcome)) {
      paths.add(getParameterFieldValue(gitStoreConfig.getFolderPath()));
    } else {
      paths.add("/");
    }
    return paths;
  }

  public List<String> getValuesFileContents(Ambiance ambiance, List<String> valuesFileContents) {
    return valuesFileContents.stream()
        .filter(Objects::nonNull)
        .map(valuesFileContent -> engineExpressionService.renderExpression(ambiance, valuesFileContent, false))
        .collect(Collectors.toList());
  }

  public StepResponse handleCustomTaskFailure(CustomFetchResponsePassThroughData customFetchResponse) {
    UnitProgressData unitProgressData = customFetchResponse.getUnitProgressData();
    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(customFetchResponse.getErrorMsg()).build())
        .build();
  }

  public StepResponse handleHelmValuesFetchFailure(HelmValuesFetchResponsePassThroughData helmValuesFetchResponse) {
    UnitProgressData unitProgressData = helmValuesFetchResponse.getUnitProgressData();
    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(helmValuesFetchResponse.getErrorMsg()).build())
        .build();
  }

  public static List<HelmFetchFileConfig> mapHelmChartManifestsToHelmFetchFileConfig(
      String identifier, List<String> valuesPaths, String manifestType, String subChartName) {
    List<HelmFetchFileConfig> helmFetchFileConfigList = new ArrayList<>();
    if (isEmpty(subChartName)) {
      helmFetchFileConfigList.add(
          createHelmFetchFileConfig(identifier, manifestType, Arrays.asList(VALUES_YAML_KEY), true));
    } else {
      helmFetchFileConfigList.add(createHelmFetchFileConfig(identifier, manifestType,
          Arrays.asList(Paths.get(SUB_CHARTS_FOLDER, subChartName, VALUES_YAML_KEY).toString()), true));
    }
    if (isNotEmpty(valuesPaths)) {
      helmFetchFileConfigList.add(createHelmFetchFileConfig(identifier, manifestType, valuesPaths, false));
    }
    return helmFetchFileConfigList;
  }

  public static HelmFetchFileConfig createHelmFetchFileConfig(
      String identifier, String manifestType, List<String> valuesPaths, boolean succeedIfFileNotFound) {
    return HelmFetchFileConfig.builder()
        .identifier(identifier)
        .manifestType(manifestType)
        .filePaths(valuesPaths)
        .succeedIfFileNotFound(succeedIfFileNotFound)
        .build();
  }

  public List<String> getManifestFilesContents(Map<String, FetchFilesResult> gitFetchFilesResultMap,
      List<ManifestOutcome> valuesManifests, Map<String, HelmFetchFileResult> helmChartFetchFilesResultMap,
      Map<String, Collection<CustomSourceFile>> customFetchContent,
      Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap) {
    List<String> valuesFileContents = new ArrayList<>();

    for (ManifestOutcome valuesManifest : valuesManifests) {
      StoreConfig store = extractStoreConfigFromManifestOutcome(valuesManifest);
      String valuesIdentifier = valuesManifest.getIdentifier();
      if (ManifestStoreType.INLINE.equals(store.getKind())) {
        valuesFileContents.add(((InlineStoreConfig) store).extractContent());
      } else if (isNotEmpty(gitFetchFilesResultMap) && gitFetchFilesResultMap.containsKey(valuesIdentifier)) {
        FetchFilesResult gitFetchFilesResult = gitFetchFilesResultMap.get(valuesIdentifier);
        if (!isNull(gitFetchFilesResult)) {
          valuesFileContents.addAll(
              gitFetchFilesResult.getFiles().stream().map(GitFile::getFileContent).collect(Collectors.toList()));
        }
      } else if (isNotEmpty(helmChartFetchFilesResultMap)
          && helmChartFetchFilesResultMap.containsKey(valuesIdentifier)) {
        List<String> helmChartValuesFileContent =
            helmChartFetchFilesResultMap.get(valuesIdentifier).getValuesFileContents();
        if (isNotEmpty(helmChartValuesFileContent)) {
          valuesFileContents.addAll(helmChartValuesFileContent);
        }
      } else if (isNotEmpty(customFetchContent) && customFetchContent.containsKey(valuesIdentifier)) {
        Collection<CustomSourceFile> customSourceFiles = customFetchContent.get(valuesIdentifier);
        for (CustomSourceFile customSourceFile : customSourceFiles) {
          valuesFileContents.add(customSourceFile.getFileContent());
        }
      } else if (isNotEmpty(localStoreFetchFilesResultMap)
          && localStoreFetchFilesResultMap.containsKey(valuesIdentifier)) {
        List<String> localStoreValuesFileContent =
            localStoreFetchFilesResultMap.get(valuesIdentifier).getLocalStoreFileContents();
        if (isNotEmpty(localStoreValuesFileContent)) {
          valuesFileContents.addAll(localStoreValuesFileContent);
        }
      }
    }
    return valuesFileContents;
  }

  public StoreConfig extractStoreConfigFromManifestOutcome(ManifestOutcome manifestOutcome) {
    switch (manifestOutcome.getType()) {
      case ManifestType.K8Manifest:
        K8sManifestOutcome k8sManifestOutcome = (K8sManifestOutcome) manifestOutcome;
        return k8sManifestOutcome.getStore();

      case ManifestType.HelmChart:
        HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestOutcome;
        return helmChartManifestOutcome.getStore();

      case ManifestType.VALUES:
        ValuesManifestOutcome valuesManifestOutcome = (ValuesManifestOutcome) manifestOutcome;
        return valuesManifestOutcome.getStore();

      case ManifestType.KustomizePatches:
        KustomizePatchesManifestOutcome kustomizePatchesManifestOutcome =
            (KustomizePatchesManifestOutcome) manifestOutcome;
        return kustomizePatchesManifestOutcome.getStore();

      case ManifestType.OpenshiftParam:
        OpenshiftParamManifestOutcome openshiftParamManifestOutcome = (OpenshiftParamManifestOutcome) manifestOutcome;
        return openshiftParamManifestOutcome.getStore();

      case ManifestType.OpenshiftTemplate:
        OpenshiftManifestOutcome openshiftManifestOutcome = (OpenshiftManifestOutcome) manifestOutcome;
        return openshiftManifestOutcome.getStore();

      case ManifestType.Kustomize:
        KustomizeManifestOutcome kustomizeManifestOutcome = (KustomizeManifestOutcome) manifestOutcome;
        return kustomizeManifestOutcome.getStore();

      default:
        throw new UnsupportedOperationException(format("Unsupported Manifest type: [%s]", manifestOutcome.getType()));
    }
  }

  public List<ManifestOutcome> getStepLevelManifestOutcomes(StepElementParameters stepElementParameters) {
    if (!(stepElementParameters.getSpec() instanceof K8sApplyStepParameters)) {
      return Collections.emptyList();
    }
    if (((K8sApplyStepParameters) stepElementParameters.getSpec()).getOverrides() == null) {
      return Collections.emptyList();
    }
    List<ManifestOutcome> manifestOutcomes = new ArrayList<>();
    List<ManifestAttributes> manifestAttributesList =
        ((K8sApplyStepParameters) stepElementParameters.getSpec())
            .getOverrides()
            .stream()
            .map(manifestConfigWrapper -> manifestConfigWrapper.getManifest().getSpec())
            .collect(Collectors.toList());

    for (int i = 0; i < manifestAttributesList.size(); i++) {
      ManifestAttributes manifestAttributes = manifestAttributesList.get(i);
      manifestOutcomes.add(ManifestOutcomeMapper.toManifestOutcome(manifestAttributes, i));
    }
    return manifestOutcomes;
  }

  public Map<String, LocalStoreFetchFilesResult> fetchFilesFromLocalStore(Ambiance ambiance,
      ManifestOutcome manifestOutcome, List<? extends ManifestOutcome> manifestOutcomeList,
      List<ManifestFiles> manifestFiles, LogCallback logCallback) {
    Map<String, LocalStoreFetchFilesResult> localStoreFileMapContents = new HashMap<>();
    StoreConfig storeConfig = manifestOutcome.getStore();
    logCallback.saveExecutionLog(color(format("%nStarting Harness Fetch Files"), LogColor.White, LogWeight.Bold));
    if (ManifestStoreType.HARNESS.equals(storeConfig.getKind())) {
      HarnessStore localStoreConfig = (HarnessStore) storeConfig;
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      LocalStoreFetchFilesResult fileContents;

      if (VALUES_YAML_SUPPORTED_MANIFEST_TYPES.contains(manifestOutcome.getType())) {
        fileContents = getValuesFileContentsAsLocalStoreFetchFilesResult(manifestOutcome, ngAccess, logCallback);
        for (String scopedFilePath : localStoreConfig.getFiles().getValue()) {
          manifestFiles.addAll(
              validateAndReturnManifestFileFromHarnessStore(scopedFilePath, ngAccess, manifestOutcome.getIdentifier()));
        }
      } else if (ManifestType.OpenshiftTemplate.equals(manifestOutcome.getType())) {
        fileContents = getFileContentsFromManifest(ngAccess, new ArrayList<>(),
            ((OpenshiftManifestOutcome) manifestOutcome).getParamsPaths().getValue(), manifestOutcome.getType(),
            manifestOutcome.getIdentifier(), logCallback);
        for (String scopedFilePath : localStoreConfig.getFiles().getValue()) {
          manifestFiles.addAll(
              validateAndReturnManifestFileFromHarnessStore(scopedFilePath, ngAccess, manifestOutcome.getIdentifier()));
        }
      } else if (ManifestType.Kustomize.equals(manifestOutcome.getType())) {
        KustomizeManifestOutcome kustomizeManifestOutcome = (KustomizeManifestOutcome) manifestOutcome;
        fileContents = getFileContentsFromManifest(ngAccess, new ArrayList<>(),
            kustomizeManifestOutcome.getPatchesPaths().getValue(), manifestOutcome.getType(),
            manifestOutcome.getIdentifier(), logCallback);
        String baseFolderPath = getParameterFieldValue(localStoreConfig.getFiles()).get(0);
        manifestFiles.addAll(
            validateAndReturnManifestFileFromHarnessStore(baseFolderPath, ngAccess, manifestOutcome.getIdentifier()));
      } else {
        throw new UnsupportedOperationException(format("Unsupported Manifest type: [%s]", manifestOutcome.getType()));
      }

      if (isNotEmpty(fileContents.getLocalStoreFileContents())) {
        localStoreFileMapContents.put(manifestOutcome.getIdentifier(), fileContents);
      }
    }

    if (isNotEmpty(manifestOutcomeList) && isAnyLocalStore(manifestOutcomeList)) {
      localStoreFileMapContents.putAll(
          getFileContentsForLocalStore(manifestOutcomeList, AmbianceUtils.getNgAccess(ambiance), logCallback));
    }
    logCallback.saveExecutionLog(
        color(format("%nHarness Fetch Files completed successfully."), LogColor.White, LogWeight.Bold));
    return localStoreFileMapContents;
  }

  public LocalStoreFetchFilesResult getValuesFileContentsAsLocalStoreFetchFilesResult(
      ManifestOutcome manifestOutcome, NGAccess ngAccess, LogCallback logCallback) {
    String manifestIdentifier = manifestOutcome.getIdentifier();
    List<String> scopedFilePathList;
    if (ManifestType.K8Manifest.equals(manifestOutcome.getType())) {
      scopedFilePathList = ((K8sManifestOutcome) manifestOutcome).getValuesPaths().getValue();
    } else if (ManifestType.HelmChart.equals(manifestOutcome.getType())) {
      scopedFilePathList = ((HelmChartManifestOutcome) manifestOutcome).getValuesPaths().getValue();
    } else {
      throw new UnsupportedOperationException(format("Unsupported Manifest type: [%s]", manifestOutcome.getType()));
    }
    List<String> fileContents = new ArrayList<>();

    String scopedManifestFilePath = ((HarnessStore) manifestOutcome.getStore()).getFiles().getValue().get(0);
    scopedManifestFilePath = scopedManifestFilePath.endsWith("/") ? scopedManifestFilePath + values_filename
                                                                  : scopedManifestFilePath + "/" + values_filename;
    logCallback.saveExecutionLog(
        format("%nTrying to fetch default values yaml file for manifest with identifier: [%s].", manifestIdentifier));
    try {
      FileStoreNodeDTO baseValuesFile =
          validateAndFetchFileFromHarnessStore(scopedManifestFilePath, ngAccess, manifestIdentifier).get();
      FileNodeDTO fileNode = (FileNodeDTO) baseValuesFile;
      String fileContent = fileNode.getContent() == null ? "" : fileNode.getContent();
      fileContents.add(fileContent);

      if (isEmpty(fileContent)) {
        logCallback.saveExecutionLog(
            format("%nFetched values.yaml file is empty [file path: %s]", scopedManifestFilePath), LogLevel.WARN);
      } else {
        logCallback.saveExecutionLog(color(format("%nSuccessfully fetched values.yaml file"), White));
      }
    } catch (Exception ex) {
      logCallback.saveExecutionLog(
          color(format("No values.yaml found for manifest with identifier: %s.", manifestIdentifier), White));
    }

    return getFileContentsFromManifest(
        ngAccess, fileContents, scopedFilePathList, manifestOutcome.getType(), manifestIdentifier, logCallback);
  }

  public LocalStoreFetchFilesResult getFileContentsFromManifest(NGAccess ngAccess, List<String> fileContents,
      List<String> scopedFilePathList, String manifestType, String manifestIdentifier, LogCallback logCallback) {
    if (isNotEmpty(scopedFilePathList)) {
      logCallback.saveExecutionLog(
          color(format("%nFetching %s files with identifier: %s", manifestType, manifestIdentifier), LogColor.White,
              LogWeight.Bold));
      logCallback.saveExecutionLog(color(format("Fetching following Files :"), LogColor.White));
      printFilesFetchedFromHarnessStore(scopedFilePathList, logCallback);
      logCallback.saveExecutionLog(
          color(format("Successfully fetched following files: "), LogColor.White, LogWeight.Bold));
      for (String scopedFilePath : scopedFilePathList) {
        Optional<FileStoreNodeDTO> valuesFile =
            validateAndFetchFileFromHarnessStore(scopedFilePath, ngAccess, manifestIdentifier);
        FileStoreNodeDTO fileStoreNodeDTO = valuesFile.get();
        if (NGFileType.FILE.equals(fileStoreNodeDTO.getType())) {
          FileNodeDTO file = (FileNodeDTO) fileStoreNodeDTO;
          String fileContent = file.getContent() == null ? "" : file.getContent();
          fileContents.add(fileContent);
          if (isEmpty(fileContent)) {
            logCallback.saveExecutionLog(format("- %s is empty", scopedFilePath), LogLevel.WARN);
          } else {
            logCallback.saveExecutionLog(color(format("- %s", scopedFilePath), LogColor.White));
          }
        } else {
          throw new UnsupportedOperationException("Only File type is supported. Please enter the correct file path");
        }
      }
    }
    return LocalStoreFetchFilesResult.builder().LocalStoreFileContents(fileContents).build();
  }

  public Optional<FileStoreNodeDTO> validateAndFetchFileFromHarnessStore(
      String scopedFilePath, NGAccess ngAccess, String manifestIdentifier) {
    if (isBlank(scopedFilePath)) {
      throw new InvalidRequestException(
          format("File reference cannot be null or empty, manifest identifier: %s", manifestIdentifier));
    }
    FileReference fileReference = FileReference.of(
        scopedFilePath, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    Optional<FileStoreNodeDTO> manifestFile =
        fileStoreService.getWithChildrenByPath(fileReference.getAccountIdentifier(), fileReference.getOrgIdentifier(),
            fileReference.getProjectIdentifier(), fileReference.getPath(), true);
    if (!manifestFile.isPresent()) {
      throw new InvalidRequestException(
          format("File/Folder not found in File Store with path: [%s], scope: [%s], manifest identifier: [%s]",
              fileReference.getPath(), fileReference.getScope(), manifestIdentifier));
    }

    return manifestFile;
  }

  public Map<String, LocalStoreFetchFilesResult> getFileContentsForLocalStore(
      List<? extends ManifestOutcome> aggregatedManifestOutcomes, NGAccess ngAccess, LogCallback logCallback) {
    return aggregatedManifestOutcomes.stream()
        .filter(manifestOutcome -> ManifestStoreType.HARNESS.equals(manifestOutcome.getStore().getKind()))
        .collect(Collectors.toMap(manifestOutcome
            -> manifestOutcome.getIdentifier(),
            manifestOutcome
            -> getFileContentsFromManifest(ngAccess, new ArrayList<>(),
                ((HarnessStore) manifestOutcome.getStore()).getFiles().getValue(), manifestOutcome.getType(),
                manifestOutcome.getIdentifier(), logCallback)));
  }

  public List<ManifestFiles> validateAndReturnManifestFileFromHarnessStore(
      String scopedFilePath, NGAccess ngAccess, String manifestIdentifier) {
    Optional<FileStoreNodeDTO> manifestFile =
        validateAndFetchFileFromHarnessStore(scopedFilePath, ngAccess, manifestIdentifier);
    FileStoreNodeDTO fileStoreNodeDTO = manifestFile.get();
    List<ManifestFiles> manifestFileList = new ArrayList<>();
    if (NGFileType.FILE.equals(fileStoreNodeDTO.getType())) {
      FileNodeDTO file = (FileNodeDTO) fileStoreNodeDTO;
      manifestFileList.add(ManifestFiles.builder()
                               .fileName(file.getName())
                               .filePath(file.getPath())
                               .fileContent(file.getContent())
                               .build());
    } else if (NGFileType.FOLDER.equals(fileStoreNodeDTO.getType())) {
      Integer folderPathLength = fileStoreNodeDTO.getPath().length();
      manifestFileList.addAll(mapFileNodes(fileStoreNodeDTO,
          fileNode
          -> ManifestFiles.builder()
                 .fileContent(fileNode.getContent())
                 .fileName(fileNode.getName())
                 .filePath(fileNode.getPath().substring(folderPathLength))
                 .build()));
    } else {
      throw new UnsupportedOperationException(
          "The File/Folder type is not supported. Please enter the correct file path");
    }
    return manifestFileList;
  }

  public void printFilesFetchedFromHarnessStore(List<String> scopedFilePathList, LogCallback logCallback) {
    for (String scopedFilePath : scopedFilePathList) {
      logCallback.saveExecutionLog(color(format("- %s", scopedFilePath), LogColor.White));
    }
  }
}
