/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.pdcconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorTestConstants.HOST_NAME_1;
import static io.harness.connector.ConnectorTestConstants.HOST_NAME_2;
import static io.harness.connector.ConnectorTestConstants.SSK_KEY_REF_IDENTIFIER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.pdcconnector.Host;
import io.harness.connector.entities.embedded.pdcconnector.PhysicalDataCenterConnector;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class PhysicalDataCenterEntityToDTOTest extends CategoryTest {
  @InjectMocks private PhysicalDataCenterEntityToDTO physicalDataCenterEntityToDTO;

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testCreateConnectorDTO() {
    PhysicalDataCenterConnectorDTO connectorDTO = physicalDataCenterEntityToDTO.createConnectorDTO(
        PhysicalDataCenterConnector.builder().hosts(getHosts()).sshKeyRef(SSK_KEY_REF_IDENTIFIER).build());

    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getHosts().size()).isEqualTo(2);

    HostDTO hostDTO1 = getHostDTO(HOST_NAME_1);
    HostDTO hostDTO2 = getHostDTO(HOST_NAME_2);
    assertThat(connectorDTO.getHosts()).contains(hostDTO1, hostDTO2);
  }

  private List<Host> getHosts() {
    return Arrays.asList(getHost(HOST_NAME_1), getHost(HOST_NAME_2));
  }

  private Host getHost(String hostName) {
    return Host.builder().hostName(hostName).hostAttributes(getHostAttributes()).build();
  }

  private HostDTO getHostDTO(String hostName) {
    HostDTO hostDTO = new HostDTO();
    hostDTO.setHostName(hostName);
    hostDTO.setHostAttributes(getHostAttributes());

    return hostDTO;
  }

  private Map<String, String> getHostAttributes() {
    Map<String, String> attr1 = Maps.newHashMap();
    attr1.put("region", "west");
    attr1.put("hostType", "DB");
    return attr1;
  }
}
