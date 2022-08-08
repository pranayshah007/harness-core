/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.githubconnector;

import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.delegate.beans.connector.scm.github.GithubApiAccessType.GITHUB_APP;
import static io.harness.delegate.beans.connector.scm.github.GithubApiAccessType.OAUTH;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.HEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.githubconnector.GithubAppApiAccess;
import io.harness.connector.entities.embedded.githubconnector.GithubConnector;
import io.harness.connector.entities.embedded.githubconnector.GithubHttpAuthentication;
import io.harness.connector.entities.embedded.githubconnector.GithubOauth;
import io.harness.connector.entities.embedded.githubconnector.GithubUsernamePassword;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class GithubEntityToDTOTest extends CategoryTest {
  @InjectMocks GithubEntityToDTO githubEntityToDTO;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToConnectorEntity_0() throws IOException {
    final String url = "https://github.com/harness";
    final String passwordRef = "passwordRef";
    final String username = "username";
    final String appId = "appId";
    final String insId = "insId";
    final String tokenRef = "tokenRef";
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

    final GithubConnector githubConnector1 =
        GithubConnector.builder()
            .hasApiAccess(true)
            .url(url)
            .validationRepo(validationRepo)
            .githubApiAccess(GithubAppApiAccess.builder()
                                 .applicationId(appId)
                                 .installationId(insId)
                                 .privateKeyRef(privateKeyRef)
                                 .build())
            .apiAccessType(GITHUB_APP)
            .connectionType(GitConnectionType.ACCOUNT)
            .authType(HTTP)
            .authenticationDetails(
                GithubHttpAuthentication.builder()
                    .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                    .auth(GithubUsernamePassword.builder().username(username).passwordRef(passwordRef).build())
                    .build())
            .build();
    final GithubConnectorDTO githubConnector = githubEntityToDTO.createConnectorDTO(githubConnector1);
    ObjectMapper objectMapper = new ObjectMapper();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(githubConnector)))
        .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(githubConnectorDTO)));
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testToConnectorEntity_1() throws IOException {
    final String url = "https://github.com/harness-core";
    final String username = "username";
    final String appId = "appId";
    final String insId = "insId";
    final String tokenRef = "tokenRef";
    final String validationRepo = "validationRepo";

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
            .type(OAUTH)
            .spec(GithubOauthDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
            .build();
    final GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                      .url(url)
                                                      .validationRepo(validationRepo)
                                                      .connectionType(GitConnectionType.ACCOUNT)
                                                      .authentication(githubAuthenticationDTO)
                                                      .apiAccess(githubApiAccessDTO)
                                                      .build();

    final GithubConnector githubConnector1 =
        GithubConnector.builder()
            .hasApiAccess(true)
            .url(url)
            .validationRepo(validationRepo)
            .githubApiAccess(GithubOauth.builder().tokenRef(tokenRef).build())
            .apiAccessType(OAUTH)
            .connectionType(GitConnectionType.ACCOUNT)
            .authType(HTTP)
            .authenticationDetails(GithubHttpAuthentication.builder()
                                       .type(GithubHttpAuthenticationType.OAUTH)
                                       .auth(GithubOauth.builder().tokenRef(tokenRef).build())
                                       .build())
            .build();
    final GithubConnectorDTO githubConnector = githubEntityToDTO.createConnectorDTO(githubConnector1);
    ObjectMapper objectMapper = new ObjectMapper();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(githubConnector)))
        .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(githubConnectorDTO)));
  }
}
