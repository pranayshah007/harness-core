/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorsTestBase;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.ng.beans.PageRequest;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.springframework.data.domain.Page;

@OwnedBy(CDP)
@Slf4j
public class NGHostServiceImplTest extends ConnectorsTestBase {
  private static final String REGION = "region";
  private static final String WEST = "west";
  private static final String HOST_TYPE = "hostType";
  private static final String EAST = "east";
  private static final String DB = "DB";
  private static final String VM = "VM";
  private static final String accountIdentifier = "accountIdentifier";
  private static final String identifier = "identifier";
  private static final String scopedIdentifier = "account.identifier";
  private static final String name = "name";
  @Inject @InjectMocks private NGHostServiceImpl hostService;
  @Inject @InjectMocks private DefaultConnectorServiceImpl connectorService;

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category({UnitTests.class})
  public void testGetPdcConnectorHostsNoFilter() {
    createConnectorWithHosts();
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(2).build();
    Page<HostDTO> pageResponse =
        hostService.filterHostsByConnector(accountIdentifier, null, null, scopedIdentifier, null, pageRequest);

    assertThat(pageResponse.getTotalElements()).isEqualTo(8);
    assertThat(pageResponse.getTotalPages()).isEqualTo(4);
    assertThat(pageResponse.get().count()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category({UnitTests.class})
  public void testGetPdcConnectorHostsFilterByHostNameCommaSeparated() {
    createConnectorWithHosts();
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(2).build();
    HostFilterDTO filter = new HostFilterDTO();
    filter.setType(HostFilterType.HOST_NAMES);
    filter.setFilter("host1, host2,host3\nhost4\nhost5");
    Page<HostDTO> pageResponse =
        hostService.filterHostsByConnector(accountIdentifier, null, null, scopedIdentifier, filter, pageRequest);

    assertThat(pageResponse.getTotalElements()).isEqualTo(5);
    assertThat(pageResponse.getTotalPages()).isEqualTo(3);
    assertThat(pageResponse.get().count()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category({UnitTests.class})
  public void testGetPdcConnectorHostsFilterByHostAttributesCommaSeparated() {
    createConnectorWithHosts();
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(2).build();
    HostFilterDTO filter = new HostFilterDTO();
    filter.setType(HostFilterType.HOST_ATTRIBUTES);
    filter.setFilter("region:west, hostType:DB\n hostType:VM");
    Page<HostDTO> pageResponse =
        hostService.filterHostsByConnector(accountIdentifier, null, null, scopedIdentifier, filter, pageRequest);

    assertThat(pageResponse.getTotalElements()).isEqualTo(8);
    assertThat(pageResponse.getTotalPages()).isEqualTo(4);
    assertThat(pageResponse.get().count()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category({UnitTests.class})
  public void testGetPdcConnectorHostsFilterByHostAttributesNewLineSeparated() {
    createConnectorWithHosts();
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(2).build();
    HostFilterDTO filter = new HostFilterDTO();
    filter.setType(HostFilterType.HOST_ATTRIBUTES);
    filter.setFilter("region:west\nhostType:DB");
    Page<HostDTO> pageResponse =
        hostService.filterHostsByConnector(accountIdentifier, null, null, scopedIdentifier, filter, pageRequest);

    assertThat(pageResponse.getTotalElements()).isEqualTo(6);
    assertThat(pageResponse.getTotalPages()).isEqualTo(3);
    assertThat(pageResponse.get().count()).isEqualTo(2);
  }

  private void createConnectorWithHosts() {
    ConnectorDTO connectorRequestDTO = createPdcConnectorRequestDTO();
    connectorService.create(connectorRequestDTO, accountIdentifier);
  }

  private ConnectorDTO createPdcConnectorRequestDTO() {
    PhysicalDataCenterConnectorDTO connectorDTO = PhysicalDataCenterConnectorDTO.builder().hosts(createHosts()).build();
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name(name)
                                         .identifier(identifier)
                                         .connectorType(ConnectorType.PDC)
                                         .connectorConfig(connectorDTO)
                                         .build();
    return ConnectorDTO.builder().connectorInfo(connectorInfo).build();
  }

  @NotNull
  private List<HostDTO> createHosts() {
    Map<String, String> attrs1 = newHashMap(REGION, WEST, HOST_TYPE, DB);
    Map<String, String> attrs2 = newHashMap(REGION, EAST, HOST_TYPE, DB);
    Map<String, String> attrs3 = newHashMap(REGION, WEST, HOST_TYPE, VM);
    Map<String, String> attrs4 = newHashMap(REGION, EAST, HOST_TYPE, VM);

    HostDTO host1 = new HostDTO("host1", attrs1);
    HostDTO host2 = new HostDTO("host2", attrs1);
    HostDTO host3 = new HostDTO("host3", attrs2);
    HostDTO host4 = new HostDTO("host4", attrs2);
    HostDTO host5 = new HostDTO("host5", attrs3);
    HostDTO host6 = new HostDTO("host6", attrs3);
    HostDTO host7 = new HostDTO("host7", attrs4);
    HostDTO host8 = new HostDTO("host8", attrs4);

    return asList(host1, host2, host3, host4, host5, host6, host7, host8);
  }

  private Map<String, String> newHashMap(String... parameters) {
    Map<String, String> result = Maps.newHashMap();

    for (int i = 0; i < parameters.length; i += 2) {
      result.put(parameters[i], parameters[i + 1]);
    }

    return result;
  }
}
