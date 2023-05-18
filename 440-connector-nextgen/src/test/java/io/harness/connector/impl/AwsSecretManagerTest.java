/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.delegate.beans.connector.ConnectorType.AWS_SECRET_MANAGER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorsTestBase;
import io.harness.connector.validator.ConnectionValidator;
import io.harness.connector.validator.KubernetesConnectionValidator;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSMCredentialSpecAssumeSTSDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSMCredentialSpecManualConfigDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerCredentialDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerCredentialType;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
@Slf4j
public class AwsSecretManagerTest extends ConnectorsTestBase {
  @Mock KubernetesConnectionValidator kubernetesConnectionValidator;
  @Mock ConnectorRepository connectorRepository;
  @Mock private Map<String, ConnectionValidator> connectionValidatorMap;
  @Mock SecretRefInputValidationHelper secretRefInputValidationHelper;
  @Mock ConnectorEntityReferenceHelper connectorEntityReferenceHelper;
  @Inject @InjectMocks DefaultConnectorServiceImpl connectorService;

  String identifier = "identifier";
  String name = "name";
  String description = "description";

  String accountIdentifier = "accountIdentifier";

  SecretRefData accessKey;
  SecretRefData secretKey;
  ConnectorDTO connectorRequest;
  ConnectorResponseDTO connectorResponse;
  AwsSecretManagerDTO connectorDTO;
  @Rule public ExpectedException expectedEx = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    accessKey = SecretRefData.builder().identifier("accessKey").scope(Scope.ACCOUNT).build();
    secretKey = SecretRefData.builder().identifier("secretKey").scope(Scope.ACCOUNT).build();
    doNothing().when(secretRefInputValidationHelper).validateTheSecretInput(any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateAwsSMConnectorManualConfig() {
    AwsSecretManagerCredentialDTO awsCredentialDTO =
        AwsSecretManagerCredentialDTO.builder()
            .credentialType(AwsSecretManagerCredentialType.MANUAL_CONFIG)
            .config(AwsSMCredentialSpecManualConfigDTO.builder().accessKey(accessKey).secretKey(secretKey).build())
            .build();
    ConnectorDTO connectorDTO = createConnectorDTO(awsCredentialDTO, null);
    ConnectorResponseDTO connectorDTOOutput = createConnector(connectorDTO);
    ensureAwsSMConnectorFieldsAreCorrect(connectorDTOOutput);
    AwsSecretManagerDTO awsConnectorDTO = (AwsSecretManagerDTO) connectorDTOOutput.getConnector().getConnectorConfig();
    assertThat(awsConnectorDTO).isNotNull();
    assertThat(awsConnectorDTO.getCredential()).isNotNull();
    assertThat(awsConnectorDTO.getCredential().getCredentialType())
        .isEqualByComparingTo(AwsSecretManagerCredentialType.MANUAL_CONFIG);
    assertThat(awsConnectorDTO.getCredential().getConfig()).isNotNull();
    assertThat(((AwsSMCredentialSpecManualConfigDTO) awsConnectorDTO.getCredential().getConfig()).getAccessKey())
        .isEqualTo(accessKey);
    assertThat(((AwsSMCredentialSpecManualConfigDTO) awsConnectorDTO.getCredential().getConfig()).getSecretKey())
        .isEqualTo(secretKey);
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateAwsSMConnectorIAMRole() {
    AwsSecretManagerCredentialDTO awsCredentialDTO = AwsSecretManagerCredentialDTO.builder()
                                                         .credentialType(AwsSecretManagerCredentialType.ASSUME_IAM_ROLE)
                                                         .config(null)
                                                         .build();
    Set<String> delegateSelectors = new HashSet<>(Collections.singleton("delegate"));
    ConnectorDTO connectorDTO = createConnectorDTO(awsCredentialDTO, delegateSelectors);
    ConnectorResponseDTO connectorDTOOutput = createConnector(connectorDTO);
    ensureAwsSMConnectorFieldsAreCorrect(connectorDTOOutput);
    AwsSecretManagerDTO awsConnectorDTO = (AwsSecretManagerDTO) connectorDTOOutput.getConnector().getConnectorConfig();
    assertThat(awsConnectorDTO).isNotNull();
    assertThat(awsConnectorDTO.getCredential()).isNotNull();
    assertThat(awsConnectorDTO.getCredential().getCredentialType())
        .isEqualByComparingTo(AwsSecretManagerCredentialType.ASSUME_IAM_ROLE);
    assertThat(awsConnectorDTO.getCredential().getConfig()).isNull();
    assertThat(awsConnectorDTO.getDelegateSelectors()).isNotNull();
    assertThat(awsConnectorDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateAwsSMConnectorSTSRole() {
    AwsSecretManagerCredentialDTO awsCredentialDTO =
        AwsSecretManagerCredentialDTO.builder()
            .credentialType(AwsSecretManagerCredentialType.ASSUME_STS_ROLE)
            .config(AwsSMCredentialSpecAssumeSTSDTO.builder().roleArn("roleArn").build())
            .build();
    Set<String> delegateSelectors = new HashSet<>(Collections.singleton("delegate"));
    ConnectorDTO connectorDTO = createConnectorDTO(awsCredentialDTO, delegateSelectors);
    ConnectorResponseDTO connectorDTOOutput = createConnector(connectorDTO);
    ensureAwsSMConnectorFieldsAreCorrect(connectorDTOOutput);
    AwsSecretManagerDTO awsConnectorDTO = (AwsSecretManagerDTO) connectorDTOOutput.getConnector().getConnectorConfig();
    assertThat(awsConnectorDTO).isNotNull();
    assertThat(awsConnectorDTO.getCredential()).isNotNull();
    assertThat(awsConnectorDTO.getCredential().getCredentialType())
        .isEqualByComparingTo(AwsSecretManagerCredentialType.ASSUME_STS_ROLE);
    assertThat(((AwsSMCredentialSpecAssumeSTSDTO) awsConnectorDTO.getCredential().getConfig()).getRoleArn())
        .isEqualTo("roleArn");
    assertThat(awsConnectorDTO.getDelegateSelectors()).isNotNull();
    assertThat(awsConnectorDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
  }

  private ConnectorResponseDTO createConnector(ConnectorDTO connectorRequest) {
    return connectorService.create(connectorRequest, accountIdentifier);
  }

  private ConnectorDTO createConnectorDTO(
      AwsSecretManagerCredentialDTO awsCredentialDTO, Set<String> delegateSelectors) {
    AwsSecretManagerDTO awsConnectorDTO =
        AwsSecretManagerDTO.builder().credential(awsCredentialDTO).delegateSelectors(delegateSelectors).build();

    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .name(name)
                           .identifier(identifier)
                           .description(description)
                           .connectorType(AWS_SECRET_MANAGER)
                           .connectorConfig(awsConnectorDTO)
                           .build())
        .build();
  }

  private void ensureAwsSMConnectorFieldsAreCorrect(ConnectorResponseDTO connectorResponse) {
    ConnectorInfoDTO connector = connectorResponse.getConnector();
    assertThat(connector).isNotNull();
    assertThat(connector.getName()).isEqualTo(name);
    assertThat(connector.getIdentifier()).isEqualTo(identifier);
    assertThat(connector.getConnectorType()).isEqualTo(AWS_SECRET_MANAGER);
  }
}
