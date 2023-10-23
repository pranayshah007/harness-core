/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.azurerepoconnectormapper;

import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType.TOKEN;
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
import io.harness.connector.mappers.azurerepomapper.AzureRepoDTOToEntity;
import io.harness.delegate.beans.connector.scm.GitAuthType;
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

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class AzureRepoDTOToEntityTest extends CategoryTest {
  @InjectMocks AzureRepoDTOToEntity azureRepoDTOToEntity;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testToConnectorEntityHTTPAccount() {
    final String url = "url";
    final String tokenRef = "tokenRef";
    final String validationRepo = "validationRepo";

    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(
                AzureRepoHttpCredentialsDTO.builder()
                    .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                    .httpCredentialsSpec(
                        AzureRepoUsernameTokenDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
                    .build())
            .build();

    final AzureRepoApiAccessDTO azureRepoApiAccessDTO =
        AzureRepoApiAccessDTO.builder()
            .type(TOKEN)
            .spec(AzureRepoTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
            .build();

    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .url(url)
                                                            .validationRepo(validationRepo)
                                                            .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .apiAccess(azureRepoApiAccessDTO)
                                                            .build();

    final AzureRepoConnector azureRepoConnector = azureRepoDTOToEntity.toConnectorEntity(azureRepoConnectorDTO);
    assertThat(azureRepoConnector).isNotNull();
    assertThat(azureRepoConnector.getUrl()).isEqualTo(url);
    assertThat(azureRepoConnector.getValidationRepo()).isEqualTo(validationRepo);
    assertThat(azureRepoConnector.getApiAccessType()).isEqualTo(TOKEN);
    assertThat(azureRepoConnector.getAuthType()).isEqualTo(HTTP);
    assertThat(azureRepoConnector.getAuthenticationDetails())
        .isEqualTo(AzureRepoHttpAuthentication.builder()
                       .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                       .auth(AzureRepoUsernameToken.builder().tokenRef(tokenRef).build())
                       .build());
    assertThat(azureRepoConnector.getAzureRepoApiAccess())
        .isEqualTo(AzureRepoTokenApiAccess.builder().tokenRef(tokenRef).build());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testToConnectorEntitySSHAccount() {
    final String url = "url";
    final String sshKeyRef = "sshKeyRef";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(GitAuthType.SSH)
            .credentials(
                AzureRepoSshCredentialsDTO.builder().sshKeyRef(SecretRefHelper.createSecretRef(sshKeyRef)).build())
            .build();

    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .url(url)
                                                            .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .build();

    final AzureRepoConnector azureRepoConnector = azureRepoDTOToEntity.toConnectorEntity(azureRepoConnectorDTO);
    assertThat(azureRepoConnector).isNotNull();
    assertThat(azureRepoConnector.getUrl()).isEqualTo(url);
    assertThat(azureRepoConnector.getAuthType()).isEqualTo(GitAuthType.SSH);
    assertThat(azureRepoConnector.getAuthenticationDetails())
        .isEqualTo(AzureRepoSshAuthentication.builder().sshKeyRef(sshKeyRef).build());
    assertThat(azureRepoConnector.getAzureRepoApiAccess()).isNull();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testToConnectorEntityHTTPRepo() {
    final String url = "url";
    final String tokenRef = "tokenRef";
    final String usernameRef = "usernameRef";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(AzureRepoHttpCredentialsDTO.builder()
                             .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                             .httpCredentialsSpec(AzureRepoUsernameTokenDTO.builder()
                                                      .tokenRef(SecretRefHelper.createSecretRef(tokenRef))
                                                      .usernameRef(SecretRefHelper.createSecretRef(usernameRef))
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
                                                            .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .apiAccess(azureRepoApiAccessDTO)
                                                            .build();

    final AzureRepoConnector azureRepoConnector = azureRepoDTOToEntity.toConnectorEntity(azureRepoConnectorDTO);
    assertThat(azureRepoConnector).isNotNull();
    assertThat(azureRepoConnector.getUrl()).isEqualTo(url);
    assertThat(azureRepoConnector.getApiAccessType()).isEqualTo(TOKEN);
    assertThat(azureRepoConnector.getAuthType()).isEqualTo(HTTP);
    assertThat(azureRepoConnector.getAuthenticationDetails())
        .isEqualTo(AzureRepoHttpAuthentication.builder()
                       .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                       .auth(AzureRepoUsernameToken.builder().tokenRef(tokenRef).usernameRef(usernameRef).build())
                       .build());
    assertThat(azureRepoConnector.getAzureRepoApiAccess())
        .isEqualTo(AzureRepoTokenApiAccess.builder().tokenRef(tokenRef).build());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testToConnectorEntitySSHRepo() {
    final String url = "url";
    final String sshKeyRef = "sshKeyRef";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(GitAuthType.SSH)
            .credentials(
                AzureRepoSshCredentialsDTO.builder().sshKeyRef(SecretRefHelper.createSecretRef(sshKeyRef)).build())
            .build();

    final AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                            .url(url)
                                                            .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                            .authentication(azureRepoAuthenticationDTO)
                                                            .build();

    final AzureRepoConnector azureRepoConnector = azureRepoDTOToEntity.toConnectorEntity(azureRepoConnectorDTO);
    assertThat(azureRepoConnector).isNotNull();
    assertThat(azureRepoConnector.getUrl()).isEqualTo(url);
    assertThat(azureRepoConnector.getAuthType()).isEqualTo(GitAuthType.SSH);
    assertThat(azureRepoConnector.getAuthenticationDetails())
        .isEqualTo(AzureRepoSshAuthentication.builder().sshKeyRef(sshKeyRef).build());
    assertThat(azureRepoConnector.getAzureRepoApiAccess()).isNull();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testToConnectorEntityInvalidCases() {
    // DTO is null
    final AzureRepoConnectorDTO azureRepoConnectorDTO = null;
    assertThatThrownBy(() -> azureRepoDTOToEntity.toConnectorEntity(azureRepoConnectorDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("AzureRepo Config DTO is not found");

    // Authentication DTO is null
    final String url = "url";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO = null;
    AzureRepoConnectorDTO azureRepoConnectorDTO1 = AzureRepoConnectorDTO.builder()
                                                       .url(url)
                                                       .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                       .authentication(azureRepoAuthenticationDTO)
                                                       .build();
    assertThatThrownBy(() -> azureRepoDTOToEntity.toConnectorEntity(azureRepoConnectorDTO1))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No Authentication Details Found in the ConnectorDisconnectHandler");

    // Git Auth Type is null
    final String sshKeyRef = "sshKeyRef";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO1 =
        AzureRepoAuthenticationDTO.builder()
            .credentials(
                AzureRepoSshCredentialsDTO.builder().sshKeyRef(SecretRefHelper.createSecretRef(sshKeyRef)).build())
            .build();
    AzureRepoConnectorDTO azureRepoConnectorDTO2 = AzureRepoConnectorDTO.builder()
                                                       .url(url)
                                                       .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                       .authentication(azureRepoAuthenticationDTO1)
                                                       .build();
    assertThatThrownBy(() -> azureRepoDTOToEntity.toConnectorEntity(azureRepoConnectorDTO2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Auth Type not found");

    // HTTP Auth Type is null
    final String tokenRef = "tokenRef";
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO2 =
        AzureRepoAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(
                AzureRepoHttpCredentialsDTO.builder()
                    .httpCredentialsSpec(
                        AzureRepoUsernameTokenDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
                    .build())
            .build();

    final AzureRepoApiAccessDTO azureRepoApiAccessDTO =
        AzureRepoApiAccessDTO.builder()
            .type(AzureRepoApiAccessType.TOKEN)
            .spec(AzureRepoTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
            .build();

    final AzureRepoConnectorDTO azureRepoConnectorDTO3 = AzureRepoConnectorDTO.builder()
                                                             .url(url)
                                                             .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                             .authentication(azureRepoAuthenticationDTO2)
                                                             .apiAccess(azureRepoApiAccessDTO)
                                                             .build();

    assertThatThrownBy(() -> azureRepoDTOToEntity.toConnectorEntity(azureRepoConnectorDTO3))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("AzureRepo Http Auth Type not found");

    // API Access Type is null
    final AzureRepoAuthenticationDTO azureRepoAuthenticationDTO3 =
        AzureRepoAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(
                AzureRepoHttpCredentialsDTO.builder()
                    .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                    .httpCredentialsSpec(
                        AzureRepoUsernameTokenDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
                    .build())
            .build();
    final AzureRepoApiAccessDTO azureRepoApiAccessDTO1 =
        AzureRepoApiAccessDTO.builder()
            .spec(AzureRepoTokenSpecDTO.builder().tokenRef(SecretRefHelper.createSecretRef(tokenRef)).build())
            .build();
    final AzureRepoConnectorDTO azureRepoConnectorDTO4 = AzureRepoConnectorDTO.builder()
                                                             .url(url)
                                                             .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                             .authentication(azureRepoAuthenticationDTO3)
                                                             .apiAccess(azureRepoApiAccessDTO1)
                                                             .build();
    assertThatThrownBy(() -> azureRepoDTOToEntity.toConnectorEntity(azureRepoConnectorDTO4))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("AzureRepo Api Access Type not found");
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetAzureRepoConnectionType() {
    assertThat(azureRepoDTOToEntity.getAzureRepoConnectionType(AzureRepoConnectionTypeDTO.REPO))
        .isEqualTo(AzureRepoConnectionType.REPO);
  }
}