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
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.task.ssh.artifact.GithubPackagesArtifactDelegateConfig;
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
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class GithubPackageArtifactDownloadHandlerTest extends CategoryTest {
  private static final String MAVEN_PACKAGE_TYPE = "maven";
  private static final String ARTIFACT_URL = "https://github.com/testuser/repo/myartifact-1.8.war";
  private static final String GITHUB_USERNAME = "username";
  private static final String GITHUB_TOKEN_DECRYPTED_VALUE = "decryptedValue";

  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock DecryptableEntity decryptableEntity;
  @Mock LogCallback logCallback;

  @InjectMocks private GithubPackageArtifactDownloadHandler handler;

  @Before
  public void setup() {
    doReturn(decryptableEntity).when(secretDecryptionService).decrypt(any(), anyList());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetBashCommandStringMaven() {
    GithubPackagesArtifactDelegateConfig githubPackagesArtifactDelegateConfig =
        getGithubPackagesArtifactDelegateConfig();
    String result = handler.getCommandString(githubPackagesArtifactDelegateConfig, "destinationPath", ScriptType.BASH);
    assertThat(result.trim())
        .isEqualTo(
            "curl --fail -H \"Authorization: token decryptedValue\" -X GET \"https://github.com/testuser/repo/myartifact-1.8.war\" -o \"destinationPath/myartifact-1.8.war\"");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetPowershellCommandStringMaven() {
    GithubPackagesArtifactDelegateConfig githubPackagesArtifactDelegateConfig =
        getGithubPackagesArtifactDelegateConfig();
    String result =
        handler.getCommandString(githubPackagesArtifactDelegateConfig, "destinationPath", ScriptType.POWERSHELL);
    assertThat(result.trim())
        .isEqualTo("$Headers = @{\n"
            + "    Authorization = \"token decryptedValue\"\n"
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
            + " Invoke-WebRequest -Uri \"https://github.com/testuser/repo/myartifact-1.8.war\" -Headers $Headers -OutFile \"destinationPath\\myartifact-1.8.war\" -Proxy \"$env:HTTP_PROXY\"\n"
            + "} else {\n"
            + " Invoke-WebRequest -Uri \"https://github.com/testuser/repo/myartifact-1.8.war\" -Headers $Headers -OutFile \"destinationPath\\myartifact-1.8.war\"\n"
            + "}");
  }

  private GithubPackagesArtifactDelegateConfig getGithubPackagesArtifactDelegateConfig() {
    GithubHttpCredentialsSpecDTO githubHttpCredentialsSpecDTO =
        GithubUsernameTokenDTO.builder()
            .username(GITHUB_USERNAME)
            .tokenRef(SecretRefData.builder().decryptedValue(GITHUB_TOKEN_DECRYPTED_VALUE.toCharArray()).build())
            .build();
    GithubCredentialsDTO credentials = GithubHttpCredentialsDTO.builder()
                                           .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                           .httpCredentialsSpec(githubHttpCredentialsSpecDTO)
                                           .build();
    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder().credentials(credentials).authType(GitAuthType.HTTP).build();

    GithubTokenSpecDTO githubTokenSpecDTO =
        GithubTokenSpecDTO.builder()
            .tokenRef(SecretRefData.builder().decryptedValue(GITHUB_TOKEN_DECRYPTED_VALUE.toCharArray()).build())
            .build();

    GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder().type(GithubApiAccessType.TOKEN).spec(githubTokenSpecDTO).build();

    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder().authentication(githubAuthenticationDTO).apiAccess(githubApiAccessDTO).build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(githubConnectorDTO).connectorType(ConnectorType.GITHUB).build();

    Map<String, String> metadata = new HashMap<>();
    metadata.put("url", ARTIFACT_URL);

    return GithubPackagesArtifactDelegateConfig.builder()
        .packageType(MAVEN_PACKAGE_TYPE)
        .artifactUrl(ARTIFACT_URL)
        .identifier("identifier")
        .connectorDTO(connectorInfoDTO)
        .metadata(metadata)
        .encryptedDataDetails(Collections.emptyList())
        .build();
  }
}
