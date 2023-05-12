/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.ConnectorType.TERRAFORM_CLOUD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorsTestBase;
import io.harness.connector.entities.embedded.terraformcloudconncetor.TerraformCloudConfig;
import io.harness.connector.entities.embedded.terraformcloudconncetor.TerraformCloudTokenCredential;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@Slf4j
public class TerraformCloudConnectorTest extends ConnectorsTestBase {
  @Mock SecretRefInputValidationHelper secretRefInputValidationHelper;
  @Mock ConnectorRepository connectorRepository;
  @Inject @InjectMocks DefaultConnectorServiceImpl connectorService;

  String identifier = "identifier";
  String name = "name";
  String description = "description";

  String accountIdentifier = "accountIdentifier";

  String url = "https://app.terraform.io";
  String tokenRef = "tokenRef";
  SecretRefData apiToken;
  TerraformCloudConfig terraformCloudConfig;
  @Rule public ExpectedException expectedEx = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    apiToken = SecretRefData.builder().identifier("tokenRefIdentifier").scope(Scope.ACCOUNT).build();

    terraformCloudConfig = TerraformCloudConfig.builder()
                               .url(url)
                               .credentialType(TerraformCloudCredentialType.API_TOKEN)
                               .credential(TerraformCloudTokenCredential.builder().tokenRef(tokenRef).build())
                               .build();

    doNothing().when(secretRefInputValidationHelper).validateTheSecretInput(any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.BUHA)
  @Category(UnitTests.class)
  public void testCreateTerraformCloudConnectorApiToken() {
    ConnectorDTO connectorDTO = createConnectorDTO();

    ConnectorResponseDTO connectorDTOOutput = connectorService.create(connectorDTO, accountIdentifier);

    ensureTerraformCloudConnectorFieldsAreCorrect(connectorDTOOutput);
    TerraformCloudConnectorDTO terraformCloudConnectorDTO =
        (TerraformCloudConnectorDTO) connectorDTOOutput.getConnector().getConnectorConfig();
    assertThat(terraformCloudConnectorDTO).isNotNull();
    assertThat(terraformCloudConnectorDTO.getTerraformCloudUrl()).isEqualTo(url);
    assertThat(terraformCloudConnectorDTO.getCredential()).isNotNull();
    assertThat(terraformCloudConnectorDTO.getCredential().getType())
        .isEqualByComparingTo(TerraformCloudCredentialType.API_TOKEN);
    assertThat(terraformCloudConnectorDTO.getCredential().getSpec()).isNotNull();
    TerraformCloudTokenCredentialsDTO terraformCloudTokenCredentialsDTO =
        (TerraformCloudTokenCredentialsDTO) terraformCloudConnectorDTO.getCredential().getSpec();
    assertThat(terraformCloudTokenCredentialsDTO.getApiToken()).isEqualTo(apiToken);
  }

  @Test
  @Owner(developers = OwnerRule.BUHA)
  @Category(UnitTests.class)
  public void testGetTerraformCloudConnector() {
    ConnectorDTO connectorDTO = createConnectorDTO();

    connectorService.create(connectorDTO, accountIdentifier);

    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
             anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(terraformCloudConfig));
    ConnectorResponseDTO connectorDTOResponse = connectorService.get(accountIdentifier, null, null, identifier).get();
    assertThat(connectorDTOResponse).isNotNull();
    ensureTerraformCloudConnectorFieldsAreCorrect(connectorDTOResponse);
  }

  private ConnectorDTO createConnectorDTO() {
    return ConnectorDTO.builder()
        .connectorInfo(
            ConnectorInfoDTO.builder()
                .name(name)
                .identifier(identifier)
                .description(description)
                .connectorType(TERRAFORM_CLOUD)
                .connectorConfig(
                    TerraformCloudConnectorDTO.builder()
                        .terraformCloudUrl(url)
                        .credential(TerraformCloudCredentialDTO.builder()
                                        .type(TerraformCloudCredentialType.API_TOKEN)
                                        .spec(TerraformCloudTokenCredentialsDTO.builder().apiToken(apiToken).build())
                                        .build())
                        .build())
                .build())
        .build();
  }

  private void ensureTerraformCloudConnectorFieldsAreCorrect(ConnectorResponseDTO connectorResponse) {
    ConnectorInfoDTO connector = connectorResponse.getConnector();
    assertThat(connector).isNotNull();
    assertThat(connector.getName()).isEqualTo(name);
    assertThat(connector.getIdentifier()).isEqualTo(identifier);
    assertThat(connector.getConnectorType()).isEqualTo(TERRAFORM_CLOUD);
  }
}
