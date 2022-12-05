/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.beans.FileReference;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.elastigroup.config.StartupScriptOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.delegate.task.localstore.LocalStoreFetchFilesResult;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ElastigroupStepUtils extends CDStepHelper {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private FileStoreService fileStoreService;

  public List<String> fetchFilesContentFromLocalStore(
      Ambiance ambiance, StartupScriptOutcome startupScriptOutcome, LogCallback logCallback) {
    Map<String, LocalStoreFetchFilesResult> localStoreFileMapContents = new HashMap<>();
    LocalStoreFetchFilesResult localStoreFetchFilesResult = null;

    logCallback.saveExecutionLog(
        color(format("%nFetching %s from Harness File Store", "startupScript"), LogColor.White, LogWeight.Bold));
    if (ManifestStoreType.HARNESS.equals(startupScriptOutcome.getStore().getKind())) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      localStoreFetchFilesResult = getFileContentsFromStartupScriptOutcome(startupScriptOutcome, ngAccess, logCallback);
      localStoreFileMapContents.put("startupScript", localStoreFetchFilesResult);
    }
    return localStoreFileMapContents.get("startupScript").getLocalStoreFileContents();
  }

  private LocalStoreFetchFilesResult getFileContentsFromStartupScriptOutcome(
      StartupScriptOutcome startupScriptOutcome, NGAccess ngAccess, LogCallback logCallback) {
    HarnessStore localStoreConfig = (HarnessStore) startupScriptOutcome.getStore();
    List<String> scopedFilePathList = localStoreConfig.getFiles().getValue();
    return getFileContents(ngAccess, scopedFilePathList, "startupScript", logCallback);
  }

  private LocalStoreFetchFilesResult getFileContents(
      NGAccess ngAccess, List<String> scopedFilePathList, String manifestType, LogCallback logCallback) {
    List<String> fileContents = new ArrayList<>();
    if (isNotEmpty(scopedFilePathList)) {
      logCallback.saveExecutionLog(color(format("%nFetching %s files", manifestType), LogColor.White, LogWeight.Bold));
      logCallback.saveExecutionLog(color(format("Fetching following Files :"), LogColor.White));
      printFilesFetchedFromHarnessStore(scopedFilePathList, logCallback);
      logCallback.saveExecutionLog(
          color(format("Successfully fetched following files: "), LogColor.White, LogWeight.Bold));
      for (String scopedFilePath : scopedFilePathList) {
        Optional<FileStoreNodeDTO> valuesFile = validateAndFetchFileFromHarnessStore(scopedFilePath, ngAccess);
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

  private Optional<FileStoreNodeDTO> validateAndFetchFileFromHarnessStore(String scopedFilePath, NGAccess ngAccess) {
    if (isBlank(scopedFilePath)) {
      throw new InvalidRequestException(format("File reference cannot be null or empty"));
    }
    FileReference fileReference = FileReference.of(
        scopedFilePath, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    Optional<FileStoreNodeDTO> manifestFile =
        fileStoreService.getWithChildrenByPath(fileReference.getAccountIdentifier(), fileReference.getOrgIdentifier(),
            fileReference.getProjectIdentifier(), fileReference.getPath(), true);
    if (!manifestFile.isPresent()) {
      throw new InvalidRequestException(format("File/Folder not found in File Store with path: [%s], scope: [%s]",
          fileReference.getPath(), fileReference.getScope()));
    }
    return manifestFile;
  }

  private void printFilesFetchedFromHarnessStore(List<String> scopedFilePathList, LogCallback logCallback) {
    for (String scopedFilePath : scopedFilePathList) {
      logCallback.saveExecutionLog(color(format("- %s", scopedFilePath), LogColor.White));
    }
  }
}
