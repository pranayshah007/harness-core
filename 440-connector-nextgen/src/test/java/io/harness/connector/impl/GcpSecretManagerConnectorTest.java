/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.delegate.beans.connector.ConnectorType.GCP_SECRET_MANAGER;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorsTestBase;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSecretManagerConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class GcpSecretManagerConnectorTest extends ConnectorsTestBase {
  @Mock SecretRefInputValidationHelper secretRefInputValidationHelper;

  @Inject @InjectMocks DefaultConnectorServiceImpl connectorService;

  private static String credentialsRef = "account.credentials-ref";
  private static String identifier;
  private static String name;
  private static String description;
  private static String accountIdentifier;

  @Before
  public void setUp() throws Exception {
    setupVariableValues();
    MockitoAnnotations.initMocks(this);
    doNothing().when(secretRefInputValidationHelper).validateTheSecretInput(any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.SHREYAS)
  @Category(UnitTests.class)
  public void testConnectorCreationForDefaultConnector() {
    GcpSecretManagerConnectorDTO gcpSecretManagerConnectorDTO = createDefaultGcpConnector(credentialsRef);
    ConnectorDTO connectorDTO = createConnectorDTO(gcpSecretManagerConnectorDTO);
    ConnectorResponseDTO connectorResponseDTO = createConnector(connectorDTO);
    ensureConnectorFieldsAreCorrect(connectorResponseDTO);
    ensureConnectorConfigDTOFieldsAreCorrect(connectorResponseDTO.getConnector().getConnectorConfig(), true);
  }

  @Test
  @Owner(developers = OwnerRule.SHREYAS)
  @Category(UnitTests.class)
  public void testConnectorCreationForNonDefaultConnector() {
    GcpSecretManagerConnectorDTO gcpSecretManagerConnectorDTO = createNotDefaultGcpConnector(credentialsRef);
    ConnectorDTO connectorDTO = createConnectorDTO(gcpSecretManagerConnectorDTO);
    ConnectorResponseDTO connectorResponseDTO = createConnector(connectorDTO);
    ensureConnectorFieldsAreCorrect(connectorResponseDTO);
    ensureConnectorConfigDTOFieldsAreCorrect(connectorResponseDTO.getConnector().getConnectorConfig(), false);
  }

  private ConnectorResponseDTO createConnector(ConnectorDTO connectorRequest) {
    return connectorService.create(connectorRequest, accountIdentifier);
  }

  private GcpSecretManagerConnectorDTO createDefaultGcpConnector(String credentialsRef) {
    return createGcpSecretManagerConnector(true, credentialsRef);
  }

  private GcpSecretManagerConnectorDTO createNotDefaultGcpConnector(String credentialsRef) {
    return createGcpSecretManagerConnector(false, credentialsRef);
  }

  private GcpSecretManagerConnectorDTO createGcpSecretManagerConnector(boolean isDefault, String credentialsRef) {
    SecretRefData secretRefData = SecretRefHelper.createSecretRef(credentialsRef);
    return GcpSecretManagerConnectorDTO.builder().credentialsRef(secretRefData).isDefault(isDefault).build();
  }

  private ConnectorDTO createConnectorDTO(GcpSecretManagerConnectorDTO gcpSecretManagerConnectorDTO) {
    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .name(name)
                           .identifier(identifier)
                           .description(description)
                           .connectorType(GCP_SECRET_MANAGER)
                           .connectorConfig(gcpSecretManagerConnectorDTO)
                           .build())
        .build();
  }

  private void ensureConnectorFieldsAreCorrect(ConnectorResponseDTO connectorResponseDTO) {
    ConnectorInfoDTO connector = connectorResponseDTO.getConnector();
    assertThat(connector).isNotNull();
    assertThat(connector.getName()).isEqualTo(name);
    assertThat(connector.getIdentifier()).isEqualTo(identifier);
    assertThat(connector.getConnectorType()).isEqualTo(GCP_SECRET_MANAGER);
  }

  private void ensureConnectorConfigDTOFieldsAreCorrect(
      ConnectorConfigDTO connectorConfigDTO, boolean expectedIsDefaultValue) {
    GcpSecretManagerConnectorDTO gcpSecretManagerConnectorDTO = (GcpSecretManagerConnectorDTO) connectorConfigDTO;
    assertThat(gcpSecretManagerConnectorDTO).isNotNull();
    assertThat(SecretRefHelper.getSecretConfigString(gcpSecretManagerConnectorDTO.getCredentialsRef()))
        .isEqualTo(credentialsRef);
    assertThat(gcpSecretManagerConnectorDTO.getDelegateSelectors()).isNullOrEmpty();
    assertThat(gcpSecretManagerConnectorDTO.isDefault()).isEqualTo(expectedIsDefaultValue);
  }

  private void setupVariableValues() {
    identifier = randomAlphabetic(10);
    name = randomAlphabetic(10);
    description = randomAlphabetic(10);
    accountIdentifier = randomAlphabetic(10);
  }
}
