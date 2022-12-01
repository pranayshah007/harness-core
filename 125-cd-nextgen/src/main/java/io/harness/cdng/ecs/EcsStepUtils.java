/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.beans.FileReference;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.delegate.task.localstore.LocalStoreFetchFilesResult;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EcsStepUtils extends CDStepHelper {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private EcsEntityHelper ecsEntityHelper;
  @Inject private FileStoreService fileStoreService;

  public GitStoreDelegateConfig getGitStoreDelegateConfig(
      Ambiance ambiance, GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome) {
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    String validationMessage = format("Ecs manifest with Id [%s]", manifestOutcome.getIdentifier());
    ConnectorInfoDTO connectorDTO = getConnectorDTO(connectorId, ambiance);
    validateManifest(gitStoreConfig.getKind(), connectorDTO, validationMessage);
    return getGitStoreDelegateConfig(
        gitStoreConfig, connectorDTO, manifestOutcome, gitStoreConfig.getPaths().getValue(), ambiance);
  }

  public GitStoreDelegateConfig getGitStoreDelegateConfigForRunTask(
      Ambiance ambiance, ManifestOutcome manifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) manifestOutcome.getStore();
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    String validationMessage = format("Ecs run task configuration");
    ConnectorInfoDTO connectorDTO = getConnectorDTO(connectorId, ambiance);
    validateManifest(gitStoreConfig.getKind(), connectorDTO, validationMessage);

    return getGitStoreDelegateConfig(
        gitStoreConfig, connectorDTO, manifestOutcome, gitStoreConfig.getPaths().getValue(), ambiance);
  }

  public boolean isAnyGitManifest(List<ManifestOutcome> ecsManifestsOutcomes) {
    Boolean isGitManifest = false;
    for (ManifestOutcome manifest : ecsManifestsOutcomes) {
      if (ManifestStoreType.isInGitSubset(manifest.getStore().getKind())) {
        isGitManifest = true;
      }
    }
    return isGitManifest;
  }

  public S3StoreDelegateConfig getS3StoreDelegateConfig(
      Ambiance ambiance, S3StoreConfig s3StoreConfig, ManifestOutcome manifestOutcome) {
    String connectorId = s3StoreConfig.getConnectorRef().getValue();
    String validationMessage = format("Ecs manifest with Id [%s]", manifestOutcome.getIdentifier());
    ConnectorInfoDTO connectorDTO = getConnectorDTO(connectorId, ambiance);
    validateManifest(s3StoreConfig.getKind(), connectorDTO, validationMessage);
    return getS3StoreDelegateConfig(s3StoreConfig, connectorDTO, ambiance);
  }

  private ConnectorInfoDTO getConnectorDTO(String connectorId, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return ecsEntityHelper.getConnectorInfoDTO(connectorId, ngAccess);
  }

  public List<String> fetchFilesContentFromLocalStore(
      Ambiance ambiance, ManifestOutcome manifestOutcome, LogCallback logCallback) {
    Map<String, LocalStoreFetchFilesResult> localStoreFileMapContents = new HashMap<>();
    LocalStoreFetchFilesResult localStoreFetchFilesResult = null;
    logCallback.saveExecutionLog(color(
        format("%nFetching %s from Harness File Store", manifestOutcome.getType()), LogColor.White, LogWeight.Bold));
    if (ManifestStoreType.HARNESS.equals(manifestOutcome.getStore().getKind())) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      localStoreFetchFilesResult = getFileContentsFromManifestOutcome(manifestOutcome, ngAccess, logCallback);
      localStoreFileMapContents.put(manifestOutcome.getType(), localStoreFetchFilesResult);
    }
    return localStoreFileMapContents.get(manifestOutcome.getType()).getLocalStoreFileContents();
  }

  private LocalStoreFetchFilesResult getFileContentsFromManifestOutcome(
      ManifestOutcome manifestOutcome, NGAccess ngAccess, LogCallback logCallback) {
    HarnessStore localStoreConfig = (HarnessStore) manifestOutcome.getStore();
    List<String> scopedFilePathList = localStoreConfig.getFiles().getValue();
    return getFileContentsFromManifest(
        ngAccess, scopedFilePathList, manifestOutcome.getType(), manifestOutcome.getIdentifier(), logCallback);
  }

  private LocalStoreFetchFilesResult getFileContentsFromManifest(NGAccess ngAccess, List<String> scopedFilePathList,
      String manifestType, String manifestIdentifier, LogCallback logCallback) {
    List<String> fileContents = new ArrayList<>();
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
          if (isNotEmpty(file.getContent())) {
            fileContents.add(file.getContent());
          } else {
            throw new InvalidRequestException(
                format("The following file %s in Harness File Store has empty content", scopedFilePath));
          }
          logCallback.saveExecutionLog(color(format("- %s", scopedFilePath), LogColor.White));
        } else {
          throw new UnsupportedOperationException("Only File type is supported. Please enter the correct file path");
        }
      }
    }
    return LocalStoreFetchFilesResult.builder().LocalStoreFileContents(fileContents).build();
  }

  private Optional<FileStoreNodeDTO> validateAndFetchFileFromHarnessStore(
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

  public boolean areAllManifestsFromHarnessFileStore(List<? extends ManifestOutcome> manifestOutcomes) {
    boolean retVal = true;
    for (ManifestOutcome manifestOutcome : manifestOutcomes) {
      retVal = retVal && ManifestStoreType.HARNESS.equals(manifestOutcome.getStore().getKind());
    }
    return retVal;
  }

  private void printFilesFetchedFromHarnessStore(List<String> scopedFilePathList, LogCallback logCallback) {
    for (String scopedFilePath : scopedFilePathList) {
      logCallback.saveExecutionLog(color(format("- %s", scopedFilePath), LogColor.White));
    }
  }

  public UnitProgressData getCommandUnitProgressData(
      String commandName, CommandExecutionStatus commandExecutionStatus) {
    LinkedHashMap<String, CommandUnitProgress> commandUnitProgressMap = new LinkedHashMap<>();
    CommandUnitProgress commandUnitProgress = CommandUnitProgress.builder().status(commandExecutionStatus).build();
    commandUnitProgressMap.put(commandName, commandUnitProgress);
    CommandUnitsProgress commandUnitsProgress =
        CommandUnitsProgress.builder().commandUnitProgressMap(commandUnitProgressMap).build();
    return UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress);
  }
}
