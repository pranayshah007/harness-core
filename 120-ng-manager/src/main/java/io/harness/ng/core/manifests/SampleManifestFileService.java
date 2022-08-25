/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.manifests;

import io.harness.FileStoreConstants;
import io.harness.beans.FeatureName;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.dto.EmbeddedUserDetailsDTO;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class SampleManifestFileService {
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject private FileStoreService fileStoreService;

  @Data
  @AllArgsConstructor
  public static class SampleManifestFileCreateResponse {
    boolean created;
    String errorMessage;
  }

  /**
   * @param accountIdentifier Account for which to create default files in the file store
   * Creates sample k8s manifests at the account level file store for the given account.
   */
  public SampleManifestFileCreateResponse createDefaultFilesInFileStore(String accountIdentifier) {
    final boolean shouldCreate =
        ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.NG_DEFAULT_K8S_MANIFESTS)
        && ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.NG_FILE_STORE);
    if (!shouldCreate) {
      return new SampleManifestFileCreateResponse(false,
          String.format("Please enable following feature flags %s %s", FeatureName.NG_DEFAULT_K8S_MANIFESTS.name(),
              FeatureName.NG_FILE_STORE.name()));
    }

    final String topLevelFolderName = "Sample K8s Manifests";
    final String templatesFolderName = "templates";
    final FileDTO topLevelFolder =
        buildFolderDTO(accountIdentifier, FileStoreConstants.ROOT_FOLDER_IDENTIFIER, topLevelFolderName);
    final FileDTO templatesFolder =
        buildFolderDTO(accountIdentifier, topLevelFolder.getIdentifier(), templatesFolderName);

    ensureFolder(topLevelFolder);
    ensureFolder(templatesFolder);

    final List<String> filesUnderTopLevelFolder = List.of("values.yaml");

    // Create files under top level folder
    {
      SampleManifestFileCreateResponse response = createFiles(
          accountIdentifier, filesUnderTopLevelFolder, topLevelFolder.getIdentifier(), Paths.get(topLevelFolderName));
      if (!response.isCreated()) {
        log.error(String.format("Files [%s] could not be created for account %s at account level file store",
            String.join(",", filesUnderTopLevelFolder), accountIdentifier));
        return response;
      }
    }

    final List<String> filesUnderTemplatesFolder = List.of("deployment.yaml", "namespace.yaml", "service.yaml");
    // Create files under templates level folder
    {
      SampleManifestFileCreateResponse response = createFiles(accountIdentifier, filesUnderTemplatesFolder,
          templatesFolder.getIdentifier(), Paths.get(topLevelFolderName, templatesFolderName));
      if (!response.isCreated()) {
        log.error(String.format("Files [%s] could not be created for account %s at account level file store",
            String.join(",", filesUnderTemplatesFolder), accountIdentifier));
        return response;
      }
    }

    return new SampleManifestFileCreateResponse(true, null);
  }

  private SampleManifestFileCreateResponse createFiles(
      String accountIdentifier, List<String> fileNames, String parentIdentifier, Path topLevelFolder) {
    for (String fileName : fileNames) {
      try {
        upsertFile(accountIdentifier, fileName, topLevelFolder, parentIdentifier);
      } catch (IOException ex) {
        return new SampleManifestFileCreateResponse(false, ex.getMessage());
      }
    }
    return new SampleManifestFileCreateResponse(true, null);
  }

  private void upsertFile(String accountIdentifier, String fileName, Path parentFolderPath, String parentIdentifier)
      throws IOException {
    final String parentPath = parentFolderPath.toString();
    try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(
             Paths.get(parentPath, fileName).toString())) {
      FileDTO file = buildFileDTO(accountIdentifier, parentIdentifier, parentPath, fileName);
      if (fileStoreService.get(file).isEmpty()) {
        FileDTO fileDTO = fileStoreService.create(file, is);
        log.info(String.format("Successfully created file [%s] for account %s. FileDTO %s", fileName, accountIdentifier,
            fileDTO.toString()));
      } else {
        FileDTO fileDTO = fileStoreService.update(file, is);
        log.info(String.format("Successfully updated file [%s] for account %s. FileDTO %s", fileName, accountIdentifier,
            fileDTO.toString()));
      }
    }
  }

  private FileDTO buildFileDTO(
      String accountIdentifier, String parentIdentifier, String parentFolder, String fileName) {
    return FileDTO.builder()
        .type(NGFileType.FILE)
        .accountIdentifier(accountIdentifier)
        .identifier(fileName.replace(".", "_").replace("-", "_").toLowerCase(Locale.ENGLISH))
        .parentIdentifier(parentIdentifier)
        .name(fileName)
        .fileUsage(FileUsage.MANIFEST_FILE)
        .path(parentFolder + fileName)
        .description("Sample k8s manifest file created by Harness")
        .createdBy(EmbeddedUserDetailsDTO.builder().name("Harness").build())
        .build();
  }

  private void ensureFolder(FileDTO file) {
    if (fileStoreService.get(file).isEmpty()) {
      fileStoreService.create(file, null);
      log.info(String.format("Successfully created folder [%s] for account %s. FileDTO %s", file.getIdentifier(),
          file.getAccountIdentifier(), file));
    } else {
      fileStoreService.update(file, null);
      log.info(String.format("Folder %s already exists for account %s at account level file store",
          file.getIdentifier(), file.getAccountIdentifier()));
    }
  }

  private FileDTO buildFolderDTO(String accountIdentifier, String parentIdentifier, String folderName) {
    String identifier = folderName.replace(" ", "_").replace("-", "_").replace("/", "").toLowerCase(Locale.ENGLISH);
    return FileDTO.builder()
        .type(NGFileType.FOLDER)
        .identifier(identifier)
        .accountIdentifier(accountIdentifier)
        .parentIdentifier(parentIdentifier)
        .name(folderName)
        .path(folderName)
        .description("Sample k8s manifest folder created by Harness")
        .build();
  }
}
