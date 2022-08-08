/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.azurerepoconnectormapper;

import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.delegate.beans.connector.scm.GitAuthType.SSH;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.MANKRIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoConnectionType;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoConnector;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoHttpAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoSshAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoTokenApiAccess;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoUsernameToken;
import io.harness.connector.mappers.azurerepomapper.AzureRepoEntityToDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class AzureRepoEntityToDTOTest extends CategoryTest {
  @InjectMocks AzureRepoEntityToDTO azureRepoEntityToDTO;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testToConnectorEntityHTTP() throws IOException {
    final String url = "url";
    final String usernameRef = "usernameRef";
    final String tokenRef = "tokenRef";
    final String validationRepo = "validationRepo";

    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(AzureRepoHttpCredentialsDTO.builder()
                             .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                             .httpCredentialsSpec(AzureRepoUsernameTokenDTO.builder()
                                                      .usernameRef(SecretRefHelper.createSecretRef(usernameRef))
                                                      .tokenRef(SecretRefHelper.createSecretRef(tokenRef))
                                                      .build())
                             .build())
            .build();

    final AzureRepoApiAccessDTO azureRepoApiAccessDTO =
        AzureRepoApiAccessDTO.builder()
            .type(AzureRepoApiAccessType.TOKEN)
            .spec(AzureRepoTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
            .build();
    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .url(url)
                                                            .validationRepo(validationRepo)
                                                            .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .apiAccess(azureRepoApiAccessDTO)
                                                            .build();

    final AzureRepoConnector azureRepoConnector =
        AzureRepoConnector.builder()
            .hasApiAccess(true)
            .url(url)
            .validationRepo(validationRepo)
            .azureRepoApiAccess(AzureRepoTokenApiAccess.builder().tokenRef(tokenRef).build())
            .apiAccessType(AzureRepoApiAccessType.TOKEN)
            .connectionType(AzureRepoConnectionType.PROJECT)
            .authType(HTTP)
            .authenticationDetails(
                AzureRepoHttpAuthentication.builder()
                    .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                    .auth(AzureRepoUsernameToken.builder().usernameRef(usernameRef).tokenRef(tokenRef).build())
                    .build())
            .build();
    final AzureRepoConnectorDTO azureRepoConnectorDTO1 = azureRepoEntityToDTO.createConnectorDTO(azureRepoConnector);
    ObjectMapper objectMapper = new ObjectMapper();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(azureRepoConnectorDTO1)))
        .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(azureRepoConnectorDTO)));
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testToConnectorEntitySSH() throws IOException {
    final String url = "url";
    final String tokenRef = "tokenRef";
    final String validationRepo = "validationRepo";
    final String sshKeyRef = "sshKeyRef";

    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(SSH)
            .credentials(
                AzureRepoSshCredentialsDTO.builder().sshKeyRef(SecretRefHelper.createSecretRef(sshKeyRef)).build())
            .build();

    final AzureRepoApiAccessDTO azureRepoApiAccessDTO =
        AzureRepoApiAccessDTO.builder()
            .type(AzureRepoApiAccessType.TOKEN)
            .spec(AzureRepoTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
            .build();
    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .url(url)
                                                            .validationRepo(validationRepo)
                                                            .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .apiAccess(azureRepoApiAccessDTO)
                                                            .build();

    final AzureRepoConnector azureRepoConnector =
        AzureRepoConnector.builder()
            .hasApiAccess(true)
            .url(url)
            .validationRepo(validationRepo)
            .azureRepoApiAccess(AzureRepoTokenApiAccess.builder().tokenRef(tokenRef).build())
            .apiAccessType(AzureRepoApiAccessType.TOKEN)
            .connectionType(AzureRepoConnectionType.PROJECT)
            .authType(SSH)
            .authenticationDetails(AzureRepoSshAuthentication.builder().sshKeyRef(sshKeyRef).build())
            .build();
    final AzureRepoConnectorDTO azureRepoConnectorDTO1 = azureRepoEntityToDTO.createConnectorDTO(azureRepoConnector);
    ObjectMapper objectMapper = new ObjectMapper();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(azureRepoConnectorDTO1)))
        .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(azureRepoConnectorDTO)));
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testToConnectorEntityInvalidCases() {
    // Entity value is null
    final AzureRepoConnector azureRepoConnector = null;
    assertThatThrownBy(() -> azureRepoEntityToDTO.createConnectorDTO(azureRepoConnector))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Connector object not found");

    // Git Auth Type is null
    final String url = "url";
    final String tokenRef = "tokenRef";
    final String validationRepo = "validationRepo";

    final AzureRepoConnector azureRepoConnector1 =
        AzureRepoConnector.builder()
            .hasApiAccess(true)
            .url(url)
            .validationRepo(validationRepo)
            .azureRepoApiAccess(AzureRepoTokenApiAccess.builder().tokenRef(tokenRef).build())
            .apiAccessType(AzureRepoApiAccessType.TOKEN)
            .connectionType(AzureRepoConnectionType.PROJECT)
            .authenticationDetails(AzureRepoHttpAuthentication.builder()
                                       .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                                       .auth(AzureRepoUsernameToken.builder().tokenRef(tokenRef).build())
                                       .build())
            .build();
    assertThatThrownBy(() -> azureRepoEntityToDTO.createConnectorDTO(azureRepoConnector1))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("AzureRepo Auth Type not found");

    // HTTP Auth Type is null
    final AzureRepoConnector azureRepoConnector2 =
        AzureRepoConnector.builder()
            .hasApiAccess(true)
            .url(url)
            .validationRepo(validationRepo)
            .authType(HTTP)
            .azureRepoApiAccess(AzureRepoTokenApiAccess.builder().tokenRef(tokenRef).build())
            .apiAccessType(AzureRepoApiAccessType.TOKEN)
            .connectionType(AzureRepoConnectionType.PROJECT)
            .authenticationDetails(AzureRepoHttpAuthentication.builder()
                                       .auth(AzureRepoUsernameToken.builder().tokenRef(tokenRef).build())
                                       .build())
            .build();
    assertThatThrownBy(() -> azureRepoEntityToDTO.createConnectorDTO(azureRepoConnector2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("AzureRepo Http Auth Type not found");

    // API Access Type is null
    final AzureRepoConnector azureRepoConnector3 =
        AzureRepoConnector.builder()
            .hasApiAccess(true)
            .url(url)
            .validationRepo(validationRepo)
            .authType(HTTP)
            .azureRepoApiAccess(AzureRepoTokenApiAccess.builder().tokenRef(tokenRef).build())
            .connectionType(AzureRepoConnectionType.PROJECT)
            .authenticationDetails(AzureRepoHttpAuthentication.builder()
                                       .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                                       .auth(AzureRepoUsernameToken.builder().tokenRef(tokenRef).build())
                                       .build())
            .build();
    assertThatThrownBy(() -> azureRepoEntityToDTO.createConnectorDTO(azureRepoConnector3))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("AzureRepo Api Access Type not found");
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetAzureRepoConnectionTypeDTO() {
    assertThat(azureRepoEntityToDTO.getAzureRepoConnectionTypeDTO(AzureRepoConnectionType.REPO))
        .isEqualTo(AzureRepoConnectionTypeDTO.REPO);
  }
}