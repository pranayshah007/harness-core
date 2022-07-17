/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.ManifestType.K8S_SUPPORTED_MANIFEST_TYPES;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.K8sHelmCommonStepHelper;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome.HelmChartManifestOutcomeKeys;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome.K8sManifestOutcomeKeys;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome.KustomizeManifestOutcomeKeys;
import io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome.OpenshiftManifestOutcomeKeys;
import io.harness.cdng.manifest.yaml.OpenshiftParamManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.helm.HelmFetchFileResult;
import io.harness.delegate.task.helm.HelmValuesFetchResponse;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.localstore.LocalStoreFetchFilesResult;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.git.model.FetchFilesResult;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.manifest.CustomSourceFile;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDP)
@Singleton
public class K8sStepHelper extends K8sHelmCommonStepHelper {
  private static final Set<String> VALUES_YAML_SUPPORTED_MANIFEST_TYPES =
      ImmutableSet.of(ManifestType.K8Manifest, ManifestType.HelmChart);

  public static final String RELEASE_NAME = "Release Name";
  public static final String PATCH_YAML_ID = "Patches YAML with Id [%s]";
  public static final String PARAM_YAML_ID = "Openshift Param file with Id [%s]";
  public static final String MISSING_INFRASTRUCTURE_ERROR = "Infrastructure section is missing or is not configured";
  public static final String RELEASE_NAME_VALIDATION_REGEX =
      "[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*";
  public static final Pattern releaseNamePattern = Pattern.compile(RELEASE_NAME_VALIDATION_REGEX);
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private EncryptionHelper encryptionHelper;
  @Inject private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Inject private AccountClient accountClient;
  @Inject private CDStepHelper cdStepHelper;

