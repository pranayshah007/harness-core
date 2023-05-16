/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.delegate.task.winrm.DownloadWinRmScript.AUTHORIZATION;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.DOWNLOAD_ARTIFACT_BY_PROXY_PS;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.DOWNLOAD_ARTIFACT_USING_CREDENTIALS_BY_PROXY_PS;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.OUT_FILE;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.URI;
import static io.harness.delegate.utils.ArtifactoryUtils.getArtifactConfigRequest;
import static io.harness.delegate.utils.ArtifactoryUtils.getArtifactFileName;
import static io.harness.delegate.utils.ArtifactoryUtils.getArtifactoryUrl;
import static io.harness.delegate.utils.ArtifactoryUtils.getAuthHeader;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.ScriptType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@Singleton
public class ArtifactoryArtifactDownloadHandler implements ArtifactDownloadHandler {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private ArtifactoryRequestMapper artifactoryRequestMapper;

  @Override
  public String getCommandString(
      SshWinRmArtifactDelegateConfig artifactDelegateConfig, String destinationPath, ScriptType scriptType) {
    ArtifactoryArtifactDelegateConfig artifactoryArtifactDelegateConfig =
        (ArtifactoryArtifactDelegateConfig) artifactDelegateConfig;
    ArtifactoryConfigRequest artifactoryConfigRequest =
        getArtifactConfigRequest(artifactoryArtifactDelegateConfig, secretDecryptionService, artifactoryRequestMapper);

    String artifactPath = Paths
                              .get(artifactoryArtifactDelegateConfig.getRepositoryName(),
                                  artifactoryArtifactDelegateConfig.getArtifactPath())
                              .toString();
    String artifactFileName = getArtifactFileName(artifactPath);

    if (ScriptType.POWERSHELL.equals(scriptType)) {
      return getPowerShellCommand(destinationPath, artifactoryConfigRequest, artifactPath, artifactFileName);
    } else if (ScriptType.BASH.equals(scriptType)) {
      return getSshCommand(destinationPath, artifactoryConfigRequest, artifactPath, artifactFileName);
    } else {
      throw new InvalidRequestException("Unknown script type.");
    }
  }

  private String getSshCommand(String destinationPath, ArtifactoryConfigRequest artifactoryConfigRequest,
      String artifactPath, String artifactFileName) {
    if (!artifactoryConfigRequest.isHasCredentials()) {
      return "curl -L --fail -X GET \"" + getArtifactoryUrl(artifactoryConfigRequest, artifactPath) + "\" -o \""
          + destinationPath + "/" + artifactFileName + "\"";
    } else {
      return "curl -L --fail -H \"Authorization: " + getAuthHeader(artifactoryConfigRequest) + "\" -X GET \""
          + getArtifactoryUrl(artifactoryConfigRequest, artifactPath) + "\" -o \"" + destinationPath + "/"
          + artifactFileName + "\"";
    }
  }

  @NotNull
  private String getPowerShellCommand(String destinationPath, ArtifactoryConfigRequest artifactoryConfigRequest,
      String artifactPath, String artifactFileName) {
    if (artifactoryConfigRequest.isHasCredentials()) {
      return DOWNLOAD_ARTIFACT_USING_CREDENTIALS_BY_PROXY_PS
          .replace(AUTHORIZATION, getAuthHeader(artifactoryConfigRequest))
          .replace(URI, getArtifactoryUrl(artifactoryConfigRequest, artifactPath))
          .replace(OUT_FILE, destinationPath + "\\" + artifactFileName);
    } else {
      return DOWNLOAD_ARTIFACT_BY_PROXY_PS.replace(URI, getArtifactoryUrl(artifactoryConfigRequest, artifactPath))
          .replace(OUT_FILE, destinationPath + "\\" + artifactFileName);
    }
  }
}
