/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.delegate.beans.connector.ConnectorType.SPLUNK;
import static io.harness.git.model.ChangeType.ADD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.embedded.splunkconnector.SplunkConnector;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.connector.validator.ConnectionValidator;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@OwnedBy(DX)
public class SplunkConnectorTest extends CategoryTest {
  @Mock ConnectorMapper connectorMapper;
  @Mock ConnectorRepository connectorRepository;
  @Mock private Map<String, ConnectionValidator> connectionValidatorMap;
  @InjectMocks DefaultConnectorServiceImpl connectorService;
  @Mock SecretRefInputValidationHelper secretRefInputValidationHelper;
  @Mock ConnectorEntityReferenceHelper connectorEntityReferenceHelper;
  @Mock GitSyncSdkService gitSyncSdkService;

  String userName = "userName";
  String password = "password";
  String identifier = "identifier";
  String secretIdentifier = "secretIdentifier";
  String name = "name";
  String splunkUrl = "https://xwz.com";
  ConnectorDTO connectorRequest;
  ConnectorResponseDTO connectorResponse;
  SplunkConnector connector;
  String accountIdentifier = "accountIdentifier";
  @Rule public ExpectedException expectedEx = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    connector = SplunkConnector.builder()
                    .username(userName)
                    .accountId(accountIdentifier)
                    .splunkUrl(splunkUrl)
                    .passwordRef(password)
                    .build();

    connector.setType(SPLUNK);
    connector.setIdentifier(identifier);
    connector.setName(name);

    SecretRefData secretRefData = SecretRefData.builder().identifier(secretIdentifier).build();
    SplunkConnectorDTO splunkConnectorDTO = SplunkConnectorDTO.builder()
                                                .username(userName)
                                                .accountId(accountIdentifier)
                                                .splunkUrl(splunkUrl)
                                                .passwordRef(secretRefData)
                                                .build();

    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name(name)
                                         .identifier(identifier)
                                         .connectorType(SPLUNK)
                                         .connectorConfig(splunkConnectorDTO)
                                         .build();
    connectorRequest = ConnectorDTO.builder().connectorInfo(connectorInfo).build();
    connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfo).build();
    when(connectorRepository.save(connector, connectorRequest, ADD, null)).thenReturn(connector);
    when(connectorMapper.writeDTO(connector)).thenReturn(connectorResponse);
    when(connectorMapper.toConnector(connectorRequest, accountIdentifier)).thenReturn(connector);
    when(gitSyncSdkService.isGitSyncEnabled(accountIdentifier, null, null)).thenReturn(true);
    doNothing().when(secretRefInputValidationHelper).validateTheSecretInput(any(), any());
  }

  private ConnectorResponseDTO createConnector() {
    return connectorService.create(connectorRequest, accountIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.NEMANJA)
  @Category(UnitTests.class)
  public void testCreateSplunkConnector() {
    ConnectorResponseDTO connectorDTOOutput = createConnector();
    ensureSplunkConnectorFieldsAreCorrect(connectorDTOOutput);
  }

  private void ensureSplunkConnectorFieldsAreCorrect(ConnectorResponseDTO connectorResponse) {
    ConnectorInfoDTO connector = connectorResponse.getConnector();
    assertThat(connector).isNotNull();
    assertThat(connector.getName()).isEqualTo(name);
    assertThat(connector.getIdentifier()).isEqualTo(identifier);
    assertThat(connector.getConnectorType()).isEqualTo(SPLUNK);
    SplunkConnectorDTO splunkConnectorDTO = (SplunkConnectorDTO) connector.getConnectorConfig();
    assertThat(splunkConnectorDTO).isNotNull();
    assertThat(splunkConnectorDTO.getUsername()).isEqualTo(userName);
    assertThat(splunkConnectorDTO.getPasswordRef()).isNotNull();
    assertThat(splunkConnectorDTO.getPasswordRef().getIdentifier()).isEqualTo(secretIdentifier);
    assertThat(splunkConnectorDTO.getSplunkUrl()).isEqualTo(splunkUrl + "/");
    assertThat(splunkConnectorDTO.getAccountId()).isEqualTo(accountIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.NEMANJA)
  @Category(UnitTests.class)
  public void testGetSplunkConnector() {
    createConnector();
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Optional.of(connector));
    ConnectorResponseDTO connectorDTO = connectorService.get(accountIdentifier, null, null, identifier).get();
    ensureSplunkConnectorFieldsAreCorrect(connectorDTO);
  }
}
