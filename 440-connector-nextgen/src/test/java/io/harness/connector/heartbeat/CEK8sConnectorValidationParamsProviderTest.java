/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.heartbeat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.cek8s.CEKubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CEK8sConnectorValidationParamsProviderTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String CONNECTOR_NAME = "connectorName";
  private static final String KUBERNETES_CONNECTOR_IDENTIFIER = "kubernetesConnectorIdentifier";
  private static final String SCOPED_KUBERNETES_CONNECTOR_IDENTIFIER = "account." + KUBERNETES_CONNECTOR_IDENTIFIER;

  @Mock private ConnectorService connectorService;
  @Mock private EncryptionHelper encryptionHelper;
  @InjectMocks private CEK8sConnectorValidationParamsProvider cek8sConnectorValidationParamsProvider;

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testAccountScopedConnectorIdentifier() throws Exception {
    when(encryptionHelper.getEncryptionDetail(eq(null), eq(ACCOUNT_ID), eq(null), eq(null)))
        .thenReturn(Collections.emptyList());

    final ConnectorResponseDTO connectorResponseDTO = createCloudProviderK8sConnector();
    when(connectorService.get(eq(ACCOUNT_ID), eq(null), eq(null), eq(KUBERNETES_CONNECTOR_IDENTIFIER)))
        .thenReturn(Optional.of(connectorResponseDTO));

    final ConnectorInfoDTO connectorInfoDTO =
        createCCMK8sConnectorForReferencedConnector(SCOPED_KUBERNETES_CONNECTOR_IDENTIFIER);
    ConnectorValidationParams connectorValidationParams =
        cek8sConnectorValidationParamsProvider.getConnectorValidationParams(
            connectorInfoDTO, CONNECTOR_NAME, ACCOUNT_ID, null, null);

    assertThat(connectorValidationParams).isNotNull();
    assertThat(connectorValidationParams.getConnectorType()).isEqualTo(ConnectorType.CE_KUBERNETES_CLUSTER);
    assertThat(connectorValidationParams.getConnectorName()).isEqualTo(CONNECTOR_NAME);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testNotExplicitlyScopedConnectorIdentifier() throws Exception {
    when(encryptionHelper.getEncryptionDetail(eq(null), eq(ACCOUNT_ID), eq(null), eq(null)))
        .thenReturn(Collections.emptyList());

    final ConnectorResponseDTO connectorResponseDTO = createCloudProviderK8sConnector();
    when(connectorService.get(eq(ACCOUNT_ID), eq(null), eq(null), eq(KUBERNETES_CONNECTOR_IDENTIFIER)))
        .thenReturn(Optional.of(connectorResponseDTO));

    final ConnectorInfoDTO connectorInfoDTO =
        createCCMK8sConnectorForReferencedConnector(KUBERNETES_CONNECTOR_IDENTIFIER);
    ConnectorValidationParams connectorValidationParams =
        cek8sConnectorValidationParamsProvider.getConnectorValidationParams(
            connectorInfoDTO, CONNECTOR_NAME, ACCOUNT_ID, null, null);

    assertThat(connectorValidationParams).isNotNull();
    assertThat(connectorValidationParams.getConnectorType()).isEqualTo(ConnectorType.CE_KUBERNETES_CLUSTER);
    assertThat(connectorValidationParams.getConnectorName()).isEqualTo(CONNECTOR_NAME);
  }

  private static ConnectorResponseDTO createCloudProviderK8sConnector() {
    final KubernetesClusterConfigDTO kubernetesClusterConfigDTO =
        KubernetesClusterConfigDTO.builder()
            .delegateSelectors(ImmutableSet.of("ce-dev"))
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(KubernetesCredentialType.INHERIT_FROM_DELEGATE)
                            .build())
            .build();

    final ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder()
            .connectorType(ConnectorType.KUBERNETES_CLUSTER)
            .identifier(CEK8sConnectorValidationParamsProviderTest.KUBERNETES_CONNECTOR_IDENTIFIER)
            .connectorConfig(kubernetesClusterConfigDTO)
            .build();

    return ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
  }

  private static ConnectorInfoDTO createCCMK8sConnectorForReferencedConnector(String connectorRef) {
    final CEKubernetesClusterConfigDTO ceKubernetesClusterConfigDTO =
        CEKubernetesClusterConfigDTO.builder()
            .connectorRef(connectorRef)
            .featuresEnabled(Collections.singletonList(CEFeatures.VISIBILITY))
            .build();

    return ConnectorInfoDTO.builder()
        .connectorType(ConnectorType.CE_KUBERNETES_CLUSTER)
        .connectorConfig(ceKubernetesClusterConfigDTO)
        .identifier("randomString_jhdfv")
        .build();
  }
}
