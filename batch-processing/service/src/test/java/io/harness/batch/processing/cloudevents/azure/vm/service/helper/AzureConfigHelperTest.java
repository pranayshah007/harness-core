/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.azure.vm.service.helper;

import static io.harness.rule.OwnerRule.ANMOL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.rule.Owner;

import software.wings.beans.AzureAccountAttributes;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AzureConfigHelperTest extends CategoryTest {
  @Mock private NGConnectorHelper mockNgConnectorHelper;
  @InjectMocks private AzureConfigHelper azureConfigHelperUnderTest;
  private Map<String, AzureAccountAttributes> expectedResult;

  private final String CONNECTOR_NAME = "connectorName";
  private final String CONNECTOR_ID = "connectorId";
  private final String TENANT_ID = "tenantId";
  private final String SUBSCRIPTION_ID = "subscriptionId";
  private final String ACCOUNT_ID = "accountId";
  private final String TENANT_ID_SUBSCRIPTION_ID = "tenantId-subscriptionId";

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    expectedResult = Map.ofEntries(Map.entry(TENANT_ID_SUBSCRIPTION_ID,
        AzureAccountAttributes.builder()
            .connectorName(CONNECTOR_NAME)
            .connectorId(CONNECTOR_ID)
            .tenantId(TENANT_ID)
            .subscriptionId(SUBSCRIPTION_ID)
            .build()));
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetAzureAccountAttributes() throws Exception {
    when(mockNgConnectorHelper.getNextGenConnectors(ACCOUNT_ID, Arrays.asList(ConnectorType.CE_AZURE),
             Arrays.asList(CEFeatures.VISIBILITY, CEFeatures.BILLING),
             Arrays.asList(ConnectivityStatus.SUCCESS, ConnectivityStatus.FAILURE, ConnectivityStatus.PARTIAL,
                 ConnectivityStatus.UNKNOWN)))
        .thenReturn(getConnectorMockedResponse(Arrays.asList(CEFeatures.VISIBILITY, CEFeatures.BILLING)));
    final Map<String, AzureAccountAttributes> result = azureConfigHelperUnderTest.getAzureAccountAttributes(ACCOUNT_ID);
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetAzureAccountAttributes_WithJustVisibilityEnabled() throws Exception {
    when(mockNgConnectorHelper.getNextGenConnectors(ACCOUNT_ID, Arrays.asList(ConnectorType.CE_AZURE),
             Arrays.asList(CEFeatures.VISIBILITY, CEFeatures.BILLING),
             Arrays.asList(ConnectivityStatus.SUCCESS, ConnectivityStatus.FAILURE, ConnectivityStatus.PARTIAL,
                 ConnectivityStatus.UNKNOWN)))
        .thenReturn(getConnectorMockedResponse(Arrays.asList(CEFeatures.VISIBILITY)));
    final Map<String, AzureAccountAttributes> result = azureConfigHelperUnderTest.getAzureAccountAttributes(ACCOUNT_ID);
    assertThat(result).isEqualTo(Collections.emptyMap());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetAzureAccountAttributes_WithJustBillingEnabled() throws Exception {
    when(mockNgConnectorHelper.getNextGenConnectors(ACCOUNT_ID, Arrays.asList(ConnectorType.CE_AZURE),
             Arrays.asList(CEFeatures.VISIBILITY, CEFeatures.BILLING),
             Arrays.asList(ConnectivityStatus.SUCCESS, ConnectivityStatus.FAILURE, ConnectivityStatus.PARTIAL,
                 ConnectivityStatus.UNKNOWN)))
        .thenReturn(getConnectorMockedResponse(Arrays.asList(CEFeatures.BILLING)));
    final Map<String, AzureAccountAttributes> result = azureConfigHelperUnderTest.getAzureAccountAttributes(ACCOUNT_ID);
    assertThat(result).isEqualTo(Collections.emptyMap());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetAzureAccountAttributes_WithNoFeatureEnabled() throws Exception {
    when(mockNgConnectorHelper.getNextGenConnectors(ACCOUNT_ID, Arrays.asList(ConnectorType.CE_AZURE),
             Arrays.asList(CEFeatures.VISIBILITY, CEFeatures.BILLING),
             Arrays.asList(ConnectivityStatus.SUCCESS, ConnectivityStatus.FAILURE, ConnectivityStatus.PARTIAL,
                 ConnectivityStatus.UNKNOWN)))
        .thenReturn(getConnectorMockedResponse(null));
    final Map<String, AzureAccountAttributes> result = azureConfigHelperUnderTest.getAzureAccountAttributes(ACCOUNT_ID);
    assertThat(result).isEqualTo(Collections.emptyMap());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetAzureAccountAttributes_NGConnectorHelperReturnsNoItems() {
    when(mockNgConnectorHelper.getNextGenConnectors(ACCOUNT_ID, List.of(ConnectorType.KUBERNETES_CLUSTER),
             List.of(CEFeatures.BILLING), List.of(ConnectivityStatus.SUCCESS)))
        .thenReturn(Collections.emptyList());
    final Map<String, AzureAccountAttributes> result = azureConfigHelperUnderTest.getAzureAccountAttributes(ACCOUNT_ID);
    assertThat(result).isEqualTo(Collections.emptyMap());
  }

  private List<ConnectorResponseDTO> getConnectorMockedResponse(List<CEFeatures> featuresEnabled) {
    return List.of(ConnectorResponseDTO.builder()
                       .connector(ConnectorInfoDTO.builder()
                                      .name(CONNECTOR_NAME)
                                      .identifier(CONNECTOR_ID)
                                      .connectorConfig(CEAzureConnectorDTO.builder()
                                                           .featuresEnabled(featuresEnabled)
                                                           .tenantId(TENANT_ID)
                                                           .subscriptionId(SUBSCRIPTION_ID)
                                                           .build())
                                      .build())
                       .build());
  }
}