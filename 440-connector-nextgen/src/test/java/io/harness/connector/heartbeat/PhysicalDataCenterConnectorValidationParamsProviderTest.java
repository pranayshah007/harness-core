/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.heartbeat;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorTestConstants.ACCOUNT_IDENTIFIER;
import static io.harness.connector.ConnectorTestConstants.CONNECTOR_NAME;
import static io.harness.connector.ConnectorTestConstants.HOST_NAME;
import static io.harness.connector.ConnectorTestConstants.HOST_WITH_PORT;
import static io.harness.connector.ConnectorTestConstants.ORG_IDENTIFIER;
import static io.harness.connector.ConnectorTestConstants.PROJECT_IDENTIFIER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.mixin.SocketConnectivityCapabilityGenerator;
import io.harness.delegate.task.utils.PhysicalDataCenterUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class PhysicalDataCenterConnectorValidationParamsProviderTest extends CategoryTest {
  public static final HashSet<String> DELEGATE_SELECTORS = Sets.newHashSet("delegateGroup1, delegateGroup2");

  @InjectMocks private PhysicalDataCenterConnectorValidationParamsProvider connectorValidationParamsProvider;

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetConnectorValidationParams() {
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.PDC)
                                            .name(CONNECTOR_NAME)
                                            .connectorConfig(getPhysicalDataCenterConnectorDTO())
                                            .build();

    ConnectorValidationParams connectorValidationParams =
        connectorValidationParamsProvider.getConnectorValidationParams(
            connectorInfoDTO, CONNECTOR_NAME, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(connectorValidationParams).isNotNull();
    assertThat(connectorValidationParams.getConnectorName()).isEqualTo(CONNECTOR_NAME);
    assertThat(connectorValidationParams.getConnectorType()).isEqualTo(ConnectorType.PDC);

    List<ExecutionCapability> executionCapabilityList =
        connectorValidationParams.fetchRequiredExecutionCapabilities(null);

    assertThat(executionCapabilityList).isNotNull();
    assertThat(executionCapabilityList)
        .contains(SocketConnectivityCapabilityGenerator.buildSocketConnectivityCapability(
            HOST_NAME, PhysicalDataCenterUtils.getPortOrSSHDefault(HOST_WITH_PORT)));
    assertThat(executionCapabilityList)
        .contains(SelectorCapability.builder().selectors(DELEGATE_SELECTORS).selectorOrigin("connector").build());
  }

  private PhysicalDataCenterConnectorDTO getPhysicalDataCenterConnectorDTO() {
    HostDTO hostDTO = new HostDTO();
    hostDTO.setHostName(HOST_WITH_PORT);
    return PhysicalDataCenterConnectorDTO.builder()
        .hosts(Collections.singletonList(hostDTO))
        .delegateSelectors(DELEGATE_SELECTORS)
        .build();
  }
}
