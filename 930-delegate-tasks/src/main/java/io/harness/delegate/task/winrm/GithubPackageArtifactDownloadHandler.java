/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.AUTHORIZATION;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.DOWNLOAD_ARTIFACT_BY_PROXY_PS;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.DOWNLOAD_ARTIFACT_USING_CREDENTIALS_BY_PROXY_PS;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.OUT_FILE;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.URI;
import static io.harness.delegate.utils.GithubPackageUtils.getGithubPackagesArtifactDelegateConfig;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.task.artifacts.mappers.GithubPackagesRequestResponseMapper;
import io.harness.delegate.task.ssh.artifact.GithubPackagesArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.utils.GithubPackageUtils;
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
public class GithubPackageArtifactDownloadHandler implements ArtifactDownloadHandler {
  private SecretDecryptionService secretDecryptionService;

  @Override
  public String getCommandString(
      SshWinRmArtifactDelegateConfig artifactDelegateConfig, String destinationPath, ScriptType scriptType) {
    GithubPackagesArtifactDelegateConfig githubPackagesArtifactDelegateConfig =
        getGithubPackagesArtifactDelegateConfig(artifactDelegateConfig);

    GithubConnectorDTO githubConnectorDTO =
        (GithubConnectorDTO) githubPackagesArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    GithubPackageUtils.decryptRequestDTOs(
        githubConnectorDTO, githubPackagesArtifactDelegateConfig.getEncryptedDataDetails(), secretDecryptionService);
    GithubPackagesInternalConfig githubPackagesInternalConfig =
        GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(githubConnectorDTO);

    String artifactName = GithubPackageUtils.getArtifactFileName(
        githubPackagesArtifactDelegateConfig.getPackageType(), githubPackagesArtifactDelegateConfig.getMetadata());
    String artifactUrl = githubPackagesArtifactDelegateConfig.getArtifactUrl();

    if (ScriptType.BASH == scriptType) {
      return generateBashCommandScript(githubPackagesInternalConfig, artifactUrl, artifactName, destinationPath);
    } else if (ScriptType.POWERSHELL == scriptType) {
      return generatePowerShellCommandScript(githubPackagesInternalConfig, artifactUrl, artifactName, destinationPath);
    }

    throw new InvalidRequestException(format("Unsupported Nexus script type, %s", scriptType), USER);
  }

  private String generateBashCommandScript(GithubPackagesInternalConfig githubPackagesInternalConfig,
      final String artifactUrl, final String artifactName, final String destinationPath) {
    StringBuilder command = new StringBuilder(128);
    if (githubPackagesInternalConfig.hasCredentials()) {
      command.append("curl --fail -H \"Authorization: token ")
          .append(githubPackagesInternalConfig.getToken())
          .append("\" -X GET \"")
          .append(artifactUrl)
          .append("\" -o \"")
          .append(destinationPath.trim())
          .append('/')
          .append(artifactName)
          .append("\"\n");
    } else {
      command.append("curl --fail -X GET \"")
          .append(artifactUrl)
          .append("\" -o \"")
          .append(destinationPath.trim())
          .append('/')
          .append(artifactName)
          .append("\"\n");
    }

    return command.toString();
  }

  private String generatePowerShellCommandScript(GithubPackagesInternalConfig githubPackagesInternalConfig,
      final String artifactUrl, final String artifactName, final String destinationPath) {
    if (githubPackagesInternalConfig.hasCredentials()) {
      return DOWNLOAD_ARTIFACT_USING_CREDENTIALS_BY_PROXY_PS
          .replace(AUTHORIZATION, "token " + githubPackagesInternalConfig.getToken())
          .replace(URI, artifactUrl)
          .replace(OUT_FILE, destinationPath.trim() + "\\" + artifactName);
    } else {
      return DOWNLOAD_ARTIFACT_BY_PROXY_PS.replace(URI, artifactUrl)
          .replace(OUT_FILE, destinationPath.trim() + "\\" + artifactName);
    }
  }
}
