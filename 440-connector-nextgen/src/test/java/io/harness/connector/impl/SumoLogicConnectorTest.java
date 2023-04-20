/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.delegate.beans.connector.ConnectorType.SUMOLOGIC;
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
import io.harness.connector.entities.embedded.sumologic.SumoLogicConnector;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.connector.mappers.sumologicmapper.SumoLogicDTOToEntity;
import io.harness.connector.validator.ConnectionValidator;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
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
public class SumoLogicConnectorTest extends CategoryTest {
  String url = "https://sumologic.com/";
  String identifier = "sumoLogicIdentifier";
  String name = "SumoLogic";
  String accountIdentifier = "accountIdentifier";
  String projectIdentifier = "projectIdentifier";
  String orgIdentifier = "orgIdentifier";
  String accessIdRef = "accessIdRef";
  String accessKeyRef = "accessKeyRef";

  @Mock ConnectorMapper connectorMapper;
  @Mock ConnectorRepository connectorRepository;
  @Mock ConnectorEntityReferenceHelper connectorEntityReferenceHelper;
  @Mock Map<String, ConnectionValidator> connectionValidatorMap;
  @InjectMocks @Spy DefaultConnectorServiceImpl connectorService;
  @Mock GitSyncSdkService gitSyncSdkService;

  SumoLogicConnector sumoLogicConnector;
  SumoLogicConnectorDTO sumoLogicConnectorDTO;
  ConnectorDTO connectorDTO;
  ConnectorResponseDTO connectorResponseDTO;

  @Before
  public void setUp() throws Exception {
    connectorRepository = mock(ConnectorRepository.class);
    connectorMapper = mock(ConnectorMapper.class);
    MockitoAnnotations.initMocks(this);
    create();
    when(connectorRepository.save(sumoLogicConnector, connectorDTO, ChangeType.ADD, null))
        .thenReturn(sumoLogicConnector);
    when(connectorMapper.writeDTO(sumoLogicConnector)).thenReturn(connectorResponseDTO);
    when(connectorMapper.toConnector(connectorDTO, accountIdentifier)).thenReturn(sumoLogicConnector);
    doNothing().when(connectorService).assurePredefined(any(), any());
    when(gitSyncSdkService.isGitSyncEnabled(accountIdentifier, null, null)).thenReturn(true);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testCreateSumoLogicConnector() {
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
             anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(sumoLogicConnector));
    ConnectorResponseDTO connectorResponseDTOFromCreate = connectorService.create(connectorDTO, accountIdentifier);
    assertThat(connectorResponseDTO).isEqualTo(connectorResponseDTOFromCreate);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetSumoLogicConnector() {
    connectorService.create(connectorDTO, accountIdentifier);
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
             anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(sumoLogicConnector));
    ConnectorResponseDTO connectorResponseDTOFromGet =
        connectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier).get();
    assertThat(connectorResponseDTO).isEqualTo(connectorResponseDTOFromGet);
  }

  void create() {
    sumoLogicConnectorDTO = SumoLogicConnectorDTO.builder()
                                .url(url)
                                .accessIdRef(SecretRefHelper.createSecretRef(accessIdRef))
                                .accessKeyRef(SecretRefHelper.createSecretRef(accessKeyRef))
                                .build();

    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name(name)
                                         .identifier(identifier)
                                         .connectorType(SUMOLOGIC)
                                         .connectorConfig(sumoLogicConnectorDTO)
                                         .build();

    sumoLogicConnector = new SumoLogicDTOToEntity().toConnectorEntity(sumoLogicConnectorDTO);
    connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfo).build();
    connectorResponseDTO = ConnectorResponseDTO.builder().connector(connectorInfo).build();
  }
}
