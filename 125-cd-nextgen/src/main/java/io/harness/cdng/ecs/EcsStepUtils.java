/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FileReference;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
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

  public boolean isAnyS3Manifest(List<ManifestOutcome> ecsManifestsOutcomes) {
    Boolean isS3Manifest = false;
    for (ManifestOutcome manifest : ecsManifestsOutcomes) {
      if (manifest.getStore() != null && ManifestStoreType.S3.equals(manifest.getStore().getKind())) {
        isS3Manifest = true;
      }
    }
    return isS3Manifest;
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
}
