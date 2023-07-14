/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
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
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.task.azure.artifact.AzureArtifactsHelper;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.ScriptType;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class AzureArtifactDownloadHandlerTest extends CategoryTest {
  private static final String ARTIFACT_URL = "https://dev.azure.com/test/";
  private static final String TOKEN_DECRYPTED_VALUE = "decryptedValue";

  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock DecryptableEntity decryptableEntity;
  @Mock LogCallback logCallback;
  @Mock AzureArtifactsHelper azureArtifactsHelper;

  @InjectMocks private AzureArtifactDownloadHandler handler;

  @Before
  public void setup() {
    doReturn(decryptableEntity).when(secretDecryptionService).decrypt(any(), anyList());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetBashCommandStringMaven() {
    AzureArtifactDelegateConfig azureArtifactDelegateConfig = getArtifactDelegateConfig("maven");
    String result = handler.getCommandString(azureArtifactDelegateConfig, "destinationPath", ScriptType.BASH);
    assertThat(result.trim())
        .isEqualTo(
            "curl -L --fail -H \"Authorization: Basic OmRlY3J5cHRlZFZhbHVl\" -X GET \"https://pkgs.dev.azure.com/test/_apis/packaging/feeds/feed/maven///1.0.0/null/content?api-version=5.1-preview.1\" -o \"destinationPath/null\"");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetPowershellCommandStringMaven() {
    AzureArtifactDelegateConfig azureArtifactDelegateConfig = getArtifactDelegateConfig("maven");
    String result = handler.getCommandString(azureArtifactDelegateConfig, "destinationPath", ScriptType.POWERSHELL);
    assertThat(result.trim())
        .isEqualTo("$Headers = @{\n"
            + "    Authorization = \"Basic OmRlY3J5cHRlZFZhbHVl\"\n"
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
            + " Invoke-WebRequest -Uri \"https://pkgs.dev.azure.com/test/_apis/packaging/feeds/feed/maven///1.0.0/null/content?api-version=5.1-preview.1\" -Headers $Headers -OutFile \"destinationPath\\null\" -Proxy \"$env:HTTP_PROXY\"\n"
            + "} else {\n"
            + " Invoke-WebRequest -Uri \"https://pkgs.dev.azure.com/test/_apis/packaging/feeds/feed/maven///1.0.0/null/content?api-version=5.1-preview.1\" -Headers $Headers -OutFile \"destinationPath\\null\"\n"
            + "}");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetPowershellCommandStringUpackOrg() {
    AzureArtifactDelegateConfig azureArtifactDelegateConfig = getArtifactDelegateConfig("upack");
    String result = handler.getCommandString(azureArtifactDelegateConfig, "destinationPath", ScriptType.POWERSHELL);
    assertThat(result.trim())
        .isEqualTo("$env:AZURE_DEVOPS_EXT_PAT = \"decryptedValue\"\n"
            + "az artifacts universal download --organization \"https://dev.azure.com/test/\" --feed \"feed\" --name \"testPack\" --version \"1.0.0\" --path destinationPath");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetPowershellCommandStringUpackProj() {
    AzureArtifactDelegateConfig azureArtifactDelegateConfig = getArtifactDelegateConfig("upack", "project", "testProj");
    String result = handler.getCommandString(azureArtifactDelegateConfig, "destinationPath", ScriptType.POWERSHELL);
    assertThat(result.trim())
        .isEqualTo("$env:AZURE_DEVOPS_EXT_PAT = \"decryptedValue\"\n"
            + "az artifacts universal download --organization \"https://dev.azure.com/test/\" --project=\"testProj\" --scope project --feed \"feed\" --name \"testPack\" --version \"1.0.0\" --path destinationPath");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetBashCommandStringUpackOrg() {
    AzureArtifactDelegateConfig azureArtifactDelegateConfig = getArtifactDelegateConfig("upack");
    String result = handler.getCommandString(azureArtifactDelegateConfig, "destinationPath", ScriptType.BASH);
    assertThat(result.trim())
        .isEqualTo("export AZURE_DEVOPS_EXT_PAT=decryptedValue\n"
            + "az artifacts universal download --organization \"https://dev.azure.com/test/\" --feed \"feed\" --name \"testPack\" --version \"1.0.0\" --path destinationPath");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetBashCommandStringUpackProj() {
    AzureArtifactDelegateConfig azureArtifactDelegateConfig = getArtifactDelegateConfig("upack", "project", "testProj");
    String result = handler.getCommandString(azureArtifactDelegateConfig, "destinationPath", ScriptType.BASH);
    assertThat(result.trim())
        .isEqualTo("export AZURE_DEVOPS_EXT_PAT=decryptedValue\n"
            + "az artifacts universal download --organization \"https://dev.azure.com/test/\" --project=\"testProj\" --scope project --feed \"feed\" --name \"testPack\" --version \"1.0.0\" --path destinationPath");
  }

  private AzureArtifactDelegateConfig getArtifactDelegateConfig(String packageType) {
    return getArtifactDelegateConfig(packageType, "org", "");
  }

  private AzureArtifactDelegateConfig getArtifactDelegateConfig(String packageType, String scope, String project) {
    AzureArtifactsAuthenticationDTO azureArtifactsAuthenticationDTO =
        AzureArtifactsAuthenticationDTO.builder()
            .credentials(
                AzureArtifactsCredentialsDTO.builder()
                    .type(AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN)
                    .credentialsSpec(
                        AzureArtifactsTokenDTO.builder()
                            .tokenRef(
                                SecretRefData.builder().decryptedValue(TOKEN_DECRYPTED_VALUE.toCharArray()).build())
                            .build())
                    .build())
            .build();

    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO = AzureArtifactsConnectorDTO.builder()
                                                                .azureArtifactsUrl(ARTIFACT_URL)
                                                                .auth(azureArtifactsAuthenticationDTO)
                                                                .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(azureArtifactsConnectorDTO)
                                            .connectorType(ConnectorType.AZURE_ARTIFACTS)
                                            .build();

    return AzureArtifactDelegateConfig.builder()
        .packageType(packageType)
        .packageName("testPack")
        .version("1.0.0")
        .feed("feed")
        .scope(scope)
        .project(project)
        .identifier("identifier")
        .connectorDTO(connectorInfoDTO)
        .encryptedDataDetails(Collections.emptyList())
        .build();
  }
}
