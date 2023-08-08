/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;
import static io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig.toInternalConfig;
import static io.harness.delegate.utils.AzureArtifactsUtils.getDecryptedToken;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsInternalConfig;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsProtocolType;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsDownloadHelper;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsRegistryService;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageFileInfo;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AzureArtifactsHelper {
  @Inject private AzureArtifactsRegistryService azureArtifactsRegistryService;
  @Inject private SecretDecryptionService secretDecryptionService;

  public String getArtifactFileName(AzureArtifactDelegateConfig azureArtifactDelegateConfig) {
    String artifactFileName;
    if (AzureArtifactsProtocolType.upack.name().equals(azureArtifactDelegateConfig.getPackageType())) {
      artifactFileName = azureArtifactDelegateConfig.getPackageName();
    } else {
      AzureArtifactsInternalConfig azureArtifactsInternalConfig = toInternalConfig(
          azureArtifactDelegateConfig, getDecryptedToken(azureArtifactDelegateConfig, secretDecryptionService));
      List<AzureArtifactsPackageFileInfo> files = azureArtifactsRegistryService.listPackageFiles(
          azureArtifactsInternalConfig, azureArtifactDelegateConfig.getProject(), azureArtifactDelegateConfig.getFeed(),
          azureArtifactDelegateConfig.getPackageType(), azureArtifactDelegateConfig.getPackageName(),
          azureArtifactDelegateConfig.getVersion());
      artifactFileName = files.stream()
                             .map(AzureArtifactsPackageFileInfo::getName)
                             .filter(AzureArtifactsDownloadHelper::shouldDownloadFile)
                             .findFirst()
                             .orElse(null);
    }

    if (isBlank(artifactFileName)) {
      String message = "No file available for downloading the package " + azureArtifactDelegateConfig.getPackageName();
      throw NestedExceptionUtils.hintWithExplanationException(HintException.HINT_AZURE_ARTIFACT_NOT_FOUND_FOR_PACKAGE,
          ExplanationException.AZURE_ARTIFACT_NOT_FOUND_FOR_PACKAGE,
          new InvalidRequestException(message, WingsException.USER));
    }
    return artifactFileName;
  }
}
