/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ng.trialsignup;

import static io.harness.rule.OwnerRule.AMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.product.ci.scm.proto.GenerateYamlResponse;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.ScmClient;

import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class ProvisionServiceTest extends CategoryTest {
  ProvisionService provisionService;
  ConnectorService connectorService;
  SecretManagerClientService ngSecret;
  DecryptionHelper decryptionHelper;
  ScmClient scmClient;

  @Before
  public void setup() throws IllegalAccessException {
    connectorService = mock(ConnectorService.class);
    provisionService = new ProvisionService();
    ngSecret = mock(SecretManagerClientService.class);
    decryptionHelper = mock(DecryptionHelper.class);
    scmClient = mock(ScmClient.class);
    FieldUtils.writeField(provisionService, "connectorService", connectorService, true);
    FieldUtils.writeField(provisionService, "ngSecret", ngSecret, true);
    FieldUtils.writeField(provisionService, "decryptionHelper", decryptionHelper, true);
    FieldUtils.writeField(provisionService, "scmClient", scmClient, true);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void generateYamlTest() {
    char[] passarray = {'p', 'a', 's', 's'};
    GithubUsernamePasswordDTO build =
        GithubUsernamePasswordDTO.builder()
            .username("user")
            .passwordRef(SecretRefData.builder().identifier("passRef").decryptedValue(passarray).build())
            .build();
    GithubHttpCredentialsDTO credentialsDTO = GithubHttpCredentialsDTO.builder()
                                                  .httpCredentialsSpec(build)
                                                  .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                  .build();
    GithubAuthenticationDTO authenticationDTO =
        GithubAuthenticationDTO.builder().credentials(credentialsDTO).authType(GitAuthType.HTTP).build();
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .authentication(authenticationDTO)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .url("https://github.com")
                                                .build();
    GithubUsernamePasswordDTO decrypt =
        (GithubUsernamePasswordDTO) doReturn(build).when(decryptionHelper).decrypt(Mockito.any(), Mockito.any());
    doReturn(GenerateYamlResponse.newBuilder().setYaml("yaml").build())
        .when(scmClient)
        .autogenerateStageYamlForCI(anyString());
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(githubConnectorDTO).connectorType(ConnectorType.GITHUB).build();
    doReturn(Optional.of(ConnectorResponseDTO.builder().connector(connectorInfoDTO).build()))
        .when(connectorService)
        .get(anyString(), anyString(), anyString(), anyString());
    String s = provisionService.generateYaml("acc", "org", "proj", "id", "repo");
    assertThat(s).isEqualTo("yaml");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void generateYamlShouldThrowExceptionTest() {
    char[] passarray = {'p', 'a', 's', 's'};
    GithubUsernamePasswordDTO build =
        GithubUsernamePasswordDTO.builder()
            .username("user")
            .passwordRef(SecretRefData.builder().identifier("passRef").decryptedValue(passarray).build())
            .build();
    GithubHttpCredentialsDTO credentialsDTO = GithubHttpCredentialsDTO.builder()
                                                  .httpCredentialsSpec(build)
                                                  .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                  .build();
    GithubAuthenticationDTO authenticationDTO =
        GithubAuthenticationDTO.builder().credentials(credentialsDTO).authType(GitAuthType.HTTP).build();
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .authentication(authenticationDTO)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .url("https://github.com")
                                                .build();
    GithubUsernamePasswordDTO decrypt =
        (GithubUsernamePasswordDTO) doReturn(build).when(decryptionHelper).decrypt(Mockito.any(), Mockito.any());
    doReturn(GenerateYamlResponse.newBuilder().setYaml("yaml").build())
        .when(scmClient)
        .autogenerateStageYamlForCI(anyString());
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(githubConnectorDTO).connectorType(ConnectorType.GITHUB).build();
    doReturn(Optional.empty()).when(connectorService).get(anyString(), anyString(), anyString(), anyString());
    provisionService.generateYaml("acc", "org", "proj", "id", "repo");
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void updateUrlDefaultConnectorTest() {
    AutogenInput autogenInput = AutogenInput.builder().repo("https://github.com").password("pass").build();
    String repo = "harness/harness-core";
    String cloneUrl = provisionService.updateUrl(autogenInput, repo);
    assertThat(cloneUrl).isEqualTo("https://default:pass@github.com/harness/harness-core");
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void updateUrlAccountConnectorTest() {
    AutogenInput autogenInput =
        AutogenInput.builder().repo("https://github.com/harness").username("username").password("pass").build();
    String repo = "harness/harness-core";
    String cloneUrl = provisionService.updateUrl(autogenInput, repo);
    assertThat(cloneUrl).isEqualTo("https://username:pass@github.com/harness/harness-core");
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void updateUrlDefaultConnectorWithSlashInEndTest() {
    AutogenInput autogenInput = AutogenInput.builder().repo("https://github.com/").password("pass").build();
    String repo = "harness/harness-core";
    String cloneUrl = provisionService.updateUrl(autogenInput, repo);
    assertThat(cloneUrl).isEqualTo("https://default:pass@github.com/harness/harness-core");
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void updateUrlRepoConnectorTest() {
    AutogenInput autogenInput = AutogenInput.builder()
                                    .repo("https://github.com/harness/harness-core")
                                    .username("username")
                                    .password("pass")
                                    .build();
    String repo = "abc/abc";
    String cloneUrl = provisionService.updateUrl(autogenInput, repo);
    assertThat(cloneUrl).isEqualTo("https://username:pass@github.com/harness/harness-core");
  }
}