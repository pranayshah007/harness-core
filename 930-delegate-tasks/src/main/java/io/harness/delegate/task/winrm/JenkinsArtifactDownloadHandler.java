/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.AUTHORIZATION;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.JENKINS_DOWNLOAD_ARTIFACT_PS;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.JENKINS_DOWNLOAD_ARTIFACT_USING_CREDENTIALS_PS;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.OUT_FILE;
import static io.harness.delegate.task.winrm.DownloadWinRmScript.URI;
import static io.harness.delegate.utils.ArtifactoryUtils.getArtifactFileName;
import static io.harness.delegate.utils.JenkinsArtifactsUtils.getJenkinsAuthHeader;
import static io.harness.delegate.utils.JenkinsArtifactsUtils.getJenkinsUrl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.task.ssh.artifact.JenkinsArtifactDelegateConfig;
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
public class JenkinsArtifactDownloadHandler implements ArtifactDownloadHandler {
  private static final String ARTIFACT = "artifact";

  @Inject private SecretDecryptionService secretDecryptionService;

  @Override
  public String getCommandString(
      SshWinRmArtifactDelegateConfig artifactDelegateConfig, String destinationPath, ScriptType scriptType) {
    JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig =
        (JenkinsArtifactDelegateConfig) artifactDelegateConfig;

    String artifactPath = Paths
                              .get(jenkinsArtifactDelegateConfig.getJobName(), jenkinsArtifactDelegateConfig.getBuild(),
                                  ARTIFACT, jenkinsArtifactDelegateConfig.getArtifactPath())
                              .toString();

    JenkinsConnectorDTO jenkinsConnectorDto =
        (JenkinsConnectorDTO) jenkinsArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();

    if (ScriptType.POWERSHELL.equals(scriptType)) {
      return getPowerShellCommand(destinationPath, jenkinsArtifactDelegateConfig, artifactPath, jenkinsConnectorDto);
    } else if (ScriptType.BASH.equals(scriptType)) {
      return getSshCommand(destinationPath, jenkinsArtifactDelegateConfig, artifactPath);
    } else {
      throw new InvalidRequestException("Unknown script type.");
    }
  }

  private String getSshCommand(
      String destinationPath, JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig, String artifactPath) {
    return "curl --fail -H \"Authorization: "
        + getJenkinsAuthHeader(jenkinsArtifactDelegateConfig, secretDecryptionService) + "\" -X GET \""
        + getJenkinsUrl(jenkinsArtifactDelegateConfig, artifactPath) + "\" -o \"" + destinationPath.trim() + '/'
        + getArtifactFileName(artifactPath) + "\"";
  }

  @NotNull
  private String getPowerShellCommand(String destinationPath,
      JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig, String artifactPath,
      JenkinsConnectorDTO jenkinsConnectorDto) {
    if (jenkinsConnectorDto.getAuth() != null) {
      String jenkinsAuthHeader = getJenkinsAuthHeader(jenkinsArtifactDelegateConfig, secretDecryptionService);
      return isNotEmpty(jenkinsAuthHeader)
          ? JENKINS_DOWNLOAD_ARTIFACT_USING_CREDENTIALS_PS.replace(AUTHORIZATION, jenkinsAuthHeader)
                .replace(URI, getJenkinsUrl(jenkinsArtifactDelegateConfig, artifactPath))
                .replace(OUT_FILE, destinationPath + "\\" + getArtifactFileName(artifactPath))
          : getJenkinsDownloadScript(destinationPath, jenkinsArtifactDelegateConfig, artifactPath);
    } else {
      return getJenkinsDownloadScript(destinationPath, jenkinsArtifactDelegateConfig, artifactPath);
    }
  }

  @NotNull
  private String getJenkinsDownloadScript(
      String destinationPath, JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig, String artifactPath) {
    return JENKINS_DOWNLOAD_ARTIFACT_PS.replace(URI, getJenkinsUrl(jenkinsArtifactDelegateConfig, artifactPath))
        .replace(OUT_FILE, destinationPath + "\\" + getArtifactFileName(artifactPath));
  }
}
