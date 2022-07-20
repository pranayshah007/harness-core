/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.adapter;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.delegate.beans.connector.scm.GitAuthType.SSH;
import static io.harness.delegate.beans.connector.scm.GitConnectionType.PROJECT;
import static io.harness.delegate.beans.connector.scm.GitConnectionType.REPO;
import static io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType.TOKEN;
import static io.harness.rule.OwnerRule.MANKRIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class AzureRepoToGitMapperTest extends CategoryTest {
  Set<String> delegateSelectors = new HashSet<>();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    delegateSelectors.add("abc");
    delegateSelectors.add("def");
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testMappingToHTTPGitConfigDTO() {
    final String url = "url";
    final String usernameRef = "usernameRef";
    final String tokenRef = "tokenRef";
    final String username = "username";
    final String validationRepo = "validationRepo";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(AzureRepoHttpCredentialsDTO.builder()
                             .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                             .httpCredentialsSpec(AzureRepoUsernameTokenDTO.builder()
                                                      .username(username)
                                                      .usernameRef(SecretRefHelper.createSecretRef(usernameRef))
                                                      .tokenRef(SecretRefHelper.createSecretRef(tokenRef))
                                                      .build())
                             .build())
            .build();

    final AzureRepoApiAccessDTO azureRepoApiAccessDTO =
        AzureRepoApiAccessDTO.builder()
            .type(TOKEN)
            .spec(AzureRepoTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
            .build();
    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
                                                            .url(url)
                                                            .validationRepo(validationRepo)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .apiAccess(azureRepoApiAccessDTO)
                                                            .delegateSelectors(delegateSelectors)
                                                            .build();
    GitConfigDTO gitConfigDTO = AzureRepoToGitMapper.mapToGitConfigDTO(azureRepoConnectorDTO);
    assertThat(gitConfigDTO).isNotNull();
    assertThat(gitConfigDTO.getGitAuthType()).isEqualTo(HTTP);
    assertThat(gitConfigDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
    GitHTTPAuthenticationDTO gitAuthentication = (GitHTTPAuthenticationDTO) gitConfigDTO.getGitAuth();
    assertThat(gitConfigDTO.getGitConnectionType()).isEqualTo(PROJECT);
    assertThat(gitConfigDTO.getUrl()).isEqualTo(url);
    assertThat(gitAuthentication.getUsername()).isEqualTo(username);
    assertThat(gitAuthentication.getUsernameRef().toSecretRefStringValue()).isEqualTo(usernameRef);
    assertThat(gitAuthentication.getPasswordRef().toSecretRefStringValue()).isEqualTo(tokenRef);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testMappingToSSHGitConfigDTO() {
    final String url = "url";
    String sshKeyRef = "sshKeyRef";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(GitAuthType.SSH)
            .credentials(
                AzureRepoSshCredentialsDTO.builder().sshKeyRef(SecretRefHelper.createSecretRef(sshKeyRef)).build())
            .build();

    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                            .url(url)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .delegateSelectors(delegateSelectors)
                                                            .build();
    GitConfigDTO gitConfigDTO = AzureRepoToGitMapper.mapToGitConfigDTO(azureRepoConnectorDTO);
    assertThat(gitConfigDTO).isNotNull();
    assertThat(gitConfigDTO.getGitAuthType()).isEqualTo(SSH);
    GitSSHAuthenticationDTO gitAuthentication = (GitSSHAuthenticationDTO) gitConfigDTO.getGitAuth();
    assertThat(gitAuthentication.getEncryptedSshKey()).isEqualTo(SecretRefHelper.createSecretRef(sshKeyRef));
    assertThat(gitConfigDTO.getUrl()).isEqualTo(url);
    assertThat(gitConfigDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
    assertThat(gitConfigDTO.getGitConnectionType()).isEqualTo(REPO);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testMappingToNull() {
    final String url = "url";
    final String sshKeyRef = "sshKeyRef";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .credentials(
                AzureRepoSshCredentialsDTO.builder().sshKeyRef(SecretRefHelper.createSecretRef(sshKeyRef)).build())
            .build();

    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                            .url(url)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .delegateSelectors(delegateSelectors)
                                                            .build();
    assertThatThrownBy(() -> AzureRepoToGitMapper.mapToGitConfigDTO(azureRepoConnectorDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Azure Repo DTO Auth Type not found");
  }
}