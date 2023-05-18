/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.delegate.beans.connector.ConnectorType.DYNATRACE;
import static io.harness.rule.OwnerRule.ANJAN;

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
import io.harness.connector.entities.embedded.dynatraceconnector.DynatraceConnector;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.connector.validator.ConnectionValidator;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;

import java.util.Map;
import java.util.Optional;
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
@OwnedBy(HarnessTeam.CV)
public class DynatraceConnectorTest extends CategoryTest {
  @Mock ConnectorMapper connectorMapper;
  @Mock ConnectorRepository connectorRepository;
  @Mock ConnectorEntityReferenceHelper connectorEntityReferenceHelper;
  @Mock private Map<String, ConnectionValidator> connectionValidatorMap;

  @InjectMocks @Spy DefaultConnectorServiceImpl connectorService;
  @Mock GitSyncSdkService gitSyncSdkService;

  String url = "https://dynatraceURL.com/";
  String apiToken = "1234_api_token";
  String identifier = "dynatraceIdentifier";
  String projectIdentifier = "projectIdentifier";
  String orgIdentifier = "orgIdentifier";
  String name = "DynatraceConnector";
  ConnectorDTO connectorDTO;
  ConnectorResponseDTO connectorResponseDTO;
  DynatraceConnector dynatraceConnector;
  String accountIdentifier = "accountIdentifier";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    dynatraceConnector = DynatraceConnector.builder().url(url).apiTokenRef(apiToken).build();
    dynatraceConnector.setType(DYNATRACE);
    dynatraceConnector.setIdentifier(identifier);
    dynatraceConnector.setName(name);

    DynatraceConnectorDTO dynatraceConnectorDTO =
        DynatraceConnectorDTO.builder().apiTokenRef(SecretRefHelper.createSecretRef(apiToken)).url(url).build();

    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name(name)
                                         .identifier(identifier)
                                         .connectorType(DYNATRACE)
                                         .connectorConfig(dynatraceConnectorDTO)
                                         .build();
    connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfo).build();
    connectorResponseDTO = ConnectorResponseDTO.builder().connector(connectorInfo).build();
    when(connectorRepository.save(dynatraceConnector, connectorDTO, ChangeType.ADD, null))
        .thenReturn(dynatraceConnector);
    when(connectorMapper.writeDTO(dynatraceConnector)).thenReturn(connectorResponseDTO);
    when(connectorMapper.toConnector(connectorDTO, accountIdentifier)).thenReturn(dynatraceConnector);
    when(gitSyncSdkService.isGitSyncEnabled(accountIdentifier, null, null)).thenReturn(true);
    doNothing().when(connectorService).assurePredefined(any(), any());
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testCreateDynatraceConnector() {
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
             anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(dynatraceConnector));
    ConnectorResponseDTO connectorResponseDTO = connectorService.create(connectorDTO, accountIdentifier);
    ensureDynatraceConnectorFieldsAreCorrect(connectorResponseDTO);
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetDynatraceConnector() {
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
             anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(dynatraceConnector));
    ConnectorResponseDTO connectorDTO =
        connectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier).get();
    ensureDynatraceConnectorFieldsAreCorrect(connectorDTO);
  }

  private void ensureDynatraceConnectorFieldsAreCorrect(ConnectorResponseDTO connectorResponse) {
    ConnectorInfoDTO connector = connectorResponse.getConnector();
    assertThat(connector).isNotNull();
    assertThat(connector.getName()).isEqualTo(name);
    assertThat(connector.getIdentifier()).isEqualTo(identifier);
    assertThat(connector.getConnectorType()).isEqualTo(DYNATRACE);
    DynatraceConnectorDTO dynatraceConnectorDTO = (DynatraceConnectorDTO) connector.getConnectorConfig();
    assertThat(dynatraceConnectorDTO).isNotNull();
    assertThat(dynatraceConnectorDTO.getUrl()).isEqualTo(url);
    assertThat(dynatraceConnectorDTO.getApiTokenRef().toSecretRefStringValue()).isEqualTo(apiToken);
  }
}
