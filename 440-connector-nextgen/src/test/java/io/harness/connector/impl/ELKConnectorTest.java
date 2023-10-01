/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.delegate.beans.connector.ConnectorType.ELASTICSEARCH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.embedded.elkconnector.ELKConnector;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.connector.validator.ConnectionValidator;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.elkconnector.ELKAuthType;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Map;
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
import org.mockito.Spy;

@Slf4j
@OwnedBy(HarnessTeam.CV)
public class ELKConnectorTest extends CategoryTest {
  @Mock ConnectorMapper connectorMapper;
  @Mock ConnectorRepository connectorRepository;
  @Mock ConnectorEntityReferenceHelper connectorEntityReferenceHelper;
  @Mock private Map<String, ConnectionValidator> connectionValidatorMap;
  @Mock GitSyncSdkService gitSyncSdkService;

  @InjectMocks @Spy DefaultConnectorServiceImpl connectorService;

  String userName = "userName";
  String password = "password";
  String identifier = "identifier";
  String name = "name";
  String url = "https://xwz.com/";
  ConnectorDTO connectorRequest;
  ConnectorResponseDTO connectorResponse;
  ELKConnector elkConfig;
  String accountIdentifier = "accountIdentifier";
  String secretIdentifier = "secretIdentifier";
  @Rule public ExpectedException expectedEx = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    elkConfig = ELKConnector.builder()
                    .username(userName)
                    .url(url)
                    .passwordRef(password)
                    .authType(ELKAuthType.USERNAME_PASSWORD)
                    .build();

    elkConfig.setType(ConnectorType.ELASTICSEARCH);
    elkConfig.setIdentifier(identifier);
    elkConfig.setAccountIdentifier(accountIdentifier);
    when(gitSyncSdkService.isGitSyncEnabled(accountIdentifier, null, null)).thenReturn(true);
    doNothing().when(connectorService).assurePredefined(any(), any());
  }

  private ConnectorResponseDTO createConnector(ELKAuthType elkAuthType) {
    SecretRefData secretRefData = SecretRefData.builder().identifier(secretIdentifier).scope(Scope.ACCOUNT).build();
    ELKConnectorDTO elkConnectorDTO = null;
    if (elkAuthType == ELKAuthType.USERNAME_PASSWORD) {
      elkConnectorDTO = ELKConnectorDTO.builder()
                            .url(url)
                            .username(userName)
                            .passwordRef(secretRefData)
                            .authType(ELKAuthType.USERNAME_PASSWORD)
                            .build();
    } else if (elkAuthType == ELKAuthType.BEARER_TOKEN) {
      elkConnectorDTO = ELKConnectorDTO.builder()
                            .url(url)
                            .apiKeyRef(SecretRefData.builder()
                                           .identifier(secretIdentifier + 1)
                                           .decryptedValue("Harness@246".toCharArray())
                                           .build())
                            .authType(ELKAuthType.BEARER_TOKEN)
                            .build();
    }
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name(name)
                                         .identifier(identifier)
                                         .connectorType(ConnectorType.ELASTICSEARCH)
                                         .connectorConfig(elkConnectorDTO)
                                         .build();
    connectorRequest = ConnectorDTO.builder().connectorInfo(connectorInfo).build();
    connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfo).build();
    when(connectorRepository.save(elkConfig, connectorRequest, ChangeType.ADD, null)).thenReturn(elkConfig);
    when(connectorMapper.toConnector(connectorRequest, accountIdentifier)).thenReturn(elkConfig);
    when(connectorMapper.writeDTO(elkConfig)).thenReturn(connectorResponse);
    return connectorService.create(connectorRequest, accountIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.ARPITJ)
  @Category(UnitTests.class)
  public void testCreateELKConnector() {
    ConnectorResponseDTO connectorDTOOutput = createConnector(ELKAuthType.USERNAME_PASSWORD);
    ensureELKConnectorFieldsAreCorrect(connectorDTOOutput, ELKAuthType.USERNAME_PASSWORD);
  }

  @Test
  @Owner(developers = OwnerRule.ANSUMAN)
  @Category(UnitTests.class)
  public void testCreateELKConnectorWithServiceToken() {
    ConnectorResponseDTO connectorDTOOutput = createConnector(ELKAuthType.BEARER_TOKEN);
    ensureELKConnectorFieldsAreCorrect(connectorDTOOutput, ELKAuthType.BEARER_TOKEN);
  }

  @Test
  @Owner(developers = OwnerRule.ARPITJ)
  @Category(UnitTests.class)
  public void testGetELKConnector() {
    createConnector(ELKAuthType.USERNAME_PASSWORD);
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
             anyString(), any(), any(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(elkConfig));
    ConnectorResponseDTO connectorDTO = connectorService.get(accountIdentifier, null, null, identifier).get();
    ensureELKConnectorFieldsAreCorrect(connectorDTO, ELKAuthType.USERNAME_PASSWORD);
  }

  private void ensureELKConnectorFieldsAreCorrect(ConnectorResponseDTO connectorResponse, ELKAuthType elkAuthType) {
    ConnectorInfoDTO connector = connectorResponse.getConnector();
    assertThat(connector).isNotNull();
    assertThat(connector.getName()).isEqualTo(name);
    assertThat(connector.getIdentifier()).isEqualTo(identifier);
    assertThat(connector.getConnectorType()).isEqualTo(ELASTICSEARCH);
    ELKConnectorDTO elkConnectorDTO = (ELKConnectorDTO) connector.getConnectorConfig();
    assertThat(elkConnectorDTO).isNotNull();
    assertThat(elkConnectorDTO.getUrl()).isEqualTo(url);
    if (elkAuthType == ELKAuthType.USERNAME_PASSWORD) {
      assertThat(elkConnectorDTO.getAuthType().name()).isEqualTo(ELKAuthType.USERNAME_PASSWORD.name());
      assertThat(elkConnectorDTO.getUsername()).isEqualTo(userName);
      assertThat(elkConnectorDTO.getPasswordRef()).isNotNull();
      assertThat(elkConnectorDTO.getPasswordRef().getIdentifier()).isEqualTo(secretIdentifier);
      assertThat(elkConnectorDTO.getPasswordRef().getScope()).isEqualTo(Scope.ACCOUNT);
    }
    if (elkAuthType == ELKAuthType.BEARER_TOKEN) {
      assertThat(elkConnectorDTO.getAuthType().name()).isEqualTo(ELKAuthType.BEARER_TOKEN.name());
      assertThat(elkConnectorDTO.getApiKeyRef().getIdentifier()).isEqualTo(secretIdentifier + 1);
    }
  }
}
