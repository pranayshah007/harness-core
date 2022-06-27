/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.gitlabconnector;

import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabConnector;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabHttpAuthentication;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabKerberos;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabSshAuthentication;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabTokenApiAccess;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabUsernamePassword;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabUsernameToken;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabKerberosDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class GitlabDTOToEntityTest extends CategoryTest {
  @InjectMocks GitlabDTOToEntity gitlabDTOToEntity;

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

    final GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(GitlabHttpCredentialsDTO.builder()
                             .type(GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD)
                             .httpCredentialsSpec(GitlabUsernamePasswordDTO.builder()
                                                      .passwordRef(SecretRefHelper.createSecretRef(passwordRef))
                                                      .username(username)
                                                      .build())
                             .build())
            .build();

    final GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                      .url(url)
                                                      .validationRepo(validationRepo)
                                                      .connectionType(GitConnectionType.ACCOUNT)
                                                      .authentication(gitlabAuthenticationDTO)
                                                      .build();
    final GitlabConnector gitlabConnector = gitlabDTOToEntity.toConnectorEntity(gitlabConnectorDTO);
    assertThat(gitlabConnector).isNotNull();
    assertThat(gitlabConnector.getUrl()).isEqualTo(url);
    assertThat(gitlabConnector.getValidationRepo()).isEqualTo(validationRepo);
    assertThat(gitlabConnector.getAuthType()).isEqualTo(HTTP);
    assertThat(gitlabConnector.getAuthenticationDetails())
        .isEqualTo(GitlabHttpAuthentication.builder()
                       .type(GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD)
                       .auth(GitlabUsernamePassword.builder().username(username).passwordRef(passwordRef).build())
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

    final GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(GitlabHttpCredentialsDTO.builder()
                             .type(GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD)
                             .httpCredentialsSpec(GitlabUsernamePasswordDTO.builder()
                                                      .passwordRef(SecretRefHelper.createSecretRef(passwordRef))
                                                      .usernameRef(SecretRefHelper.createSecretRef(usernameRef))
                                                      .build())
                             .build())
            .build();

    final GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                      .url(url)
                                                      .connectionType(GitConnectionType.REPO)
                                                      .authentication(gitlabAuthenticationDTO)
                                                      .build();
    final GitlabConnector gitlabConnector = gitlabDTOToEntity.toConnectorEntity(gitlabConnectorDTO);
    assertThat(gitlabConnector).isNotNull();
    assertThat(gitlabConnector.getUrl()).isEqualTo(url);
    assertThat(gitlabConnector.getAuthType()).isEqualTo(HTTP);
    assertThat(gitlabConnector.getAuthenticationDetails())
        .isEqualTo(GitlabHttpAuthentication.builder()
                       .type(GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD)
                       .auth(GitlabUsernamePassword.builder().usernameRef(usernameRef).passwordRef(passwordRef).build())
                       .build());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToConnectorEntity_2() {
    final String url = "url";
    final String tokenRef = "tokenRef";
    final GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(
                GitlabHttpCredentialsDTO.builder()
                    .type(GitlabHttpAuthenticationType.USERNAME_AND_TOKEN)
                    .httpCredentialsSpec(
                        GitlabUsernameTokenDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
                    .build())
            .build();

    final GitlabApiAccessDTO gitlabApiAccessDTO =
        GitlabApiAccessDTO.builder()
            .type(GitlabApiAccessType.TOKEN)
            .spec(GitlabTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
            .build();
    final GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                      .url(url)
                                                      .connectionType(GitConnectionType.REPO)
                                                      .authentication(gitlabAuthenticationDTO)
                                                      .apiAccess(gitlabApiAccessDTO)
                                                      .build();
    final GitlabConnector gitlabConnector = gitlabDTOToEntity.toConnectorEntity(gitlabConnectorDTO);
    assertThat(gitlabConnector).isNotNull();
    assertThat(gitlabConnector.getUrl()).isEqualTo(url);
    assertThat(gitlabConnector.getAuthType()).isEqualTo(HTTP);
    assertThat(gitlabConnector.getAuthenticationDetails())
        .isEqualTo(GitlabHttpAuthentication.builder()
                       .type(GitlabHttpAuthenticationType.USERNAME_AND_TOKEN)
                       .auth(GitlabUsernameToken.builder().tokenRef(tokenRef).build())
                       .build());
    assertThat(gitlabConnector.getGitlabApiAccess())
        .isEqualTo(GitlabTokenApiAccess.builder().tokenRef(tokenRef).build());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToConnectorEntity_3() {
    final String url = "url";
    final String sshKeyRef = "sshKeyRef";
    final GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder()
            .authType(GitAuthType.SSH)
            .credentials(
                GitlabSshCredentialsDTO.builder().sshKeyRef(SecretRefHelper.createSecretRef(sshKeyRef)).build())
            .build();

    final GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                      .url(url)
                                                      .connectionType(GitConnectionType.REPO)
                                                      .authentication(gitlabAuthenticationDTO)
                                                      .build();
    final GitlabConnector gitlabConnector = gitlabDTOToEntity.toConnectorEntity(gitlabConnectorDTO);
    assertThat(gitlabConnector).isNotNull();
    assertThat(gitlabConnector.getUrl()).isEqualTo(url);
    assertThat(gitlabConnector.getAuthType()).isEqualTo(GitAuthType.SSH);
    assertThat(gitlabConnector.getAuthenticationDetails())
        .isEqualTo(GitlabSshAuthentication.builder().sshKeyRef(sshKeyRef).build());
    assertThat(gitlabConnector.getGitlabApiAccess()).isNull();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToConnectorEntity_4() {
    final String url = "url";
    final String kerberosKeyRef = "tokenRef";
    final GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(GitlabHttpCredentialsDTO.builder()
                             .type(GitlabHttpAuthenticationType.KERBEROS)
                             .httpCredentialsSpec(GitlabKerberosDTO.builder()
                                                      .kerberosKeyRef(SecretRefHelper.createSecretRef(kerberosKeyRef))
                                                      .build())
                             .build())
            .build();

    final GitlabApiAccessDTO gitlabApiAccessDTO =
        GitlabApiAccessDTO.builder()
            .type(GitlabApiAccessType.TOKEN)
            .spec(GitlabTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef(kerberosKeyRef)).build())
            .build();
    final GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                      .url(url)
                                                      .connectionType(GitConnectionType.REPO)
                                                      .authentication(gitlabAuthenticationDTO)
                                                      .apiAccess(gitlabApiAccessDTO)
                                                      .build();
    final GitlabConnector gitlabConnector = gitlabDTOToEntity.toConnectorEntity(gitlabConnectorDTO);
    assertThat(gitlabConnector).isNotNull();
    assertThat(gitlabConnector.getUrl()).isEqualTo(url);
    assertThat(gitlabConnector.getAuthType()).isEqualTo(HTTP);
    assertThat(gitlabConnector.getAuthenticationDetails())
        .isEqualTo(GitlabHttpAuthentication.builder()
                       .type(GitlabHttpAuthenticationType.KERBEROS)
                       .auth(GitlabKerberos.builder().kerberosKeyRef(kerberosKeyRef).build())
                       .build());
    assertThat(gitlabConnector.getGitlabApiAccess())
        .isEqualTo(GitlabTokenApiAccess.builder().tokenRef(kerberosKeyRef).build());
  }
}
