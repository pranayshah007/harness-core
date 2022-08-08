/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.bitbucketconnectormapper;

import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketConnector;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketHttpAuthentication;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketUsernamePassword;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketUsernamePasswordApiAccess;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class BitbucketEntityToDTOTest extends CategoryTest {
  @InjectMocks BitbucketEntityToDTO bitbucketEntityToDTO;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToConnectorEntity_0() throws IOException {
    final String url = "https://bitbucket.org/harness";
    final String passwordRef = "passwordRef";
    final String username = "username";
    final String privateKeyRef = "privateKeyRef";
    final String validationRepo = "validationRepo";

    final BitbucketAuthenticationDTO bitbucketAuthenticationDTO =
        BitbucketAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(BitbucketHttpCredentialsDTO.builder()
                             .type(BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD)
                             .httpCredentialsSpec(BitbucketUsernamePasswordDTO.builder()
                                                      .passwordRef(SecretRefHelper.createSecretRef(passwordRef))
                                                      .username(username)
                                                      .build())
                             .build())
            .build();

    final BitbucketApiAccessDTO bitbucketApiAccessDTO =
        BitbucketApiAccessDTO.builder()
            .type(BitbucketApiAccessType.USERNAME_AND_TOKEN)
            .spec(BitbucketUsernameTokenApiAccessDTO.builder()
                      .usernameRef(SecretRefHelper.createSecretRef(privateKeyRef))
                      .tokenRef(SecretRefHelper.createSecretRef(privateKeyRef))
                      .build())
            .build();
    final BitbucketConnectorDTO bitbucketConnectorDTO = BitbucketConnectorDTO.builder()
                                                            .url(url)
                                                            .validationRepo(validationRepo)
                                                            .connectionType(GitConnectionType.ACCOUNT)
                                                            .authentication(bitbucketAuthenticationDTO)
                                                            .apiAccess(bitbucketApiAccessDTO)
                                                            .build();

    final BitbucketConnector bitbucketConnector1 =
        BitbucketConnector.builder()
            .hasApiAccess(true)
            .url(url)
            .validationRepo(validationRepo)
            .bitbucketApiAccess(
                BitbucketUsernamePasswordApiAccess.builder().usernameRef(privateKeyRef).tokenRef(privateKeyRef).build())
            .connectionType(GitConnectionType.ACCOUNT)
            .authType(HTTP)
            .authenticationDetails(
                BitbucketHttpAuthentication.builder()
                    .type(BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD)
                    .auth(BitbucketUsernamePassword.builder().username(username).passwordRef(passwordRef).build())
                    .build())
            .build();
    final BitbucketConnectorDTO bitbucketConnector = bitbucketEntityToDTO.createConnectorDTO(bitbucketConnector1);
    ObjectMapper objectMapper = new ObjectMapper();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(bitbucketConnector)))
        .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(bitbucketConnectorDTO)));
  }
}
