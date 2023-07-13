/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.adapter;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.delegate.beans.connector.scm.GitAuthType.SSH;
import static io.harness.delegate.beans.connector.scm.GitConnectionType.ACCOUNT;
import static io.harness.delegate.beans.connector.scm.GitConnectionType.REPO;
import static io.harness.delegate.beans.connector.scm.github.GithubApiAccessType.GITHUB_APP;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(DX)
public class GithubToGitMapperTest extends CategoryTest {
  Set<String> delegateSelectors = new HashSet<>();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    delegateSelectors.add("abc");
    delegateSelectors.add("def");
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testMappingToHTTPGitConfigDTO() {
    final String url = "url";
    final String passwordRef = "passwordRef";
    final String username = "username";
    final String appId = "appId";
    final String insId = "insId";
    final String privateKeyRef = "privateKeyRef";
    final String validationRepo = "validationRepo";

    final GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(GithubHttpCredentialsDTO.builder()
                             .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                             .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                      .passwordRef(SecretRefHelper.createSecretRef(passwordRef))
                                                      .username(username)
                                                      .build())
                             .build())
            .build();

    final GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder()
            .type(GITHUB_APP)
            .spec(GithubAppSpecDTO.builder()
                      .applicationId(appId)
                      .installationId(insId)
                      .privateKeyRef(SecretRefHelper.createSecretRef(privateKeyRef))
                      .build())
            .build();
    final GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                      .url(url)
                                                      .validationRepo(validationRepo)
                                                      .connectionType(GitConnectionType.ACCOUNT)
                                                      .authentication(githubAuthenticationDTO)
                                                      .apiAccess(githubApiAccessDTO)
                                                      .delegateSelectors(delegateSelectors)
                                                      .build();
    GitConfigDTO gitConfigDTO = GithubToGitMapper.mapToGitConfigDTO(githubConnectorDTO);
    assertThat(gitConfigDTO).isNotNull();
    assertThat(gitConfigDTO.getGitAuthType()).isEqualTo(HTTP);
    assertThat(gitConfigDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
    GitHTTPAuthenticationDTO gitAuthentication = (GitHTTPAuthenticationDTO) gitConfigDTO.getGitAuth();
    assertThat(gitConfigDTO.getGitConnectionType()).isEqualTo(ACCOUNT);
    assertThat(gitConfigDTO.getUrl()).isEqualTo(url);
    assertThat(gitConfigDTO.getValidationRepo()).isEqualTo(validationRepo);
    assertThat(gitAuthentication.getUsername()).isEqualTo(username);
    assertThat(gitAuthentication.getPasswordRef().toSecretRefStringValue()).isEqualTo(passwordRef);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testMappingToSSHGitConfigDTO() {
    final String url = "url";
    String sshKeyReference = "sshKeyReference";
    final GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder()
            .authType(GitAuthType.SSH)
            .credentials(
                GithubSshCredentialsDTO.builder().sshKeyRef(SecretRefHelper.createSecretRef(sshKeyReference)).build())
            .build();

    final GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                      .url(url)
                                                      .connectionType(GitConnectionType.REPO)
                                                      .authentication(githubAuthenticationDTO)
                                                      .delegateSelectors(delegateSelectors)
                                                      .build();
    GitConfigDTO gitConfigDTO = GithubToGitMapper.mapToGitConfigDTO(githubConnectorDTO);
    assertThat(gitConfigDTO).isNotNull();
    assertThat(gitConfigDTO.getGitAuthType()).isEqualTo(SSH);
    GitSSHAuthenticationDTO gitAuthentication = (GitSSHAuthenticationDTO) gitConfigDTO.getGitAuth();
    assertThat(gitAuthentication.getEncryptedSshKey()).isEqualTo(SecretRefHelper.createSecretRef(sshKeyReference));
    assertThat(gitConfigDTO.getUrl()).isEqualTo(url);
    assertThat(gitConfigDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
    assertThat(gitConfigDTO.getGitConnectionType()).isEqualTo(REPO);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testMappingToHTTPGithubAppDTO() {
    final String url = "url";
    final GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder()
            .authType(GitAuthType.HTTP)
            .credentials(GithubHttpCredentialsDTO.builder()
                             .type(GithubHttpAuthenticationType.GITHUB_APP)
                             .httpCredentialsSpec(GithubAppDTO.builder().build())
                             .build())
            .build();

    final GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                      .url(url)
                                                      .connectionType(GitConnectionType.REPO)
                                                      .authentication(githubAuthenticationDTO)
                                                      .delegateSelectors(delegateSelectors)
                                                      .build();
    GitConfigDTO gitConfigDTO = GithubToGitMapper.mapToGitConfigDTO(githubConnectorDTO);
    assertThat(gitConfigDTO).isNotNull();
    assertThat(gitConfigDTO.getGitAuthType()).isEqualTo(HTTP);
    assertThat(gitConfigDTO.getUrl()).isEqualTo(url);
    assertThat(gitConfigDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
    assertThat(gitConfigDTO.getGitConnectionType()).isEqualTo(REPO);
  }
}
