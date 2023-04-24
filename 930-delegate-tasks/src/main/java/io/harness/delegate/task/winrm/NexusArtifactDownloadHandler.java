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
import static io.harness.delegate.utils.NexusUtils.getBasicAuthHeader;
import static io.harness.delegate.utils.NexusUtils.getNexusArtifactDelegateConfig;
import static io.harness.delegate.utils.NexusUtils.getNexusArtifactFileName;
import static io.harness.delegate.utils.NexusUtils.getNexusVersion;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.nexus.NexusMapper;
import io.harness.delegate.task.ssh.artifact.NexusArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.utils.NexusVersion;
import io.harness.exception.InvalidRequestException;
import io.harness.nexus.NexusRequest;
import io.harness.shell.ScriptType;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class NexusArtifactDownloadHandler implements ArtifactDownloadHandler {
  private NexusMapper nexusMapper;

  @Override
  public String getCommandString(
      SshWinRmArtifactDelegateConfig artifactDelegateConfig, String destinationPath, ScriptType scriptType) {
    NexusArtifactDelegateConfig nexusArtifactDelegateConfig = getNexusArtifactDelegateConfig(artifactDelegateConfig);
    NexusVersion nexusVersion = getNexusVersion(nexusArtifactDelegateConfig);
    NexusRequest nexusRequest = nexusMapper.toNexusRequest(nexusArtifactDelegateConfig);
    String artifactUrl = nexusArtifactDelegateConfig.getArtifactUrl();
    String artifactName = getNexusArtifactFileName(
        nexusVersion, nexusArtifactDelegateConfig.getRepositoryFormat(), nexusArtifactDelegateConfig.getMetadata());

    if (ScriptType.BASH == scriptType) {
      return generateBashCommandScript(nexusRequest, artifactUrl, artifactName, destinationPath);
    } else if (ScriptType.POWERSHELL == scriptType) {
      return generatePowerShellCommandScript(nexusRequest, artifactUrl, artifactName, destinationPath);
    }

    throw new InvalidRequestException(format("Unsupported Nexus script type, %s", scriptType), USER);
  }

  private String generateBashCommandScript(
      NexusRequest nexusRequest, final String artifactUrl, final String artifactName, final String destinationPath) {
    StringBuilder command = new StringBuilder(128);
    if (nexusRequest.isHasCredentials()) {
      String basicAuthHeader = getBasicAuthHeader(nexusRequest);
      command.append("curl --fail -H \"Authorization: ")
          .append(basicAuthHeader)
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

  private String generatePowerShellCommandScript(
      NexusRequest nexusRequest, final String artifactUrl, final String artifactName, final String destinationPath) {
    if (nexusRequest.isHasCredentials()) {
      return DOWNLOAD_ARTIFACT_USING_CREDENTIALS_BY_PROXY_PS.replace(AUTHORIZATION, getBasicAuthHeader(nexusRequest))
          .replace(URI, artifactUrl)
          .replace(OUT_FILE, destinationPath.trim() + "\\" + artifactName);
    } else {
      return DOWNLOAD_ARTIFACT_BY_PROXY_PS.replace(URI, artifactUrl)
          .replace(OUT_FILE, destinationPath.trim() + "\\" + artifactName);
    }
  }
}
