/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.githubconnector;

import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.delegate.beans.connector.scm.github.GithubApiAccessType.GITHUB_APP;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.HEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.githubconnector.GithubAppApiAccess;
import io.harness.connector.entities.embedded.githubconnector.GithubConnector;
import io.harness.connector.entities.embedded.githubconnector.GithubHttpAuthentication;
import io.harness.connector.entities.embedded.githubconnector.GithubOauth;
import io.harness.connector.entities.embedded.githubconnector.GithubSshAuthentication;
import io.harness.connector.entities.embedded.githubconnector.GithubTokenApiAccess;
import io.harness.connector.entities.embedded.githubconnector.GithubUsernamePassword;
import io.harness.connector.entities.embedded.githubconnector.GithubUsernameToken;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.connector.scm.github.GithubSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class GithubDTOToEntityTest extends CategoryTest {
  @InjectMocks GithubDTOToEntity githubDTOToEntity;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToConnectorEntity_0() {
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
                                                      .build();
    final GithubConnector githubConnector = githubDTOToEntity.toConnectorEntity(githubConnectorDTO);
    assertThat(githubConnector).isNotNull();
    assertThat(githubConnector.getUrl()).isEqualTo(url);
    assertThat(githubConnector.getValidationRepo()).isEqualTo(validationRepo);
    assertThat(githubConnector.getApiAccessType()).isEqualTo(GITHUB_APP);
    assertThat(githubConnector.getAuthType()).isEqualTo(HTTP);
    assertThat(githubConnector.getAuthenticationDetails())
        .isEqualTo(GithubHttpAuthentication.builder()
                       .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                       .auth(GithubUsernamePassword.builder().username(username).passwordRef(passwordRef).build())
                       .build());
    assertThat(githubConnector.getGithubApiAccess())
        .isEqualTo(GithubAppApiAccess.builder()
                       .applicationId(appId)
                       .installationId(insId)
                       .privateKeyRef(privateKeyRef)
                       .build());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToConnectorEntity_1() {
    final String url = "url";
    final String passwordRef = "passwordRef";
    final String usernameRef = "usernameRef";
    final String appId = "appId";
    final String insId = "insId";
    final String privateKeyRef = "privateKeyRef";

    final GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(GithubHttpCredentialsDTO.builder()
                             .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                             .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                      .passwordRef(SecretRefHelper.createSecretRef(passwordRef))
                                                      .usernameRef(SecretRefHelper.createSecretRef(usernameRef))
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
                                                      .connectionType(GitConnectionType.REPO)
                                                      .authentication(githubAuthenticationDTO)
                                                      .apiAccess(githubApiAccessDTO)
                                                      .build();
    final GithubConnector githubConnector = githubDTOToEntity.toConnectorEntity(githubConnectorDTO);
    assertThat(githubConnector).isNotNull();
    assertThat(githubConnector.getUrl()).isEqualTo(url);
    assertThat(githubConnector.getApiAccessType()).isEqualTo(GITHUB_APP);
    assertThat(githubConnector.getAuthType()).isEqualTo(HTTP);
    assertThat(githubConnector.getAuthenticationDetails())
        .isEqualTo(GithubHttpAuthentication.builder()
                       .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                       .auth(GithubUsernamePassword.builder().usernameRef(usernameRef).passwordRef(passwordRef).build())
                       .build());
    assertThat(githubConnector.getGithubApiAccess())
        .isEqualTo(GithubAppApiAccess.builder()
                       .applicationId(appId)
                       .installationId(insId)
                       .privateKeyRef(privateKeyRef)
                       .build());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToConnectorEntity_2() {
    final String url = "url";
    final String tokenRef = "tokenRef";
    final GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(
                GithubHttpCredentialsDTO.builder()
                    .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                    .httpCredentialsSpec(
                        GithubUsernameTokenDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
                    .build())
            .build();

    final GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder()
            .type(GithubApiAccessType.TOKEN)
            .spec(GithubTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
            .build();
    final GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                      .url(url)
                                                      .connectionType(GitConnectionType.REPO)
                                                      .authentication(githubAuthenticationDTO)
                                                      .apiAccess(githubApiAccessDTO)
                                                      .build();
    final GithubConnector githubConnector = githubDTOToEntity.toConnectorEntity(githubConnectorDTO);
    assertThat(githubConnector).isNotNull();
    assertThat(githubConnector.getUrl()).isEqualTo(url);
    assertThat(githubConnector.getApiAccessType()).isEqualTo(GithubApiAccessType.TOKEN);
    assertThat(githubConnector.getAuthType()).isEqualTo(HTTP);
    assertThat(githubConnector.getAuthenticationDetails())
        .isEqualTo(GithubHttpAuthentication.builder()
                       .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                       .auth(GithubUsernameToken.builder().tokenRef(tokenRef).build())
                       .build());
    assertThat(githubConnector.getGithubApiAccess())
        .isEqualTo(GithubTokenApiAccess.builder().tokenRef(tokenRef).build());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToConnectorEntity_3() {
    final String url = "url";
    final String sshKeyRef = "sshKeyRef";
    final GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder()
            .authType(GitAuthType.SSH)
            .credentials(
                GithubSshCredentialsDTO.builder().sshKeyRef(SecretRefHelper.createSecretRef(sshKeyRef)).build())
            .build();

    final GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                      .url(url)
                                                      .connectionType(GitConnectionType.REPO)
                                                      .authentication(githubAuthenticationDTO)
                                                      .build();
    final GithubConnector githubConnector = githubDTOToEntity.toConnectorEntity(githubConnectorDTO);
    assertThat(githubConnector).isNotNull();
    assertThat(githubConnector.getUrl()).isEqualTo(url);
    assertThat(githubConnector.getAuthType()).isEqualTo(GitAuthType.SSH);
    assertThat(githubConnector.getAuthenticationDetails())
        .isEqualTo(GithubSshAuthentication.builder().sshKeyRef(sshKeyRef).build());
    assertThat(githubConnector.getGithubApiAccess()).isNull();
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testToConnectorEntity_5() {
    final String url = "url";
    final String tokenRef = "tokenRef";
    final GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(GithubHttpCredentialsDTO.builder()
                             .type(GithubHttpAuthenticationType.OAUTH)
                             .httpCredentialsSpec(
                                 GithubOauthDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
                             .build())
            .build();

    final GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder()
            .type(GithubApiAccessType.OAUTH)
            .spec(GithubOauthDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
            .build();
    final GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                      .url(url)
                                                      .connectionType(GitConnectionType.REPO)
                                                      .authentication(githubAuthenticationDTO)
                                                      .apiAccess(githubApiAccessDTO)
                                                      .build();
    final GithubConnector githubConnector = githubDTOToEntity.toConnectorEntity(githubConnectorDTO);
    assertThat(githubConnector).isNotNull();
    assertThat(githubConnector.getUrl()).isEqualTo(url);
    assertThat(githubConnector.getApiAccessType()).isEqualTo(GithubApiAccessType.OAUTH);
    assertThat(githubConnector.getAuthType()).isEqualTo(HTTP);
    assertThat(githubConnector.getAuthenticationDetails())
        .isEqualTo(GithubHttpAuthentication.builder()
                       .type(GithubHttpAuthenticationType.OAUTH)
                       .auth(GithubOauth.builder().tokenRef(tokenRef).build())
                       .build());
    assertThat(githubConnector.getGithubApiAccess()).isEqualTo(GithubOauth.builder().tokenRef(tokenRef).build());
  }
}
