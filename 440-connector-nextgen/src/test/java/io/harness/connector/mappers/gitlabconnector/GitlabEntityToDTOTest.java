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
import io.harness.connector.entities.embedded.gitlabconnector.GitlabTokenApiAccess;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabUsernamePassword;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabKerberosDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernamePasswordDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class GitlabEntityToDTOTest extends CategoryTest {
  @InjectMocks GitlabEntityToDTO gitlabEntityToDTO;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToConnectorEntity_0() throws IOException {
    final String url = "url";
    final String passwordRef = "passwordRef";
    final String username = "username";
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

    final GitlabApiAccessDTO gitlabApiAccessDTO =
        GitlabApiAccessDTO.builder()
            .type(GitlabApiAccessType.TOKEN)
            .spec(GitlabTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef(privateKeyRef)).build())
            .build();
    final GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                      .url(url)
                                                      .validationRepo(validationRepo)
                                                      .connectionType(GitConnectionType.ACCOUNT)
                                                      .authentication(gitlabAuthenticationDTO)
                                                      .apiAccess(gitlabApiAccessDTO)
                                                      .build();

    final GitlabConnector gitlabConnector1 =
        GitlabConnector.builder()
            .hasApiAccess(true)
            .url(url)
            .validationRepo(validationRepo)
            .gitlabApiAccess(GitlabTokenApiAccess.builder().tokenRef(privateKeyRef).build())
            .connectionType(GitConnectionType.ACCOUNT)
            .authType(HTTP)
            .authenticationDetails(
                GitlabHttpAuthentication.builder()
                    .type(GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD)
                    .auth(GitlabUsernamePassword.builder().username(username).passwordRef(passwordRef).build())
                    .build())
            .build();
    final GitlabConnectorDTO gitlabConnector = gitlabEntityToDTO.createConnectorDTO(gitlabConnector1);
    ObjectMapper objectMapper = new ObjectMapper();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(gitlabConnector)))
        .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(gitlabConnectorDTO)));
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToConnectorEntity_1() throws IOException {
    final String url = "url";
    final String tokenKeyRef = "privateKeyRef";

    final GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(GitlabHttpCredentialsDTO.builder()
                             .type(GitlabHttpAuthenticationType.KERBEROS)
                             .httpCredentialsSpec(GitlabKerberosDTO.builder()
                                                      .kerberosKeyRef(SecretRefHelper.createSecretRef(tokenKeyRef))
                                                      .build())
                             .build())
            .build();

    final GitlabApiAccessDTO gitlabApiAccessDTO =
        GitlabApiAccessDTO.builder()
            .type(GitlabApiAccessType.TOKEN)
            .spec(GitlabTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenKeyRef)).build())
            .build();
    final GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                      .url(url)
                                                      .connectionType(GitConnectionType.REPO)
                                                      .authentication(gitlabAuthenticationDTO)
                                                      .apiAccess(gitlabApiAccessDTO)
                                                      .build();

    final GitlabConnector gitlabConnector1 =
        GitlabConnector.builder()
            .hasApiAccess(true)
            .url(url)
            .gitlabApiAccess(GitlabTokenApiAccess.builder().tokenRef(tokenKeyRef).build())
            .connectionType(GitConnectionType.REPO)
            .authType(HTTP)
            .authenticationDetails(GitlabHttpAuthentication.builder()
                                       .type(GitlabHttpAuthenticationType.KERBEROS)
                                       .auth(GitlabKerberos.builder().kerberosKeyRef(tokenKeyRef).build())
                                       .build())
            .build();
    final GitlabConnectorDTO gitlabConnector = gitlabEntityToDTO.createConnectorDTO(gitlabConnector1);
    ObjectMapper objectMapper = new ObjectMapper();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(gitlabConnector)))
        .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(gitlabConnectorDTO)));
  }
}
