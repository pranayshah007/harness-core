/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.helper;

import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GitAuthenticationDecryptionHelperTest extends CategoryTest {
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testIsGitHubAppAuthentication() {
    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(GithubHttpCredentialsDTO.builder()
                             .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                             .httpCredentialsSpec(GithubAppDTO.builder()
                                                      .applicationId("app")
                                                      .installationId("install")
                                                      .privateKeyRef(SecretRefData.builder().identifier("id").build())
                                                      .build())
                             .build())
            .build();
    ScmConnector connector = GithubConnectorDTO.builder()
                                 .url("url")
                                 .connectionType(GitConnectionType.REPO)
                                 .authentication(githubAuthenticationDTO)
                                 .build();
    assertThat(GitAuthenticationDecryptionHelper.isGitHubAppAuthentication(connector)).isTrue();
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetGitHubAppAuthenticationDecryptableEntity() {
    GithubAppDTO githubAppDTO = GithubAppDTO.builder()
                                    .applicationId("app")
                                    .installationId("install")
                                    .privateKeyRef(SecretRefData.builder().identifier("id").build())
                                    .build();
    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(
                GithubHttpCredentialsDTO.builder().type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN).build())
            .build();
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .url("url")
                                                .connectionType(GitConnectionType.REPO)
                                                .authentication(githubAuthenticationDTO)
                                                .build();
    GithubHttpCredentialsDTO authenticationDTO =
        GitAuthenticationDecryptionHelper.getGitHubAppAuthenticationDecryptableEntity(githubConnectorDTO, githubAppDTO);
    assertThat(authenticationDTO.getHttpCredentialsSpec()).isNotNull();
  }
}
