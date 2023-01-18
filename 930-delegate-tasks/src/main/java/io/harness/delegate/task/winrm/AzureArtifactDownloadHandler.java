/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.utils.AzureArtifactsUtils.getAuthHeader;
import static io.harness.delegate.utils.AzureArtifactsUtils.getAzureArtifactDelegateConfig;
import static io.harness.delegate.utils.AzureArtifactsUtils.getDecryptedToken;
import static io.harness.delegate.utils.AzureArtifactsUtils.getDownloadUrl;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.azure.artifact.AzureArtifactsHelper;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.ScriptType;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class AzureArtifactDownloadHandler implements ArtifactDownloadHandler {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private AzureArtifactsHelper azureArtifactsHelper;

  @Override
  public String getCommandString(
      SshWinRmArtifactDelegateConfig artifactDelegateConfig, String destinationPath, ScriptType scriptType) {
    AzureArtifactDelegateConfig azureArtifactDelegateConfig = getAzureArtifactDelegateConfig(artifactDelegateConfig);

    String artifactFileName = azureArtifactsHelper.getArtifactFileName(azureArtifactDelegateConfig);
    String downloadUrl = getDownloadUrl(artifactFileName, azureArtifactDelegateConfig);
    if (ScriptType.POWERSHELL.equals(scriptType)) {
      return getPowerShellCommand(destinationPath, artifactFileName, azureArtifactDelegateConfig, downloadUrl);
    } else if (ScriptType.BASH.equals(scriptType)) {
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
    return "$Headers = @{\n"
        + "    Authorization = \""
        + getAuthHeader(getDecryptedToken(azureArtifactDelegateConfig, secretDecryptionService)) + "\"\n"
        + "}\n [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12"
        + "\n $ProgressPreference = 'SilentlyContinue'"
        + "\n Invoke-WebRequest -Uri \"" + downloadUrl + "\" -Headers $Headers -OutFile \"" + destinationPath + "\\"
        + artifactFileName + "\"";
  }
}
