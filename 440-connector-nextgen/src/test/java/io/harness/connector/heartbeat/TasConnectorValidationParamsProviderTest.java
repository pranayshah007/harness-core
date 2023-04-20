/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.connector.heartbeat;

import static io.harness.connector.ConnectorTestConstants.ACCOUNT_IDENTIFIER;
import static io.harness.connector.ConnectorTestConstants.CONNECTOR_NAME;
import static io.harness.connector.ConnectorTestConstants.ORG_IDENTIFIER;
import static io.harness.connector.ConnectorTestConstants.PROJECT_IDENTIFIER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialType;
import io.harness.delegate.beans.connector.tasconnector.TasManualDetailsDTO;
import io.harness.delegate.beans.connector.tasconnector.TasValidationParams;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class TasConnectorValidationParamsProviderTest extends CategoryTest {
  public static final HashSet<String> DELEGATE_SELECTORS = Sets.newHashSet("delegateGroup1, delegateGroup2");
  private static final String URL = "endpoint_url";

  @InjectMocks private TasConnectorValidationParamsProvider tasConnectorValidationParamsProvider;
  @Mock private EncryptionHelper encryptionHelper;

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testGetConnectorValidationParams() {
    ConnectorInfoDTO connectorInfoDTO = getConnectorInfoDTO();
    ConnectorValidationParams connectorValidationParams =
        tasConnectorValidationParamsProvider.getConnectorValidationParams(
            connectorInfoDTO, CONNECTOR_NAME, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(connectorValidationParams.getConnectorName()).isEqualTo(CONNECTOR_NAME);
    assertThat(connectorValidationParams.getConnectorType()).isEqualTo(ConnectorType.TAS);
    assertThat(connectorValidationParams).isInstanceOf(TasValidationParams.class);
    List<ExecutionCapability> executionCapabilityList =
        connectorValidationParams.fetchRequiredExecutionCapabilities(null);

    assertThat(executionCapabilityList).isNotNull();
    assertThat(executionCapabilityList)
        .contains(SelectorCapability.builder().selectors(DELEGATE_SELECTORS).selectorOrigin("connector").build());
  }

  private ConnectorInfoDTO getConnectorInfoDTO() {
    return ConnectorInfoDTO.builder()
        .connectorConfig(getConnectorConfigDTO())
        .connectorType(ConnectorType.TAS)
        .name(CONNECTOR_NAME)
        .build();
  }

  private TasConnectorDTO getConnectorConfigDTO() {
    return TasConnectorDTO.builder()
        .credential(TasCredentialDTO.builder()
                        .type(TasCredentialType.MANUAL_CREDENTIALS)
                        .spec(TasManualDetailsDTO.builder().endpointUrl(URL).build())
                        .build())

        .delegateSelectors(DELEGATE_SELECTORS)
        .build();
  }
}
