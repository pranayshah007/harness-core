/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pcf;

import static io.harness.cdng.manifest.ManifestType.PCF_SUPPORTED_MANIFEST_TYPES;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.filestore.utils.FileStoreNodeUtils.mapFileNodes;
import static io.harness.k8s.manifest.ManifestHelper.values_filename;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.beans.FileReference;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.K8sStepExecutor;
import io.harness.cdng.k8s.K8sStepPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftParamManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.task.localstore.LocalStoreFetchFilesResult;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.logging.LogCallback;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.validator.constraints.NotEmpty;

public class PcfStepHelper {
  @Inject protected OutcomeService outcomeService;
  private static final Set<String> VALUES_YAML_SUPPORTED_MANIFEST_TYPES =
      ImmutableSet.of(ManifestType.K8Manifest, ManifestType.HelmChart);
  @Inject private CDStepHelper cdStepHelper;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private FileStoreService fileStoreService;

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

  public TaskChainResponse startChainLink(Ambiance ambiance, StepElementParameters stepElementParameters) {
    ManifestsOutcome manifestsOutcome = resolveManifestsOutcome(ambiance);
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    ExpressionEvaluatorUtils.updateExpressions(
        manifestsOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));
    cdStepHelper.validateManifestsOutcome(ambiance, manifestsOutcome);

    ManifestOutcome pcfManifestOutcome = getPcfSupportedManifestOutcome(manifestsOutcome.values());
    PcfStepPassThroughData pcfStepPassThroughData = PcfStepPassThroughData.builder()
                                                        .manifestOutcome(pcfManifestOutcome)
                                                        .infrastructure(infrastructureOutcome)
                                                        .build();

