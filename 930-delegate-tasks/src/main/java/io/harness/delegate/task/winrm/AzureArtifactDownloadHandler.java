/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.AUTHORIZATION;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.AZURE_ARTIFACTS_URL;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.AZURE_UPACK_DOWNLOAD_ARTIFACT_BASH_ORG;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.AZURE_UPACK_DOWNLOAD_ARTIFACT_BASH_PROJ;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.AZURE_UPACK_DOWNLOAD_ARTIFACT_PS_ORG;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.AZURE_UPACK_DOWNLOAD_ARTIFACT_PS_PROJ;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.DESTINATION_PATH;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.DOWNLOAD_ARTIFACT_USING_CREDENTIALS_BY_PROXY_PS;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.FEED;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.OUT_FILE;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.PKG_NAME;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.PKG_VERSION;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.PROJECT;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.TOKEN;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.URI;
import static io.harness.delegate.utils.AzureArtifactsUtils.getAuthHeader;
import static io.harness.delegate.utils.AzureArtifactsUtils.getAzureArtifactDelegateConfig;
import static io.harness.delegate.utils.AzureArtifactsUtils.getDecryptedToken;
import static io.harness.delegate.utils.AzureArtifactsUtils.getDownloadUrl;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsProtocolType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.task.azure.artifact.AzureArtifactsHelper;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.ScriptType;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class AzureArtifactDownloadHandler implements ArtifactDownloadHandler {
  public static final String PROJECT_SCOPE = "project";

  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private AzureArtifactsHelper azureArtifactsHelper;

  @Override
  public String getCommandString(
      SshWinRmArtifactDelegateConfig artifactDelegateConfig, String destinationPath, ScriptType scriptType) {
    AzureArtifactDelegateConfig azureArtifactDelegateConfig = getAzureArtifactDelegateConfig(artifactDelegateConfig);

    String artifactFileName = azureArtifactsHelper.getArtifactFileName(azureArtifactDelegateConfig);
    if (ScriptType.POWERSHELL.equals(scriptType)) {
      if (AzureArtifactsProtocolType.upack.name().equals(azureArtifactDelegateConfig.getPackageType())) {
        return PROJECT_SCOPE.equals(azureArtifactDelegateConfig.getScope())
            ? getPowerShellCommandForUpackProjLevel(destinationPath, azureArtifactDelegateConfig)
            : getPowerShellCommandForUpackOrgLevel(destinationPath, azureArtifactDelegateConfig);
      }
      String downloadUrl = getDownloadUrl(artifactFileName, azureArtifactDelegateConfig);
      return getPowerShellCommand(destinationPath, artifactFileName, azureArtifactDelegateConfig, downloadUrl);
    } else if (ScriptType.BASH.equals(scriptType)) {
      if (AzureArtifactsProtocolType.upack.name().equals(azureArtifactDelegateConfig.getPackageType())) {
        return PROJECT_SCOPE.equals(azureArtifactDelegateConfig.getScope())
            ? getBashCommandForUpackProjLevel(destinationPath, azureArtifactDelegateConfig)
            : getBashCommandForUpackOrgLevel(destinationPath, azureArtifactDelegateConfig);
      }
      String downloadUrl = getDownloadUrl(artifactFileName, azureArtifactDelegateConfig);
      return getSshCommand(destinationPath, artifactFileName, azureArtifactDelegateConfig, downloadUrl);
    } else {
      throw new InvalidRequestException("Unknown script type.");
    }
  }

  private String getSshCommand(String destinationPath, String artifactFileName,
      AzureArtifactDelegateConfig azureArtifactDelegateConfig, String downloadUrl) {
    return "curl -L --fail -H \"Authorization: "
        + getAuthHeader(getDecryptedToken(azureArtifactDelegateConfig, secretDecryptionService)) + "\" -X GET \""
        + downloadUrl + "\" -o \"" + destinationPath + "/" + artifactFileName + "\"";
  }

  private String getPowerShellCommand(String destinationPath, String artifactFileName,
      AzureArtifactDelegateConfig azureArtifactDelegateConfig, String downloadUrl) {
    return DOWNLOAD_ARTIFACT_USING_CREDENTIALS_BY_PROXY_PS
        .replace(AUTHORIZATION, getAuthHeader(getDecryptedToken(azureArtifactDelegateConfig, secretDecryptionService)))
        .replace(URI, downloadUrl)
        .replace(OUT_FILE, destinationPath + "\\" + artifactFileName);
  }

  private String getBashCommandForUpackOrgLevel(
      String destinationPath, AzureArtifactDelegateConfig azureArtifactDelegateConfig) {
    return AZURE_UPACK_DOWNLOAD_ARTIFACT_BASH_ORG
        .replace(TOKEN, getDecryptedToken(azureArtifactDelegateConfig, secretDecryptionService))
        .replace(AZURE_ARTIFACTS_URL, getAzureArtifactsURL(azureArtifactDelegateConfig))
        .replace(FEED, azureArtifactDelegateConfig.getFeed())
        .replace(PKG_NAME, azureArtifactDelegateConfig.getPackageName())
        .replace(PKG_VERSION, azureArtifactDelegateConfig.getVersion())
        .replace(DESTINATION_PATH, destinationPath);
  }

  private String getBashCommandForUpackProjLevel(
      String destinationPath, AzureArtifactDelegateConfig azureArtifactDelegateConfig) {
    return AZURE_UPACK_DOWNLOAD_ARTIFACT_BASH_PROJ
        .replace(TOKEN, getDecryptedToken(azureArtifactDelegateConfig, secretDecryptionService))
        .replace(AZURE_ARTIFACTS_URL, getAzureArtifactsURL(azureArtifactDelegateConfig))
        .replace(PROJECT, azureArtifactDelegateConfig.getProject())
        .replace(FEED, azureArtifactDelegateConfig.getFeed())
        .replace(PKG_NAME, azureArtifactDelegateConfig.getPackageName())
        .replace(PKG_VERSION, azureArtifactDelegateConfig.getVersion())
        .replace(DESTINATION_PATH, destinationPath);
  }

  private String getPowerShellCommandForUpackOrgLevel(
      String destinationPath, AzureArtifactDelegateConfig azureArtifactDelegateConfig) {
    return AZURE_UPACK_DOWNLOAD_ARTIFACT_PS_ORG
        .replace(TOKEN, getDecryptedToken(azureArtifactDelegateConfig, secretDecryptionService))
        .replace(AZURE_ARTIFACTS_URL, getAzureArtifactsURL(azureArtifactDelegateConfig))
        .replace(FEED, azureArtifactDelegateConfig.getFeed())
        .replace(PKG_NAME, azureArtifactDelegateConfig.getPackageName())
        .replace(PKG_VERSION, azureArtifactDelegateConfig.getVersion())
        .replace(DESTINATION_PATH, destinationPath);
  }

  private String getPowerShellCommandForUpackProjLevel(
      String destinationPath, AzureArtifactDelegateConfig azureArtifactDelegateConfig) {
    return AZURE_UPACK_DOWNLOAD_ARTIFACT_PS_PROJ
        .replace(TOKEN, getDecryptedToken(azureArtifactDelegateConfig, secretDecryptionService))
        .replace(AZURE_ARTIFACTS_URL, getAzureArtifactsURL(azureArtifactDelegateConfig))
        .replace(PROJECT, azureArtifactDelegateConfig.getProject())
        .replace(FEED, azureArtifactDelegateConfig.getFeed())
        .replace(PKG_NAME, azureArtifactDelegateConfig.getPackageName())
        .replace(PKG_VERSION, azureArtifactDelegateConfig.getVersion())
        .replace(DESTINATION_PATH, destinationPath);
  }

  private String getAzureArtifactsURL(AzureArtifactDelegateConfig azureArtifactDelegateConfig) {
    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
        (AzureArtifactsConnectorDTO) azureArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    return azureArtifactsConnectorDTO.getAzureArtifactsUrl();
  }
}
