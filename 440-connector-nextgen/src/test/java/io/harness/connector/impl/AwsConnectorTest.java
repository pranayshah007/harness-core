/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.delegate.beans.connector.ConnectorType.AWS;

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
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
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

@OwnedBy(DX)
@Slf4j
public class AwsConnectorTest extends ConnectorsTestBase {
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

  String accessKey = "accessKey";
  SecretRefData secretKey;
  ConnectorDTO connectorRequest;
  ConnectorResponseDTO connectorResponse;
  AwsConnectorDTO connectorDTO;
  @Rule public ExpectedException expectedEx = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    secretKey = SecretRefData.builder().identifier("secretKey").scope(Scope.ACCOUNT).build();
    doNothing().when(secretRefInputValidationHelper).validateTheSecretInput(any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateAwsConnectorManualConfig() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder()
            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
            .config(AwsManualConfigSpecDTO.builder().accessKey(accessKey).secretKeyRef(secretKey).build())
            .build();
    ConnectorDTO connectorDTO = createConnectorDTO(awsCredentialDTO, null);
    ConnectorResponseDTO connectorDTOOutput = createConnector(connectorDTO);
    ensureAwsConnectorFieldsAreCorrect(connectorDTOOutput);
    AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTOOutput.getConnector().getConnectorConfig();
    assertThat(awsConnectorDTO).isNotNull();
    assertThat(awsConnectorDTO.getCredential()).isNotNull();
    assertThat(awsConnectorDTO.getCredential().getAwsCredentialType())
        .isEqualByComparingTo(AwsCredentialType.MANUAL_CREDENTIALS);
    assertThat(awsConnectorDTO.getCredential().getConfig()).isNotNull();
    assertThat(((AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig()).getAccessKey())
        .isEqualTo(accessKey);
    assertThat(((AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig()).getSecretKeyRef())
        .isEqualTo(secretKey);
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateAwsConnectorInheritDelegate() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).config(null).build();
    Set<String> delegateSelectors = new HashSet<>(Collections.singleton("delegate"));
    ConnectorDTO connectorDTO = createConnectorDTO(awsCredentialDTO, delegateSelectors);
    ConnectorResponseDTO connectorDTOOutput = createConnector(connectorDTO);
    ensureAwsConnectorFieldsAreCorrect(connectorDTOOutput);
    AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTOOutput.getConnector().getConnectorConfig();
    assertThat(awsConnectorDTO).isNotNull();
    assertThat(awsConnectorDTO.getCredential()).isNotNull();
    assertThat(awsConnectorDTO.getCredential().getAwsCredentialType())
        .isEqualByComparingTo(AwsCredentialType.INHERIT_FROM_DELEGATE);
    assertThat(awsConnectorDTO.getCredential().getConfig()).isNull();
    assertThat(awsConnectorDTO.getDelegateSelectors()).isNotNull();
    assertThat(awsConnectorDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateAwsConnectorWithCrossAccountAccess() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder()
            .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
            .config(null)
            .crossAccountAccess(CrossAccountAccessDTO.builder().crossAccountRoleArn("crossAccountRoleArn").build())
            .build();
    Set<String> delegateSelectors = new HashSet<>(Collections.singleton("delegate"));
    ConnectorDTO connectorDTO = createConnectorDTO(awsCredentialDTO, delegateSelectors);
    ConnectorResponseDTO connectorDTOOutput = createConnector(connectorDTO);
    ensureAwsConnectorFieldsAreCorrect(connectorDTOOutput);
    AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTOOutput.getConnector().getConnectorConfig();
    assertThat(awsConnectorDTO).isNotNull();
    assertThat(awsConnectorDTO.getCredential()).isNotNull();
    assertThat(awsConnectorDTO.getCredential().getAwsCredentialType())
        .isEqualByComparingTo(AwsCredentialType.INHERIT_FROM_DELEGATE);
    assertThat(awsConnectorDTO.getCredential().getConfig()).isNull();
    assertThat(awsConnectorDTO.getDelegateSelectors()).isNotNull();
    assertThat(awsConnectorDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
    assertThat(awsConnectorDTO.getCredential().getCrossAccountAccess()).isNotNull();
    assertThat(awsConnectorDTO.getCredential().getCrossAccountAccess().getCrossAccountRoleArn()).isNotNull();
    assertThat(awsConnectorDTO.getCredential().getCrossAccountAccess().getCrossAccountRoleArn())
        .isEqualTo("crossAccountRoleArn");
  }

  @Test
  @Owner(developers = OwnerRule.ACHYUTH)
  @Category(UnitTests.class)
  public void testCreateAwsConnectorIRSA() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.IRSA).config(null).build();
    Set<String> delegateSelectors = new HashSet<>(Collections.singleton("delegate"));
    ConnectorDTO connectorDTO = createConnectorDTO(awsCredentialDTO, delegateSelectors);
    ConnectorResponseDTO connectorDTOOutput = createConnector(connectorDTO);
    ensureAwsConnectorFieldsAreCorrect(connectorDTOOutput);
    AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTOOutput.getConnector().getConnectorConfig();
    assertThat(awsConnectorDTO).isNotNull();
    assertThat(awsConnectorDTO.getCredential()).isNotNull();
    assertThat(awsConnectorDTO.getCredential().getAwsCredentialType()).isEqualByComparingTo(AwsCredentialType.IRSA);
    assertThat(awsConnectorDTO.getCredential().getConfig()).isNull();
    assertThat(awsConnectorDTO.getDelegateSelectors()).isNotNull();
    assertThat(awsConnectorDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
  }

  @Test
  @Owner(developers = OwnerRule.ACHYUTH)
  @Category(UnitTests.class)
  public void testCreateAwsConnectorWithCrossAccountAccessIRSA() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder()
            .awsCredentialType(AwsCredentialType.IRSA)
            .config(null)
            .crossAccountAccess(CrossAccountAccessDTO.builder().crossAccountRoleArn("crossAccountRoleArn").build())
            .build();
    Set<String> delegateSelectors = new HashSet<>(Collections.singleton("delegate"));
    ConnectorDTO connectorDTO = createConnectorDTO(awsCredentialDTO, delegateSelectors);
    ConnectorResponseDTO connectorDTOOutput = createConnector(connectorDTO);
    ensureAwsConnectorFieldsAreCorrect(connectorDTOOutput);
    AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTOOutput.getConnector().getConnectorConfig();
    assertThat(awsConnectorDTO).isNotNull();
    assertThat(awsConnectorDTO.getCredential()).isNotNull();
    assertThat(awsConnectorDTO.getCredential().getAwsCredentialType()).isEqualByComparingTo(AwsCredentialType.IRSA);
    assertThat(awsConnectorDTO.getCredential().getConfig()).isNull();
    assertThat(awsConnectorDTO.getDelegateSelectors()).isNotNull();
    assertThat(awsConnectorDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
    assertThat(awsConnectorDTO.getCredential().getCrossAccountAccess()).isNotNull();
    assertThat(awsConnectorDTO.getCredential().getCrossAccountAccess().getCrossAccountRoleArn()).isNotNull();
    assertThat(awsConnectorDTO.getCredential().getCrossAccountAccess().getCrossAccountRoleArn())
        .isEqualTo("crossAccountRoleArn");
  }

  private ConnectorResponseDTO createConnector(ConnectorDTO connectorRequest) {
    return connectorService.create(connectorRequest, accountIdentifier);
  }

  private ConnectorDTO createConnectorDTO(AwsCredentialDTO awsCredentialDTO, Set<String> delegateSelectors) {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(awsCredentialDTO).delegateSelectors(delegateSelectors).build();

    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .name(name)
                           .identifier(identifier)
                           .description(description)
                           .connectorType(AWS)
                           .connectorConfig(awsConnectorDTO)
                           .build())
        .build();
  }

  private void ensureAwsConnectorFieldsAreCorrect(ConnectorResponseDTO connectorResponse) {
    ConnectorInfoDTO connector = connectorResponse.getConnector();
    assertThat(connector).isNotNull();
    assertThat(connector.getName()).isEqualTo(name);
    assertThat(connector.getIdentifier()).isEqualTo(identifier);
    assertThat(connector.getConnectorType()).isEqualTo(AWS);
  }
}