  public TaskChainResponse queueK8sTask(StepElementParameters stepElementParameters, K8sDeployRequest k8sDeployRequest,
      Ambiance ambiance, K8sExecutionPassThroughData executionPassThroughData) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {k8sDeployRequest})
                            .taskType(TaskType.K8S_COMMAND_TASK_NG.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();

    String taskName = TaskType.K8S_COMMAND_TASK_NG.getDisplayName() + " : " + k8sDeployRequest.getCommandName();
    K8sSpecParameters k8SSpecParameters = (K8sSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        k8SSpecParameters.getCommandUnits(cdFeatureFlagHelper.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.PRUNE_KUBERNETES_RESOURCES_NG)),
        taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(k8SSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(true)
        .passThroughData(executionPassThroughData)
        .build();
  }

  public List<String> renderValues(
      ManifestOutcome manifestOutcome, Ambiance ambiance, List<String> valuesFileContents) {
    if (isEmpty(valuesFileContents) || ManifestType.Kustomize.equals(manifestOutcome.getType())) {
      return emptyList();
    }

    List<String> renderedValuesFileContents = getValuesFileContents(ambiance, valuesFileContents);

    if (ManifestType.OpenshiftTemplate.equals(manifestOutcome.getType())) {
      Collections.reverse(renderedValuesFileContents);
    }

    return renderedValuesFileContents;
  }

  public List<String> renderPatches(
      ManifestOutcome manifestOutcome, Ambiance ambiance, List<String> patchesFileContents) {
    if (null == manifestOutcome) {
      return emptyList();
    }

    if (isEmpty(patchesFileContents) || !ManifestType.Kustomize.equals(manifestOutcome.getType())) {
      return emptyList();
    }
    return patchesFileContents.stream()
        .map(patchesFileContent -> engineExpressionService.renderExpression(ambiance, patchesFileContent, false))
        .collect(Collectors.toList());
  }

  public TaskChainResponse executeOpenShiftParamsTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      InfrastructureOutcome infrastructure, ManifestOutcome k8sManifestOutcome,
      List<OpenshiftParamManifestOutcome> paramManifestOutcomes,
      Map<String, HelmFetchFileResult> helmChartValuesFileContentMap,
      Map<String, Collection<CustomSourceFile>> customFetchContent, String zippedManifestId) {
    List<GitFetchFilesConfig> gitFetchFilesConfigs = new ArrayList<>();
    List<OpenshiftParamManifestOutcome> orderedOpenshiftParamManifests =
        (List<OpenshiftParamManifestOutcome>) fetchGitFetchFileConfigAndRearrangeTheParamOrPatchesManifestOutcome(
            ambiance, paramManifestOutcomes, k8sManifestOutcome, gitFetchFilesConfigs);

    K8sStepPassThroughData k8sStepPassThroughData =
        K8sStepPassThroughData.builder()
            .manifestOutcome(k8sManifestOutcome)
            .manifestOutcomeList(new ArrayList<>(orderedOpenshiftParamManifests))
            .infrastructure(infrastructure)
            .helmValuesFileMapContents(helmChartValuesFileContentMap)
            .customFetchContent(customFetchContent)
            .zippedManifestFileId(zippedManifestId)
            .build();

    return getGitFetchFileTaskChainResponse(
        ambiance, gitFetchFilesConfigs, stepElementParameters, k8sStepPassThroughData, false, false);
  }

  public TaskChainResponse prepareOpenshiftParamFetchTask(Ambiance ambiance,
      StepElementParameters stepElementParameters, List<OpenshiftParamManifestOutcome> openshiftParamManifests,
      K8sStepPassThroughData k8sStepPassThroughData) {
    List<GitFetchFilesConfig> gitFetchFilesConfigs = new ArrayList<>();
    List<OpenshiftParamManifestOutcome> orderedOpenshiftParamManifests =
        (List<OpenshiftParamManifestOutcome>) fetchGitFetchFileConfigAndRearrangeTheParamOrPatchesManifestOutcome(
            ambiance, openshiftParamManifests, k8sStepPassThroughData.getManifestOutcome(), gitFetchFilesConfigs);

    List<ManifestOutcome> stepOverrides = getStepLevelManifestOutcomes(stepElementParameters);

    if (!isEmpty(stepOverrides)) {
      for (ManifestOutcome manifestOutcome : stepOverrides) {
        if (ManifestStoreType.isInGitSubset(manifestOutcome.getStore().getKind())) {
          gitFetchFilesConfigs.add(getGitFetchFilesConfig(
              ambiance, manifestOutcome.getStore(), manifestOutcome.getIdentifier(), manifestOutcome));
          orderedOpenshiftParamManifests.add((OpenshiftParamManifestOutcome) manifestOutcome);
        } else if (ManifestStoreType.INLINE.equals(manifestOutcome.getStore().getKind())) {
          orderedOpenshiftParamManifests.add((OpenshiftParamManifestOutcome) manifestOutcome);
        }
      }
    }

    K8sStepPassThroughData updatedK8sStepPassThroughData =
        k8sStepPassThroughData.toBuilder().manifestOutcomeList(new ArrayList<>(orderedOpenshiftParamManifests)).build();

    return getGitFetchFileTaskChainResponse(ambiance, gitFetchFilesConfigs, stepElementParameters,
        updatedK8sStepPassThroughData,
        isEmpty(k8sStepPassThroughData.getManifestFiles())
            && isEmpty(k8sStepPassThroughData.getLocalStoreFileMapContents()),
        false);
  }

  private List<? extends ManifestOutcome> fetchGitFetchFileConfigAndRearrangeTheParamOrPatchesManifestOutcome(
      Ambiance ambiance, List<? extends ManifestOutcome> aggregateManifestOutcome, ManifestOutcome k8sManifestOutcome,
      List<GitFetchFilesConfig> gitFetchFilesConfigs) {
    LinkedList<ManifestOutcome> orderedAggregateManifestOutcome = new LinkedList<>(aggregateManifestOutcome);
    String manifestType = k8sManifestOutcome.getType();
    ManifestOutcome overrideManifestOutcomes;
    String yamlId;
    switch (manifestType) {
      case ManifestType.OpenshiftTemplate:
        overrideManifestOutcomes = OpenshiftParamManifestOutcome.builder()
                                       .identifier(k8sManifestOutcome.getIdentifier())
                                       .store(extractStoreConfigFromManifestOutcome(k8sManifestOutcome))
                                       .build();
        yamlId = PARAM_YAML_ID;
        break;

      case ManifestType.Kustomize:
        overrideManifestOutcomes = KustomizePatchesManifestOutcome.builder()
                                       .identifier(k8sManifestOutcome.getIdentifier())
                                       .store(extractStoreConfigFromManifestOutcome(k8sManifestOutcome))
                                       .build();
        yamlId = PATCH_YAML_ID;
        break;

      default:
        throw new UnsupportedOperationException(format("Unsupported Manifest type: [%s]", manifestType));
    }

    orderedAggregateManifestOutcome.addFirst(overrideManifestOutcomes);
    if (ManifestStoreType.isInGitSubset(k8sManifestOutcome.getStore().getKind())) {
      List<GitFetchFilesConfig> gitFetchFilesConfigsFromOpenshiftOrKustomizeManifest =
          mapOpenshiftParamOrKustomizePatchesManifestToGitFetchFileConfig(ambiance, k8sManifestOutcome);
      if (isNotEmpty(gitFetchFilesConfigsFromOpenshiftOrKustomizeManifest)) {
        gitFetchFilesConfigs.addAll(gitFetchFilesConfigsFromOpenshiftOrKustomizeManifest);
      }
    }
    for (ManifestOutcome individualManifestOutcome : aggregateManifestOutcome) {
      String validationMessage = format(yamlId, individualManifestOutcome.getIdentifier());
      if (ManifestStoreType.isInGitSubset(individualManifestOutcome.getStore().getKind())) {
        GitFetchFilesConfig gitFetchFilesConfig = getGitFetchFilesConfig(
            ambiance, individualManifestOutcome.getStore(), validationMessage, individualManifestOutcome);
        gitFetchFilesConfigs.add(gitFetchFilesConfig);
      } else if (ManifestStoreType.InheritFromManifest.equals(individualManifestOutcome.getStore().getKind())) {
        GitFetchFilesConfig gitFetchFilesConfig = getPathsFromInheritFromManifestStoreConfig(
            ambiance, validationMessage, individualManifestOutcome, (GitStoreConfig) k8sManifestOutcome.getStore());
        gitFetchFilesConfigs.add(gitFetchFilesConfig);
      }
    }
    return orderedAggregateManifestOutcome;
  }

  public TaskChainResponse prepareKustomizePatchesFetchTask(Ambiance ambiance, K8sStepExecutor k8sStepExecutor,
      StepElementParameters stepElementParameters, List<KustomizePatchesManifestOutcome> kustomizePatchesManifests,
      K8sStepPassThroughData k8sStepPassThroughData) {
    ManifestOutcome k8sManifestOutcome = k8sStepPassThroughData.getManifestOutcome();
    StoreConfig storeConfig = k8sManifestOutcome.getStore();
    if (ManifestStoreType.isInGitSubset(storeConfig.getKind())
        || ManifestStoreType.HARNESS.equals(storeConfig.getKind())) {
      return prepareGitFetchPatchesTaskChainResponse(
          ambiance, stepElementParameters, kustomizePatchesManifests, k8sStepPassThroughData);
    }

    return k8sStepExecutor.executeK8sTask(k8sManifestOutcome, ambiance, stepElementParameters, emptyList(),
        K8sExecutionPassThroughData.builder()
            .infrastructure(k8sStepPassThroughData.getInfrastructure())
            .manifestFiles(k8sStepPassThroughData.getManifestFiles())
            .build(),
        true, null);
  }

  public TaskChainResponse prepareValuesFetchTask(Ambiance ambiance, K8sStepExecutor k8sStepExecutor,
      StepElementParameters stepElementParameters, List<ValuesManifestOutcome> aggregatedValuesManifests,
      List<ManifestOutcome> manifestOutcomes, K8sStepPassThroughData k8sStepPassThroughData) {
    ManifestOutcome k8sManifestOutcome = k8sStepPassThroughData.getManifestOutcome();
    StoreConfig storeConfig = extractStoreConfigFromManifestOutcome(k8sManifestOutcome);

    if (shouldExecuteCustomFetchTask(storeConfig, manifestOutcomes)) {
      return prepareCustomFetchManifestAndValuesTaskChainResponse(
          storeConfig, ambiance, stepElementParameters, manifestOutcomes, k8sStepPassThroughData);
    }

    K8sStepPassThroughData deepCopyOfK8sPassThroughData =
        k8sStepPassThroughData.toBuilder().customFetchContent(emptyMap()).zippedManifestFileId("").build();

    if (ManifestStoreType.isInGitSubset(storeConfig.getKind())
        || ManifestStoreType.HARNESS.equals(storeConfig.getKind())) {
      ValuesManifestOutcome valuesManifestOutcome =
          ValuesManifestOutcome.builder().identifier(k8sManifestOutcome.getIdentifier()).store(storeConfig).build();
      return prepareGitFetchValuesTaskChainResponse(ambiance, stepElementParameters, valuesManifestOutcome,
          aggregatedValuesManifests, deepCopyOfK8sPassThroughData, storeConfig,
          isEmpty(k8sStepPassThroughData.getLocalStoreFileMapContents())
              && isEmpty(k8sStepPassThroughData.getManifestFiles()),
          false);
    }

    if (ManifestType.HelmChart.equals(k8sManifestOutcome.getType())) {
      return prepareHelmFetchValuesTaskChainResponse(
          ambiance, stepElementParameters, aggregatedValuesManifests, deepCopyOfK8sPassThroughData);
    }

    return k8sStepExecutor.executeK8sTask(k8sManifestOutcome, ambiance, stepElementParameters, emptyList(),
        K8sExecutionPassThroughData.builder()
            .infrastructure(k8sStepPassThroughData.getInfrastructure())
            .manifestFiles(Arrays.asList(ManifestFiles.builder().build()))
            .build(),
        true, null);
  }

  private TaskChainResponse prepareGitFetchPatchesTaskChainResponse(Ambiance ambiance,
      StepElementParameters stepElementParameters, List<KustomizePatchesManifestOutcome> kustomizePatchesManifests,
      K8sStepPassThroughData k8sStepPassThroughData) {
    List<GitFetchFilesConfig> gitFetchFilesConfigs = new ArrayList<>();
    ManifestOutcome k8sManifestOutcome = k8sStepPassThroughData.getManifestOutcome();
    List<KustomizePatchesManifestOutcome> orderedPatchesManifests =
        (List<KustomizePatchesManifestOutcome>) fetchGitFetchFileConfigAndRearrangeTheParamOrPatchesManifestOutcome(
            ambiance, kustomizePatchesManifests, k8sManifestOutcome, gitFetchFilesConfigs);
    List<ManifestOutcome> stepOverrides = getStepLevelManifestOutcomes(stepElementParameters);

    if (!isEmpty(stepOverrides)) {
      for (ManifestOutcome manifestOutcome : stepOverrides) {
        if (ManifestStoreType.isInGitSubset(manifestOutcome.getStore().getKind())) {
          gitFetchFilesConfigs.add(getGitFetchFilesConfig(
              ambiance, manifestOutcome.getStore(), manifestOutcome.getIdentifier(), manifestOutcome));
          orderedPatchesManifests.add((KustomizePatchesManifestOutcome) manifestOutcome);
        } else if (ManifestStoreType.INLINE.equals(manifestOutcome.getStore().getKind())) {
          orderedPatchesManifests.add((KustomizePatchesManifestOutcome) manifestOutcome);
        }
      }
    }
    K8sStepPassThroughData updatedK8sStepPassThroughData =
        k8sStepPassThroughData.toBuilder().manifestOutcomeList(new LinkedList<>(orderedPatchesManifests)).build();

    return getGitFetchFileTaskChainResponse(ambiance, gitFetchFilesConfigs, stepElementParameters,
        updatedK8sStepPassThroughData,
        isEmpty(k8sStepPassThroughData.getManifestFiles())
            && isEmpty(k8sStepPassThroughData.getLocalStoreFileMapContents()),
        false);
  }

  private List<GitFetchFilesConfig> mapOpenshiftParamOrKustomizePatchesManifestToGitFetchFileConfig(
      Ambiance ambiance, ManifestOutcome manifestOutcome) {
    List<GitFetchFilesConfig> gitFetchFilesConfigList = new ArrayList<>();
    String validationMessage;
    List<String> overridePaths;
    if (ManifestType.OpenshiftTemplate.equals(manifestOutcome.getType())) {
      validationMessage = format(PARAM_YAML_ID, manifestOutcome.getIdentifier());
      OpenshiftManifestOutcome openshiftManifestOutcome = (OpenshiftManifestOutcome) manifestOutcome;
      overridePaths = getParameterFieldValue(openshiftManifestOutcome.getParamsPaths());
    } else if (ManifestType.Kustomize.equals(manifestOutcome.getType())) {
      validationMessage = format(PATCH_YAML_ID, manifestOutcome.getIdentifier());
      KustomizeManifestOutcome kustomizeManifestOutcome = (KustomizeManifestOutcome) manifestOutcome;
      overridePaths = getParameterFieldValue(kustomizeManifestOutcome.getPatchesPaths());
    } else {
      throw new UnsupportedOperationException(format("Unsupported Manifest type: [%s]", manifestOutcome.getType()));
    }
    StoreConfig store = manifestOutcome.getStore();
    GitStoreConfig gitStoreConfig = (GitStoreConfig) store;
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);
    cdStepHelper.validateManifest(store.getKind(), connectorDTO, validationMessage);
    populateGitFetchFilesConfigListWithValuesPaths(gitFetchFilesConfigList, gitStoreConfig, manifestOutcome,
        connectorDTO, ambiance, manifestOutcome.getIdentifier(), false, overridePaths);
    return gitFetchFilesConfigList;
  }

  private List<GitFetchFilesConfig> mapK8sOrHelmValuesManifestToGitFetchFileConfig(
      ValuesManifestOutcome valuesManifestOutcome, Ambiance ambiance, ManifestOutcome k8sManifestOutcome) {
    String validationMessage = format("Values YAML with Id [%s]", valuesManifestOutcome.getIdentifier());
    return getValuesGitFetchFilesConfig(ambiance, valuesManifestOutcome.getIdentifier(),
        valuesManifestOutcome.getStore(), validationMessage, k8sManifestOutcome);
  }

  public TaskChainResponse startChainLink(
      K8sStepExecutor k8sStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters) {
    ManifestsOutcome manifestsOutcome = resolveManifestsOutcome(ambiance);
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    ExpressionEvaluatorUtils.updateExpressions(
        manifestsOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));
    cdStepHelper.validateManifestsOutcome(ambiance, manifestsOutcome);

    ManifestOutcome k8sManifestOutcome = getK8sSupportedManifestOutcome(manifestsOutcome.values());
    K8sStepPassThroughData k8sStepPassThroughData = K8sStepPassThroughData.builder()
                                                        .manifestOutcome(k8sManifestOutcome)
                                                        .infrastructure(infrastructureOutcome)
                                                        .build();

    if (ManifestType.Kustomize.equals(k8sManifestOutcome.getType())) {
      List<KustomizePatchesManifestOutcome> kustomizePatchesManifests =
          getKustomizePatchesManifests(getOrderedManifestOutcome(manifestsOutcome.values()));
      return prepareKustomizeTemplateWithPatchesManifest(
          ambiance, k8sStepExecutor, kustomizePatchesManifests, stepElementParameters, k8sStepPassThroughData);
    }

    if (VALUES_YAML_SUPPORTED_MANIFEST_TYPES.contains(k8sManifestOutcome.getType())) {
      return prepareK8sOrHelmWithValuesManifests(ambiance, k8sStepExecutor,
          getOrderedManifestOutcome(manifestsOutcome.values()), stepElementParameters, k8sStepPassThroughData);
    } else {
      return prepareOcTemplateWithOcParamManifests(k8sStepExecutor,
          getOrderedManifestOutcome(manifestsOutcome.values()), ambiance, stepElementParameters,
          k8sStepPassThroughData);
    }
  }

  public ManifestsOutcome resolveManifestsOutcome(Ambiance ambiance) {
    OptionalOutcome manifestsOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    if (!manifestsOutcome.isFound()) {
      String stageName = AmbianceUtils.getStageLevelFromAmbiance(ambiance)
                             .map(level -> level.getIdentifier())
                             .orElse("Deployment stage");
      String stepType =
          Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance)).map(StepType::getType).orElse("Kubernetes");
      throw new GeneralException(format(
          "No manifests found in stage %s. %s step requires at least one manifest defined in stage service definition",
          stageName, stepType));
    }

    return (ManifestsOutcome) manifestsOutcome.getOutcome();
  }

  private TaskChainResponse prepareOcTemplateWithOcParamManifests(K8sStepExecutor k8sStepExecutor,
      List<ManifestOutcome> manifestOutcomes, Ambiance ambiance, StepElementParameters stepElementParameters,
      K8sStepPassThroughData k8sStepPassThroughData) {
    List<OpenshiftParamManifestOutcome> openshiftParamManifests = getOpenshiftParamManifests(manifestOutcomes);
    ManifestOutcome k8sManifestOutcome = k8sStepPassThroughData.getManifestOutcome();
    List<ManifestFiles> manifestFiles = new ArrayList<>();
    Map<String, LocalStoreFetchFilesResult> localStoreFileMapContents = new HashMap<>();

    if (ManifestStoreType.HARNESS.equals(k8sStepPassThroughData.getManifestOutcome().getStore().getKind())
        || isAnyLocalStore(openshiftParamManifests)) {
      localStoreFileMapContents.putAll(fetchFilesFromLocalStore(
          ambiance, k8sStepPassThroughData.getManifestOutcome(), openshiftParamManifests, manifestFiles));
    }

    K8sStepPassThroughData updatedK8sStepPassThroughData = k8sStepPassThroughData.toBuilder()
                                                               .localStoreFileMapContents(localStoreFileMapContents)
                                                               .manifestFiles(manifestFiles)
                                                               .build();
    StoreConfig storeConfig = extractStoreConfigFromManifestOutcome(k8sManifestOutcome);

    if (shouldExecuteCustomFetchTask(storeConfig, manifestOutcomes)) {
      return prepareCustomFetchManifestAndValuesTaskChainResponse(
          storeConfig, ambiance, stepElementParameters, manifestOutcomes, updatedK8sStepPassThroughData);
    }

    return prepareOpenshiftParamFetchTask(
        ambiance, stepElementParameters, openshiftParamManifests, updatedK8sStepPassThroughData);
  }

  private TaskChainResponse prepareKustomizeTemplateWithPatchesManifest(Ambiance ambiance,
      K8sStepExecutor k8sStepExecutor, List<KustomizePatchesManifestOutcome> kustomizePatchesManifests,
      StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData) {
    List<ManifestFiles> manifestFiles = new ArrayList<>();
    Map<String, LocalStoreFetchFilesResult> localStoreFileMapContents = new HashMap<>();

    if (ManifestStoreType.HARNESS.equals(k8sStepPassThroughData.getManifestOutcome().getStore().getKind())
        || isAnyLocalStore(kustomizePatchesManifests)) {
      localStoreFileMapContents.putAll(fetchFilesFromLocalStore(
          ambiance, k8sStepPassThroughData.getManifestOutcome(), kustomizePatchesManifests, manifestFiles));
    }
    K8sStepPassThroughData updatedK8sStepPassThroughData = k8sStepPassThroughData.toBuilder()
                                                               .localStoreFileMapContents(localStoreFileMapContents)
                                                               .manifestFiles(manifestFiles)
                                                               .build();

    return prepareKustomizePatchesFetchTask(
        ambiance, k8sStepExecutor, stepElementParameters, kustomizePatchesManifests, updatedK8sStepPassThroughData);
  }

  private TaskChainResponse prepareK8sOrHelmWithValuesManifests(Ambiance ambiance, K8sStepExecutor k8sStepExecutor,
      List<ManifestOutcome> manifestOutcomes, StepElementParameters stepElementParameters,
      K8sStepPassThroughData k8sStepPassThroughData) {
    List<ValuesManifestOutcome> aggregatedValuesManifests = getAggregatedValuesManifests(manifestOutcomes);
    List<ManifestFiles> manifestFiles = new ArrayList<>();
    Map<String, LocalStoreFetchFilesResult> localStoreFileMapContents = new HashMap<>();

    if (ManifestStoreType.HARNESS.equals(k8sStepPassThroughData.getManifestOutcome().getStore().getKind())
        || isAnyLocalStore(aggregatedValuesManifests)) {
      localStoreFileMapContents.putAll(fetchFilesFromLocalStore(
          ambiance, k8sStepPassThroughData.getManifestOutcome(), aggregatedValuesManifests, manifestFiles));
    }
    K8sStepPassThroughData updatedK8sStepPassThroughData = k8sStepPassThroughData.toBuilder()
                                                               .localStoreFileMapContents(localStoreFileMapContents)
                                                               .manifestFiles(manifestFiles)
                                                               .build();

    return prepareValuesFetchTask(ambiance, k8sStepExecutor, stepElementParameters, aggregatedValuesManifests,
        valuesAndParamsManifestOutcomes(manifestOutcomes), updatedK8sStepPassThroughData);
  }

  @VisibleForTesting
  public ManifestOutcome getK8sSupportedManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> k8sManifests =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> K8S_SUPPORTED_MANIFEST_TYPES.contains(manifestOutcome.getType()))
            .collect(Collectors.toList());
    if (isEmpty(k8sManifests)) {
      throw new InvalidRequestException(
          "Manifests are mandatory for K8s step. Select one from " + String.join(", ", K8S_SUPPORTED_MANIFEST_TYPES),
          USER);
    }

    if (k8sManifests.size() > 1) {
      throw new InvalidRequestException(
          "There can be only a single manifest. Select one from " + String.join(", ", K8S_SUPPORTED_MANIFEST_TYPES),
          USER);
    }
    return k8sManifests.get(0);
  }

  public List<KustomizePatchesManifestOutcome> getKustomizePatchesManifests(
      @NotEmpty List<ManifestOutcome> manifestOutcomeList) {
    List<KustomizePatchesManifestOutcome> kustomizePatchesManifests = new ArrayList<>();

    List<KustomizePatchesManifestOutcome> servicePatchesManifests =
        manifestOutcomeList.stream()
            .filter(manifestOutcome -> ManifestType.KustomizePatches.equals(manifestOutcome.getType()))
            .map(manifestOutcome -> (KustomizePatchesManifestOutcome) manifestOutcome)
            .collect(Collectors.toList());

    if (isNotEmpty(servicePatchesManifests)) {
      kustomizePatchesManifests.addAll(servicePatchesManifests);
    }
    return kustomizePatchesManifests;
  }

  @VisibleForTesting
  public List<OpenshiftParamManifestOutcome> getOpenshiftParamManifests(
      @NotEmpty List<ManifestOutcome> manifestOutcomeList) {
    List<OpenshiftParamManifestOutcome> openshiftParamManifests = new ArrayList<>();

    List<OpenshiftParamManifestOutcome> serviceParamsManifests =
        manifestOutcomeList.stream()
            .filter(manifestOutcome -> ManifestType.OpenshiftParam.equals(manifestOutcome.getType()))
            .map(manifestOutcome -> (OpenshiftParamManifestOutcome) manifestOutcome)
            .collect(Collectors.toList());

    if (isNotEmpty(serviceParamsManifests)) {
      openshiftParamManifests.addAll(serviceParamsManifests);
    }
    return openshiftParamManifests;
  }

  private List<String> getPatchesFileContentsForLocalStore(
      List<KustomizePatchesManifestOutcome> kustomizePatchesManifests) {
    // TODO: implement when local store is available
    return emptyList();
  }

  private List<ManifestOutcome> getOrderedManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    return manifestOutcomes.stream()
        .sorted(Comparator.comparingInt(ManifestOutcome::getOrder))
        .collect(Collectors.toCollection(LinkedList::new));
  }

  public TaskChainResponse executeNextLink(K8sStepExecutor k8sStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    K8sStepPassThroughData k8sStepPassThroughData = (K8sStepPassThroughData) passThroughData;
    ManifestOutcome k8sManifest = k8sStepPassThroughData.getManifestOutcome();
    ResponseData responseData = responseDataSupplier.get();
    UnitProgressData unitProgressData = null;

    try {
      if (responseData instanceof GitFetchResponse) {
        unitProgressData = ((GitFetchResponse) responseData).getUnitProgressData();
        return handleGitFetchFilesResponse(
            responseData, k8sStepExecutor, ambiance, stepElementParameters, k8sStepPassThroughData, k8sManifest);
      }

      if (responseData instanceof HelmValuesFetchResponse) {
        unitProgressData = ((HelmValuesFetchResponse) responseData).getUnitProgressData();
        return handleHelmValuesFetchResponse(
            responseData, k8sStepExecutor, ambiance, stepElementParameters, k8sStepPassThroughData, k8sManifest);
      }

      if (responseData instanceof CustomManifestValuesFetchResponse) {
        unitProgressData = ((CustomManifestValuesFetchResponse) responseData).getUnitProgressData();
        return handleCustomFetchResponse(
            responseData, k8sStepExecutor, ambiance, stepElementParameters, k8sStepPassThroughData, k8sManifest);
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

    return k8sStepExecutor.executeK8sTask(k8sManifest, ambiance, stepElementParameters, emptyList(),
        K8sExecutionPassThroughData.builder()
            .infrastructure(k8sStepPassThroughData.getInfrastructure())
            .manifestFiles(Arrays.asList(ManifestFiles.builder().build()))
            .build(),
        true, unitProgressData);
  }

  private TaskChainResponse handleGitFetchFilesResponse(ResponseData responseData, K8sStepExecutor k8sStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData,
      ManifestOutcome k8sManifest) {
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
    String helmValuesYamlContent = k8sStepPassThroughData.getHelmValuesFileContent();
    if (isNotEmpty(helmValuesYamlContent)) {
      valuesFileContents.add(helmValuesYamlContent);
    }
    Map<String, FetchFilesResult> gitFetchFilesResultMap = gitFetchResponse.getFilesFromMultipleRepo();
    Map<String, HelmFetchFileResult> helmChartValuesFilesResultMap =
        k8sStepPassThroughData.getHelmValuesFileMapContents();
    addValuesFileFromHelmChartManifest(helmChartValuesFilesResultMap, valuesFileContents, k8sManifest.getIdentifier());

    Map<String, Collection<CustomSourceFile>> customFetchContent = k8sStepPassThroughData.getCustomFetchContent();
    addValuesFilesFromCustomFetch(customFetchContent, valuesFileContents, k8sManifest.getIdentifier());

    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap =
        k8sStepPassThroughData.getLocalStoreFileMapContents();
    if (isNotEmpty(gitFetchFilesResultMap) || isNotEmpty(helmChartValuesFilesResultMap)
        || isNotEmpty(customFetchContent) || isNotEmpty(localStoreFetchFilesResultMap)) {
      valuesFileContents.addAll(
          getManifestFilesContents(gitFetchFilesResultMap, k8sStepPassThroughData.getManifestOutcomeList(),
              helmChartValuesFilesResultMap, customFetchContent, localStoreFetchFilesResultMap));
    }

    return k8sStepExecutor.executeK8sTask(k8sManifest, ambiance, stepElementParameters, valuesFileContents,
        K8sExecutionPassThroughData.builder()
            .infrastructure(k8sStepPassThroughData.getInfrastructure())
            .lastActiveUnitProgressData(gitFetchResponse.getUnitProgressData())
            .zippedManifestId(k8sStepPassThroughData.getZippedManifestFileId())
            .manifestFiles(k8sStepPassThroughData.getManifestFiles())
            .build(),
        false, gitFetchResponse.getUnitProgressData());
  }

  private TaskChainResponse handleCustomFetchResponse(ResponseData responseData, K8sStepExecutor k8sStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData,
      ManifestOutcome k8sManifest) {
    CustomManifestValuesFetchResponse customManifestValuesFetchResponse =
        (CustomManifestValuesFetchResponse) responseData;
    if (customManifestValuesFetchResponse.getCommandExecutionStatus() != SUCCESS) {
      if (customManifestValuesFetchResponse.getCommandExecutionStatus() != SUCCESS) {
        CustomFetchResponsePassThroughData customFetchResponsePassThroughData =
            CustomFetchResponsePassThroughData.builder()
                .errorMsg(customManifestValuesFetchResponse.getErrorMessage())
                .unitProgressData(customManifestValuesFetchResponse.getUnitProgressData())
                .build();
        return TaskChainResponse.builder().chainEnd(true).passThroughData(customFetchResponsePassThroughData).build();
      }
    }

    K8sStepPassThroughData updatedK8sStepPassThroughData =
        k8sStepPassThroughData.toBuilder()
            .customFetchContent(customManifestValuesFetchResponse.getValuesFilesContentMap())
            .zippedManifestFileId(customManifestValuesFetchResponse.getZippedManifestFileId())
            .build();

    // in case of OpenShift Template
    if (ManifestType.OpenshiftTemplate.equals(k8sManifest.getType())) {
      List<OpenshiftParamManifestOutcome> openshiftParamManifestOutcomes = new ArrayList<>();

      for (OpenshiftParamManifestOutcome openshiftParamManifestOutcome :
          k8sStepPassThroughData.getOpenShiftParamsOutcomes()) {
        if (ManifestStoreType.isInGitSubset(openshiftParamManifestOutcome.getStore().getKind())) {
          openshiftParamManifestOutcomes.add(openshiftParamManifestOutcome);
        }
      }

      List<ManifestOutcome> stepOverrides = getStepLevelManifestOutcomes(stepElementParameters);
      if (!isEmpty(stepOverrides)) {
        for (ManifestOutcome manifestOutcome : stepOverrides) {
          openshiftParamManifestOutcomes.add((OpenshiftParamManifestOutcome) manifestOutcome);
        }
      }

      return executeOpenShiftParamsTask(ambiance, stepElementParameters, k8sStepPassThroughData.getInfrastructure(),
          k8sStepPassThroughData.getManifestOutcome(), openshiftParamManifestOutcomes, emptyMap(),
          customManifestValuesFetchResponse.getValuesFilesContentMap(),
          customManifestValuesFetchResponse.getZippedManifestFileId());
    }

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
          ambiance, stepElementParameters, aggregatedValuesManifest, emptyMap(), updatedK8sStepPassThroughData, false);
    }

    if (ManifestType.HelmChart.equals(k8sManifest.getType())) {
      return prepareHelmFetchValuesTaskChainResponse(ambiance, stepElementParameters,
          k8sStepPassThroughData.getValuesManifestOutcomes(), updatedK8sStepPassThroughData);
    }

    StoreConfig storeConfig = extractStoreConfigFromManifestOutcome(k8sManifest);

    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().identifier(k8sManifest.getIdentifier()).store(storeConfig).build();

    return prepareGitFetchValuesTaskChainResponse(ambiance, stepElementParameters, valuesManifestOutcome,
        k8sStepPassThroughData.getValuesManifestOutcomes(), updatedK8sStepPassThroughData, storeConfig, false, false);
  }

  private TaskChainResponse handleHelmValuesFetchResponse(ResponseData responseData, K8sStepExecutor k8sStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, K8sStepPassThroughData k8sStepPassThroughData,
      ManifestOutcome k8sManifest) {
    HelmValuesFetchResponse helmValuesFetchResponse = (HelmValuesFetchResponse) responseData;
    if (helmValuesFetchResponse.getCommandExecutionStatus() != SUCCESS) {
      HelmValuesFetchResponsePassThroughData helmValuesFetchPassTroughData =
          HelmValuesFetchResponsePassThroughData.builder()
              .errorMsg(helmValuesFetchResponse.getErrorMessage())
              .unitProgressData(helmValuesFetchResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().chainEnd(true).passThroughData(helmValuesFetchPassTroughData).build();
    }

    String k8sManifestIdentifier = k8sManifest.getIdentifier();
    String valuesFileContent = helmValuesFetchResponse.getValuesFileContent();
    Map<String, HelmFetchFileResult> helmValuesFetchFilesResultMap =
        helmValuesFetchResponse.getHelmChartValuesFileMapContent();
    if (isNotEmpty(valuesFileContent)) {
      helmValuesFetchFilesResultMap = new HashMap<>();
      helmValuesFetchFilesResultMap.put(k8sManifestIdentifier,
          HelmFetchFileResult.builder().valuesFileContents(new ArrayList<>(Arrays.asList(valuesFileContent))).build());
    }

    List<ValuesManifestOutcome> aggregatedValuesManifest = new ArrayList<>();
    aggregatedValuesManifest.addAll(k8sStepPassThroughData.getValuesManifestOutcomes());
    List<ManifestOutcome> stepOverrides = getStepLevelManifestOutcomes(stepElementParameters);
    if (!isEmpty(stepOverrides)) {
      for (ManifestOutcome manifestOutcome : stepOverrides) {
        aggregatedValuesManifest.add((ValuesManifestOutcome) manifestOutcome);
      }
    }
    if (isNotEmpty(aggregatedValuesManifest)) {
      return executeValuesFetchTask(ambiance, stepElementParameters, aggregatedValuesManifest,
          helmValuesFetchResponse.getHelmChartValuesFileMapContent(), k8sStepPassThroughData, false);
    } else {
      List<String> valuesFileContents = new ArrayList<>();
      addValuesFileFromHelmChartManifest(helmValuesFetchFilesResultMap, valuesFileContents, k8sManifestIdentifier);
      return k8sStepExecutor.executeK8sTask(k8sManifest, ambiance, stepElementParameters, valuesFileContents,
          K8sExecutionPassThroughData.builder()
              .zippedManifestId(k8sStepPassThroughData.getZippedManifestFileId())
              .infrastructure(k8sStepPassThroughData.getInfrastructure())
              .lastActiveUnitProgressData(helmValuesFetchResponse.getUnitProgressData())
              .manifestFiles(Arrays.asList(ManifestFiles.builder().build()))
              .build(),
          false, helmValuesFetchResponse.getUnitProgressData());
    }
  }

  public static String getErrorMessage(K8sDeployResponse k8sDeployResponse) {
    return k8sDeployResponse.getErrorMessage() == null ? "" : k8sDeployResponse.getErrorMessage();
  }

  public static StepResponseBuilder getFailureResponseBuilder(
      K8sDeployResponse k8sDeployResponse, StepResponseBuilder stepResponseBuilder) {
    stepResponseBuilder.status(Status.FAILED)
        .failureInfo(
            FailureInfo.newBuilder().setErrorMessage(K8sStepHelper.getErrorMessage(k8sDeployResponse)).build());
    return stepResponseBuilder;
  }

  public boolean getSkipResourceVersioning(ManifestOutcome manifestOutcome) {
    switch (manifestOutcome.getType()) {
      case ManifestType.K8Manifest:
        K8sManifestOutcome k8sManifestOutcome = (K8sManifestOutcome) manifestOutcome;
        return CDStepHelper.getParameterFieldBooleanValue(k8sManifestOutcome.getSkipResourceVersioning(),
            K8sManifestOutcomeKeys.skipResourceVersioning, k8sManifestOutcome);

      case ManifestType.HelmChart:
        HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestOutcome;
        return CDStepHelper.getParameterFieldBooleanValue(helmChartManifestOutcome.getSkipResourceVersioning(),
            HelmChartManifestOutcomeKeys.skipResourceVersioning, helmChartManifestOutcome);

      case ManifestType.Kustomize:
        KustomizeManifestOutcome kustomizeManifestOutcome = (KustomizeManifestOutcome) manifestOutcome;
        return CDStepHelper.getParameterFieldBooleanValue(kustomizeManifestOutcome.getSkipResourceVersioning(),
            KustomizeManifestOutcomeKeys.skipResourceVersioning, kustomizeManifestOutcome);

      case ManifestType.OpenshiftTemplate:
        OpenshiftManifestOutcome openshiftManifestOutcome = (OpenshiftManifestOutcome) manifestOutcome;
        return CDStepHelper.getParameterFieldBooleanValue(openshiftManifestOutcome.getSkipResourceVersioning(),
            OpenshiftManifestOutcomeKeys.skipResourceVersioning, openshiftManifestOutcome);

      default:
        return false;
    }
  }

  public StepResponse handleTaskException(
      Ambiance ambiance, K8sExecutionPassThroughData executionPassThroughData, Exception e) throws Exception {
    // Trying to figure out if exception is coming from k8s task or it is an exception from delegate service.
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
          ambiance, K8sReleaseDetailsInfo.builder().releaseName(releaseName).build(), RELEASE_NAME);
    }
  }

  public void addValuesFileFromHelmChartManifest(Map<String, HelmFetchFileResult> helmChartValuesResultMap,
      List<String> valuesFileContents, String k8sManifestIdentifier) {
    if (isNotEmpty(helmChartValuesResultMap) && helmChartValuesResultMap.containsKey(k8sManifestIdentifier)) {
      List<String> baseValuesFileContent = helmChartValuesResultMap.get(k8sManifestIdentifier).getValuesFileContents();
      if (isNotEmpty(baseValuesFileContent)) {
        valuesFileContents.addAll(baseValuesFileContent);
      }
    }
  }

  public List<KubernetesResourceId> getPrunedResourcesIds(
      String accountId, List<KubernetesResourceId> prunedResourceIds) {
    if (cdStepHelper.isPruningEnabled(accountId)) {
      return prunedResourceIds == null ? Collections.emptyList() : prunedResourceIds;
    }
    return Collections.emptyList();
  }
}
