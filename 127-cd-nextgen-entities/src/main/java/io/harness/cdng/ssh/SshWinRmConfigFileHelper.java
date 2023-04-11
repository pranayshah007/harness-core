/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filestore.utils.FileStoreNodeUtils.mapFileNodes;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileReference;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.delegate.task.ssh.config.SecretConfigFile;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
@OwnedBy(CDP)
public class SshWinRmConfigFileHelper {
  private static final long SSH_WIN_RM_CONFIG_FILE_SIZE_LIMIT = 15L * 1024 * 1024 /*15 MB */;

  @Inject private FileStoreService fileStoreService;
  @Inject private NGEncryptedDataService ngEncryptedDataService;
  @Inject private CDExpressionResolver cdExpressionResolver;

  public String fetchFileContent(FileReference fileReference) {
    Optional<FileStoreNodeDTO> file = fileStoreService.getWithChildrenByPath(fileReference.getAccountIdentifier(),
        fileReference.getOrgIdentifier(), fileReference.getProjectIdentifier(), fileReference.getPath(), true);

    if (!file.isPresent()) {
      throw new InvalidRequestException(format("File not found in local file store, path [%s], scope: [%s]",
          fileReference.getPath(), fileReference.getScope()));
    }

    FileStoreNodeDTO fileStoreNodeDTO = file.get();

    if (!(fileStoreNodeDTO instanceof FileNodeDTO)) {
      throw new InvalidRequestException(format(
          "Requested file is a folder, path [%s], scope: [%s]", fileReference.getPath(), fileReference.getScope()));
    }

    return ((FileNodeDTO) fileStoreNodeDTO).getContent();
  }

  public FileDelegateConfig getFileDelegateConfig(
      Map<String, ConfigFileOutcome> configFilesOutcome, Ambiance ambiance, boolean shouldRenderConfigFiles) {
    List<StoreDelegateConfig> stores = new ArrayList<>(configFilesOutcome.size());
    for (ConfigFileOutcome configFileOutcome : configFilesOutcome.values()) {
      StoreConfig storeConfig = configFileOutcome.getStore();
      if (storeConfig != null && HARNESS_STORE_TYPE.equals(storeConfig.getKind())) {
        stores.add(buildHarnessStoreDelegateConfig(ambiance, (HarnessStore) storeConfig, shouldRenderConfigFiles));
      }
    }

    return FileDelegateConfig.builder().stores(stores).build();
  }

  private HarnessStoreDelegateConfig buildHarnessStoreDelegateConfig(
      Ambiance ambiance, HarnessStore harnessStore, boolean shouldRenderConfigFiles) {
    harnessStore = (HarnessStore) cdExpressionResolver.updateExpressions(ambiance, harnessStore);
    List<String> files = ParameterFieldHelper.getParameterFieldValue(harnessStore.getFiles());
    List<String> secretFiles = ParameterFieldHelper.getParameterFieldValue(harnessStore.getSecretFiles());

    List<ConfigFileParameters> configFileParameters = new ArrayList<>();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    if (isNotEmpty(files)) {
      files.forEach(scopedFilePath -> {
        FileReference fileReference = FileReference.of(scopedFilePath, ngAccess.getAccountIdentifier(),
            ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

        configFileParameters.addAll(fetchConfigFileFromFileStore(fileReference));
      });
    }

    if (isNotEmpty(secretFiles)) {
      secretFiles.forEach(secretFileRef -> {
        IdentifierRef fileRef = IdentifierRefHelper.getIdentifierRef(secretFileRef, ngAccess.getAccountIdentifier(),
            ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

        configFileParameters.add(fetchSecretConfigFile(fileRef));
      });
    }

    if (shouldRenderConfigFiles) {
      renderConfigFilesParameters(ambiance, configFileParameters);
    }

    return HarnessStoreDelegateConfig.builder().configFiles(configFileParameters).build();
  }

  private List<ConfigFileParameters> renderConfigFilesParameters(
      Ambiance ambiance, List<ConfigFileParameters> configFileParameters) {
    for (ConfigFileParameters configFileParameter : configFileParameters) {
      if (configFileParameter.isEncrypted()) {
        // At this point we don't have secret content as it will be retrieved on delegate side, hence skipping rendering
        // for secret files. Generally we don't want to touch secret as it is not safe and might raise security
        // concerns.
        continue;
      }

      String renderedContent;
      try {
        renderedContent = cdExpressionResolver.renderExpression(ambiance, configFileParameter.getFileContent(), false);
      } catch (GeneralException generalException) {
        throw new InvalidRequestException(format("Failed to render config file %s, reason: %s",
            configFileParameter.getFileName(), generalException.getMessage()));
      }

      configFileParameter.setFileContent(renderedContent);
    }

    return configFileParameters;
  }

  private List<ConfigFileParameters> fetchConfigFileFromFileStore(FileReference fileReference) {
    Optional<FileStoreNodeDTO> configFile = fileStoreService.getWithChildrenByPath(fileReference.getAccountIdentifier(),
        fileReference.getOrgIdentifier(), fileReference.getProjectIdentifier(), fileReference.getPath(), true);

    if (!configFile.isPresent()) {
      throw new InvalidRequestException(format("Config file not found in local file store, path [%s], scope: [%s]",
          fileReference.getPath(), fileReference.getScope()));
    }

    FileStoreNodeDTO fileStoreNodeDTO = configFile.get();
    if (fileStoreNodeDTO.getType() != NGFileType.FILE) {
      throw new InvalidRequestException(format("Config file cannot be directory, path [%s], scope: [%s]",
          fileReference.getPath(), fileReference.getScope()));
    }

    if (((FileNodeDTO) fileStoreNodeDTO).getSize() > SSH_WIN_RM_CONFIG_FILE_SIZE_LIMIT) {
      throw new InvalidRequestException(format("Config file size is larger than maximum [%s], path [%s], scope: [%s]",
          SSH_WIN_RM_CONFIG_FILE_SIZE_LIMIT, fileReference.getPath(), fileReference.getScope()));
    }

    return mapFileNodes(fileStoreNodeDTO,
        fileNode
        -> ConfigFileParameters.builder()
               .fileContent(fileNode.getContent())
               .fileName(fileNode.getName())
               .fileSize(fileNode.getSize())
               .build());
  }

  private ConfigFileParameters fetchSecretConfigFile(IdentifierRef fileRef) {
    SecretConfigFile secretConfigFile =
        SecretConfigFile.builder()
            .encryptedConfigFile(SecretRefHelper.createSecretRef(fileRef.getIdentifier()))
            .build();

    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(fileRef.getAccountIdentifier())
                            .orgIdentifier(fileRef.getOrgIdentifier())
                            .projectIdentifier(fileRef.getProjectIdentifier())
                            .build();

    List<EncryptedDataDetail> encryptedDataDetails =
        ngEncryptedDataService.getEncryptionDetails(ngAccess, secretConfigFile);

    if (isEmpty(encryptedDataDetails)) {
      throw new InvalidRequestException(format("Secret file with identifier %s not found", fileRef.getIdentifier()));
    }

    return ConfigFileParameters.builder()
        .fileName(secretConfigFile.getEncryptedConfigFile().getIdentifier())
        .isEncrypted(true)
        .secretConfigFile(secretConfigFile)
        .encryptionDataDetails(encryptedDataDetails)
        .build();
  }
}
