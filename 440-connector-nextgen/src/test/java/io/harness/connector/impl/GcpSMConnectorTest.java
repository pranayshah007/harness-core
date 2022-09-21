/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.delegate.beans.connector.ConnectorType.GCP_SM;

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
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSMConnectorDTO;
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
public class GcpSMConnectorTest extends ConnectorsTestBase {
  @Mock SecretRefInputValidationHelper secretRefInputValidationHelper;

  @Inject @InjectMocks DefaultConnectorServiceImpl connectorService;

  private static final String CREDENTIALS_REF = "account.credentials-ref";
  private static final String IDENTIFIER = "identifier";
  private static final String NAME = "name";
  private static final String DESCRIPTION = "description";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doNothing().when(secretRefInputValidationHelper).validateTheSecretInput(any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.SHREYAS)
  @Category(UnitTests.class)
  public void testConnectorCreationForDefaultConnector() {
    GcpSMConnectorDTO gcpSMConnectorDTO = createDefaultGcpConnector(CREDENTIALS_REF);
    ConnectorDTO connectorDTO = createConnectorDTO(gcpSMConnectorDTO);
    ConnectorResponseDTO connectorResponseDTO = createConnector(connectorDTO);
    ensureConnectorFieldsAreCorrect(connectorResponseDTO);
    ensureConnectorConfigDTOFieldsAreCorrect(connectorResponseDTO.getConnector().getConnectorConfig(), true);
  }

  @Test
  @Owner(developers = OwnerRule.SHREYAS)
  @Category(UnitTests.class)
  public void testConnectorCreationForNonDefaultConnector() {
    GcpSMConnectorDTO gcpSMConnectorDTO = createNotDefaultGcpConnector(CREDENTIALS_REF);
    ConnectorDTO connectorDTO = createConnectorDTO(gcpSMConnectorDTO);
    ConnectorResponseDTO connectorResponseDTO = createConnector(connectorDTO);
    ensureConnectorFieldsAreCorrect(connectorResponseDTO);
    ensureConnectorConfigDTOFieldsAreCorrect(connectorResponseDTO.getConnector().getConnectorConfig(), false);
  }

  private ConnectorResponseDTO createConnector(ConnectorDTO connectorRequest) {
    return connectorService.create(connectorRequest, ACCOUNT_IDENTIFIER);
  }

  private GcpSMConnectorDTO createDefaultGcpConnector(String credentialsRef) {
    return createGcpSMConnector(true, credentialsRef);
  }

  private GcpSMConnectorDTO createNotDefaultGcpConnector(String credentialsRef) {
    return createGcpSMConnector(false, credentialsRef);
  }

  private GcpSMConnectorDTO createGcpSMConnector(boolean isDefault, String credentialsRef) {
    SecretRefData secretRefData = SecretRefHelper.createSecretRef(credentialsRef);
    return GcpSMConnectorDTO.builder().credentials(secretRefData).isDefault(isDefault).build();
  }

  private ConnectorDTO createConnectorDTO(GcpSMConnectorDTO gcpSMConnectorDTO) {
    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .name(NAME)
                           .identifier(IDENTIFIER)
                           .description(DESCRIPTION)
                           .connectorType(GCP_SM)
                           .connectorConfig(gcpSMConnectorDTO)
                           .build())
        .build();
  }

  private void ensureConnectorFieldsAreCorrect(ConnectorResponseDTO connectorResponseDTO) {
    ConnectorInfoDTO connector = connectorResponseDTO.getConnector();
    assertThat(connector).isNotNull();
    assertThat(connector.getName()).isEqualTo(NAME);
    assertThat(connector.getIdentifier()).isEqualTo(IDENTIFIER);
    assertThat(connector.getConnectorType()).isEqualTo(GCP_SM);
  }

  private void ensureConnectorConfigDTOFieldsAreCorrect(
      ConnectorConfigDTO connectorConfigDTO, boolean expectedIsDefaultValue) {
    GcpSMConnectorDTO gcpSMConnectorDTO = (GcpSMConnectorDTO) connectorConfigDTO;
    assertThat(gcpSMConnectorDTO).isNotNull();
    assertThat(SecretRefHelper.getSecretConfigString(gcpSMConnectorDTO.getCredentials())).isEqualTo(CREDENTIALS_REF);
    assertThat(gcpSMConnectorDTO.getDelegateSelectors()).isNullOrEmpty();
    assertThat(gcpSMConnectorDTO.isDefault()).isEqualTo(expectedIsDefaultValue);
  }
}
