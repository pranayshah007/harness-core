/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.delegate.task.nexus.NexusMapper;
import io.harness.delegate.task.ssh.artifact.NexusArtifactDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.ScriptType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class NexusArtifactDownloadHandlerTest extends CategoryTest {
  private static final String ARTIFACT_URL =
      "https://nexus3.dev.harness.io/repository/maven-releases/mygroup/myartifact/1.8/myartifact-1.8.war";
  private static final String NEXUS_SERVER_URL = "https://nexus3.dev.harness.io/";
  private static final String NEXUS_VERSION = "3.1";
  private static final String NEXUS_USERNAME = "username";
  private static final String NEXUS_PWD_DECRYPTED_VALUE = "decryptedValue";

  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock DecryptableEntity decryptableEntity;
  @Mock LogCallback logCallback;
  @InjectMocks @Spy private NexusMapper nexusMapper;

  @InjectMocks private NexusArtifactDownloadHandler nexusArtifactDownloadHandler;

  @Before
  public void setup() {
    doReturn(decryptableEntity).when(secretDecryptionService).decrypt(any(), anyList());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetBashCommandString() {
    assertThat(nexusArtifactDownloadHandler.getCommandString(
                   getNexusArtifactDelegateConfig(NexusAuthType.USER_PASSWORD), "destinationPath", ScriptType.BASH))
        .isEqualTo(
            "curl --fail -H \"Authorization: Basic dXNlcm5hbWU6ZGVjcnlwdGVkVmFsdWU=\" -X GET \"https://nexus3.dev.harness.io/repository/maven-releases/mygroup/myartifact/1.8/myartifact-1.8.war\" -o \"destinationPath/myartifact-1.8.war\"\n");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetBashCommandString_NoCredentials() {
    NexusArtifactDelegateConfig nexusArtifactDelegateConfig = getNexusArtifactDelegateConfig(NexusAuthType.ANONYMOUS);

    assertThat(
        nexusArtifactDownloadHandler.getCommandString(nexusArtifactDelegateConfig, "destinationPath", ScriptType.BASH))
        .isEqualTo(
            "curl --fail -X GET \"https://nexus3.dev.harness.io/repository/maven-releases/mygroup/myartifact/1.8/myartifact-1.8.war\" -o \"destinationPath/myartifact-1.8.war\"\n");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetPowerShellCommandString() {
    assertThat(
        nexusArtifactDownloadHandler.getCommandString(
            getNexusArtifactDelegateConfig(NexusAuthType.USER_PASSWORD), "destinationPath", ScriptType.POWERSHELL))
        .isEqualTo("$Headers = @{\n"
            + "    Authorization = \"Basic dXNlcm5hbWU6ZGVjcnlwdGVkVmFsdWU=\"\n"
            + "}\n"
            + "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n"
            + "$ProgressPreference = 'SilentlyContinue'\n"
            + "$var1 = $env:HARNESS_ENV_PROXY\n"
            + "$var2 = $env:HTTP_PROXY\n"
            + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
            + "}\n"
            + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
            + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
            + " Invoke-WebRequest -Uri \"https://nexus3.dev.harness.io/repository/maven-releases/mygroup/myartifact/1.8/myartifact-1.8.war\" -Headers $Headers -OutFile \"destinationPath\\myartifact-1.8.war\" -Proxy \"$env:HTTP_PROXY\"\n"
            + "} else {\n"
            + " Invoke-WebRequest -Uri \"https://nexus3.dev.harness.io/repository/maven-releases/mygroup/myartifact/1.8/myartifact-1.8.war\" -Headers $Headers -OutFile \"destinationPath\\myartifact-1.8.war\"\n"
            + "}");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetPowerShellCommandString_NoCredentials() {
    assertThat(nexusArtifactDownloadHandler.getCommandString(
                   getNexusArtifactDelegateConfig(NexusAuthType.ANONYMOUS), "destinationPath", ScriptType.POWERSHELL))
        .contains("[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]");
  }

  private NexusArtifactDelegateConfig getNexusArtifactDelegateConfig(NexusAuthType nexusAuthType) {
    NexusUsernamePasswordAuthDTO credentials =
        NexusUsernamePasswordAuthDTO.builder()
            .username(NEXUS_USERNAME)
            .passwordRef(SecretRefData.builder().decryptedValue(NEXUS_PWD_DECRYPTED_VALUE.toCharArray()).build())
            .build();
    NexusAuthenticationDTO nexusAuthenticationDTO =
        NexusAuthenticationDTO.builder().credentials(credentials).authType(nexusAuthType).build();
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder()
                                         .auth(nexusAuthenticationDTO)
                                         .nexusServerUrl(NEXUS_SERVER_URL)
                                         .version(NEXUS_VERSION)
                                         .build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(connectorDTO).connectorType(ConnectorType.NEXUS).build();

    Map<String, String> metadata = new HashMap<>();
    metadata.put("url", ARTIFACT_URL);

    return NexusArtifactDelegateConfig.builder()
        .artifactUrl(ARTIFACT_URL)
        .identifier("identifier")
        .isCertValidationRequired(false)
        .connectorDTO(connectorInfoDTO)
        .metadata(metadata)
        .encryptedDataDetails(Collections.emptyList())
        .build();
  }
}
