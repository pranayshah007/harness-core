/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.delegate.beans.connector.ConnectorType.PROMETHEUS;
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
import io.harness.connector.entities.embedded.prometheusconnector.PrometheusConnector;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.connector.validator.ConnectionValidator;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;

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
import org.mockito.Spy;

@Slf4j
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@OwnedBy(HarnessTeam.CV)
public class PrometheusConnectorTest extends CategoryTest {
  @Mock ConnectorMapper connectorMapper;
  @Mock ConnectorRepository connectorRepository;
  @Mock ConnectorEntityReferenceHelper connectorEntityReferenceHelper;
  @Mock private Map<String, ConnectionValidator> connectionValidatorMap;
  @Mock GitSyncSdkService gitSyncSdkService;

  @InjectMocks @Spy DefaultConnectorServiceImpl connectorService;

  String url = "https://prometheus.com/";
  String identifier = "prometheusIdentifier";
  String name = "Prometheus";
  ConnectorDTO connectorDTO;
  ConnectorResponseDTO connectorResponseDTO;
  PrometheusConnector prometheusConnector;
  String accountIdentifier = "accountIdentifier";

  @Rule public ExpectedException expectedEx = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    prometheusConnector = PrometheusConnector.builder().url(url).build();
    prometheusConnector.setType(PROMETHEUS);
    prometheusConnector.setIdentifier(identifier);
    prometheusConnector.setName(name);

    PrometheusConnectorDTO prometheusConnectorDTO = PrometheusConnectorDTO.builder().url(url).build();

    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name(name)
                                         .identifier(identifier)
                                         .connectorType(PROMETHEUS)
                                         .connectorConfig(prometheusConnectorDTO)
                                         .build();
    connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfo).build();
    connectorResponseDTO = ConnectorResponseDTO.builder().connector(connectorInfo).build();
    when(connectorRepository.save(prometheusConnector, connectorDTO, ChangeType.ADD, null))
        .thenReturn(prometheusConnector);
    when(connectorMapper.writeDTO(prometheusConnector)).thenReturn(connectorResponseDTO);
    when(connectorMapper.toConnector(connectorDTO, accountIdentifier)).thenReturn(prometheusConnector);
    when(gitSyncSdkService.isGitSyncEnabled(accountIdentifier, null, null)).thenReturn(true);
    doNothing().when(connectorService).assurePredefined(any(), any());
  }

  private ConnectorResponseDTO createConnector() {
    return connectorService.create(connectorDTO, accountIdentifier);
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testCreateAppDynamicsConnector() {
    createConnector();
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
             anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(prometheusConnector));
    ConnectorResponseDTO connectorResponseDTO = connectorService.create(connectorDTO, accountIdentifier);
    ensurePrometheusConnectorFieldsAreCorrect(connectorResponseDTO);
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetAppDynamicsConnector() {
    createConnector();
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Optional.of(prometheusConnector));
    ConnectorResponseDTO connectorDTO = connectorService.get(accountIdentifier, null, null, identifier).get();
    ensurePrometheusConnectorFieldsAreCorrect(connectorDTO);
  }

  private void ensurePrometheusConnectorFieldsAreCorrect(ConnectorResponseDTO connectorResponse) {
    ConnectorInfoDTO connector = connectorResponse.getConnector();
    assertThat(connector).isNotNull();
    assertThat(connector.getName()).isEqualTo(name);
    assertThat(connector.getIdentifier()).isEqualTo(identifier);
    assertThat(connector.getConnectorType()).isEqualTo(PROMETHEUS);
    PrometheusConnectorDTO prometheusConnectorDTO = (PrometheusConnectorDTO) connector.getConnectorConfig();
    assertThat(prometheusConnectorDTO).isNotNull();
    assertThat(prometheusConnectorDTO.getUrl()).isEqualTo(url);
  }
}
