/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.delegate.beans.connector.ConnectorType.DATADOG;
import static io.harness.rule.OwnerRule.KANHAIYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.embedded.datadogconnector.DatadogConnector;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.connector.mappers.datadogmapper.DatadogDTOToEntity;
import io.harness.connector.validator.ConnectionValidator;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;

import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@Slf4j
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CV)
public class DatadogConnectorTest extends CategoryTest {
  String url = "https://datadoghq.com/";
  String identifier = "datadogIdentifier";
  String name = "Datadog";
  String accountIdentifier = "accountIdentifier";
  String projectIdentifier = "projectIdentifier";
  String orgIdentifier = "orgIdentifier";
  String apiKeyRef = "apiKeyRef";
  String applicationKeyRef = "appKeyRef";

  @Mock ConnectorMapper connectorMapper;
  @Mock ConnectorRepository connectorRepository;
  @Mock ConnectorEntityReferenceHelper connectorEntityReferenceHelper;
  @Mock Map<String, ConnectionValidator> connectionValidatorMap;
  @InjectMocks @Spy DefaultConnectorServiceImpl connectorService;
  @Mock GitSyncSdkService gitSyncSdkService;
  DatadogConnector datadogConnector;
  DatadogConnectorDTO datadogConnectorDTO;
  ConnectorDTO connectorDTO;
  ConnectorResponseDTO connectorResponseDTO;

  @Before
  public void setUp() throws Exception {
    connectorRepository = mock(ConnectorRepository.class);
    connectorMapper = mock(ConnectorMapper.class);
    MockitoAnnotations.initMocks(this);
    create();
    when(connectorRepository.save(datadogConnector, connectorDTO, ChangeType.ADD, null)).thenReturn(datadogConnector);
    when(connectorMapper.writeDTO(datadogConnector)).thenReturn(connectorResponseDTO);
    when(connectorMapper.toConnector(connectorDTO, accountIdentifier)).thenReturn(datadogConnector);
    when(gitSyncSdkService.isGitSyncEnabled(accountIdentifier, null, null)).thenReturn(true);
    doNothing().when(connectorService).assurePredefined(any(), any());
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testCreateDatadogConnector() {
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
             anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(datadogConnector));
    ConnectorResponseDTO connectorResponseDTOFromCreate = connectorService.create(connectorDTO, accountIdentifier);
    assertThat(connectorResponseDTO).isEqualTo(connectorResponseDTOFromCreate);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetDatadogConnector() {
    connectorService.create(connectorDTO, accountIdentifier);
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
             anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(datadogConnector));
    ConnectorResponseDTO connectorResponseDTOFromGet =
        connectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier).get();
    assertThat(connectorResponseDTO).isEqualTo(connectorResponseDTOFromGet);
  }

  void create() {
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name(name)
                                         .identifier(identifier)
                                         .connectorType(DATADOG)
                                         .connectorConfig(datadogConnectorDTO)
                                         .build();
    datadogConnectorDTO = DatadogConnectorDTO.builder()
                              .url(url)
                              .apiKeyRef(SecretRefHelper.createSecretRef(apiKeyRef))
                              .applicationKeyRef(SecretRefHelper.createSecretRef(applicationKeyRef))
                              .build();
    datadogConnector = new DatadogDTOToEntity().toConnectorEntity(datadogConnectorDTO);
    connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfo).build();
    connectorResponseDTO = ConnectorResponseDTO.builder().connector(connectorInfo).build();
  }
}