    return preparePcfWithValuesManifests(
        ambiance, getOrderedManifestOutcome(manifestsOutcome.values()), stepElementParameters, pcfStepPassThroughData);
  }

  @VisibleForTesting
  public ManifestOutcome getPcfSupportedManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> pcfManifests =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> PCF_SUPPORTED_MANIFEST_TYPES.contains(manifestOutcome.getType()))
            .collect(Collectors.toList());
    if (isEmpty(pcfManifests)) {
      throw new InvalidRequestException(
          "Manifests are mandatory for PCF step. Select one from " + String.join(", ", PCF_SUPPORTED_MANIFEST_TYPES),
          USER);
    }

    if (pcfManifests.size() > 1) {
      throw new InvalidRequestException(
          "There can be only a single manifest. Select one from " + String.join(", ", PCF_SUPPORTED_MANIFEST_TYPES),
          USER);
    }
    return pcfManifests.get(0);
  }

  protected boolean isAnyLocalStore(@NotEmpty List<? extends ManifestOutcome> aggregatedValuesManifests) {
    return aggregatedValuesManifests.stream().anyMatch(
        valuesManifest -> ManifestStoreType.HARNESS.equals(valuesManifest.getStore().getKind()));
  }

  public static boolean shouldOpenFetchFilesStream(Boolean openFetchFilesStream) {
    return openFetchFilesStream == null;
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
          fileContents.add(file.getContent());
          logCallback.saveExecutionLog(color(format("- %s", scopedFilePath), LogColor.White));
        } else {
          throw new UnsupportedOperationException("Only File type is supported. Please enter the correct file path");
        }
      }
    }
    return LocalStoreFetchFilesResult.builder().LocalStoreFileContents(fileContents).build();
  }
  public void printFilesFetchedFromHarnessStore(List<String> scopedFilePathList, LogCallback logCallback) {
    for (String scopedFilePath : scopedFilePathList) {
      logCallback.saveExecutionLog(color(format("- %s", scopedFilePath), LogColor.White));
    }
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
      fileContents.add(((FileNodeDTO) baseValuesFile).getContent());
      logCallback.saveExecutionLog(color(format("%nSuccessfully fetched values.yaml file"), White));
    } catch (Exception ex) {
      logCallback.saveExecutionLog(
          color(format("No values.yaml found for manifest with identifier: %s.", manifestIdentifier), White));
    }

    return getFileContentsFromManifest(
        ngAccess, fileContents, scopedFilePathList, manifestOutcome.getType(), manifestIdentifier, logCallback);
  }

  private TaskChainResponse preparePcfWithValuesManifests(Ambiance ambiance, List<ManifestOutcome> manifestOutcomes,
      StepElementParameters stepElementParameters, PcfStepPassThroughData pcfStepPassThroughData) {
    List<ValuesManifestOutcome> aggregatedValuesManifests = getAggregatedValuesManifests(manifestOutcomes);
    List<ManifestFiles> manifestFiles = new ArrayList<>();
    Map<String, LocalStoreFetchFilesResult> localStoreFileMapContents = new HashMap<>();

    if (ManifestStoreType.HARNESS.equals(pcfStepPassThroughData.getManifestOutcome().getStore().getKind())
        || isAnyLocalStore(aggregatedValuesManifests)) {
      pcfStepPassThroughData.setShouldOpenFetchFilesStream(
          shouldOpenFetchFilesStream(pcfStepPassThroughData.getShouldOpenFetchFilesStream()));
      LogCallback logCallback = cdStepHelper.getLogCallback(
          K8sCommandUnitConstants.FetchFiles, ambiance, pcfStepPassThroughData.getShouldOpenFetchFilesStream());
      localStoreFileMapContents.putAll(fetchFilesFromLocalStore(ambiance, pcfStepPassThroughData.getManifestOutcome(),
          aggregatedValuesManifests, manifestFiles, logCallback));
    }
    PcfStepPassThroughData updatedPcfStepPassThroughData = pcfStepPassThroughData.toBuilder()
                                                               .localStoreFileMapContents(localStoreFileMapContents)
                                                               .manifestFiles(manifestFiles)
                                                               .build();

    return prepareValuesFetchTask(ambiance, stepElementParameters, aggregatedValuesManifests,
        valuesAndParamsManifestOutcomes(manifestOutcomes), updatedPcfStepPassThroughData);
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

  protected List<ManifestOutcome> valuesAndParamsManifestOutcomes(List<ManifestOutcome> manifestOutcomeList) {
    return manifestOutcomeList.stream()
        .filter(manifestOutcome
            -> ManifestType.VALUES.equals(manifestOutcome.getType())
                || ManifestType.OpenshiftParam.equals(manifestOutcome.getType()))
        .collect(Collectors.toList());
  }

  private List<ManifestOutcome> getOrderedManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    return manifestOutcomes.stream()
        .sorted(Comparator.comparingInt(ManifestOutcome::getOrder))
        .collect(Collectors.toCollection(LinkedList::new));
  }

  public TaskChainResponse prepareValuesFetchTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      List<ValuesManifestOutcome> aggregatedValuesManifests, List<ManifestOutcome> manifestOutcomes,
      PcfStepPassThroughData pcfStepPassThroughData) {
    ManifestOutcome k8sManifestOutcome = pcfStepPassThroughData.getManifestOutcome();
    StoreConfig storeConfig = extractStoreConfigFromManifestOutcome(k8sManifestOutcome);
    pcfStepPassThroughData.setShouldOpenFetchFilesStream(
        shouldOpenFetchFilesStream(pcfStepPassThroughData.getShouldOpenFetchFilesStream()));

    //        if (shouldExecuteCustomFetchTask(storeConfig, manifestOutcomes)) {
    //            return prepareCustomFetchManifestAndValuesTaskChainResponse(
    //                    storeConfig, ambiance, stepElementParameters, manifestOutcomes, pcfStepPassThroughData);
    //        }
    //
    //        PcfStepPassThroughData deepCopyOfK8sPassThroughData =
    //                pcfStepPassThroughData.toBuilder().customFetchContent(emptyMap()).zippedManifestFileId("").build();
    //        if (ManifestType.HelmChart.equals(k8sManifestOutcome.getType())
    //                && HELM_CHART_REPO_STORE_TYPES.contains(storeConfig.getKind())) {
    //            return prepareHelmFetchValuesTaskChainResponse(
    //                    ambiance, stepElementParameters, aggregatedValuesManifests, deepCopyOfK8sPassThroughData);
    //        }
    //
    //        ValuesManifestOutcome valuesManifestOutcome =
    //                ValuesManifestOutcome.builder().identifier(k8sManifestOutcome.getIdentifier()).store(storeConfig).build();
    //        if (ManifestStoreType.isInGitSubset(storeConfig.getKind())
    //                || shouldExecuteGitFetchTask(aggregatedValuesManifests)) {
    //            return prepareGitFetchValuesTaskChainResponse(ambiance, stepElementParameters, valuesManifestOutcome,
    //                    aggregatedValuesManifests, deepCopyOfK8sPassThroughData, storeConfig);
    //        }
    //        LinkedList<ManifestOutcome> orderedValuesManifests = new LinkedList<>(aggregatedValuesManifests);
    //        orderedValuesManifests.addFirst(valuesManifestOutcome);
    //        return executeK8sTask(ambiance, stepElementParameters,  pcfStepPassThroughData,
    //                orderedValuesManifests, k8sManifestOutcome);
    return null;
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
}
