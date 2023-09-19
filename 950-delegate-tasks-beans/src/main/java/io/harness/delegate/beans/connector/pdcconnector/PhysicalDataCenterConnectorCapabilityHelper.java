/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.pdcconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER_SRE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.SocketConnectivityCapabilityGenerator;
import io.harness.delegate.task.utils.PhysicalDataCenterUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.expression.ExpressionEvaluator;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class PhysicalDataCenterConnectorCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      PhysicalDataCenterConnectorDTO physicalDataCenterConnectorDTO, ExpressionEvaluator evaluator,
      String defaultPort) {
    List<String> hostNames =
        physicalDataCenterConnectorDTO.getHosts().stream().map(HostDTO::getHostName).collect(Collectors.toList());
    List<ExecutionCapability> capabilityList =
        hostNames.stream()
            .map(host -> {
              String hostName = PhysicalDataCenterUtils.extractHostnameFromHost(host).orElseThrow(
                  ()
                      -> new InvalidArgumentsException(
                          format("Not found hostName for host capability check, host: %s", host), USER_SRE));
              return SocketConnectivityCapabilityGenerator.buildSocketConnectivityCapability(
                  hostName, getPortFromHost(host, defaultPort));
            })
            .collect(Collectors.toList());

    populateDelegateSelectorCapability(capabilityList, physicalDataCenterConnectorDTO.getDelegateSelectors(), physicalDataCenterConnectorDTO.getConnectorType());
    return capabilityList;
  }

  private String getPortFromHost(String host, String defaultPort) {
    return PhysicalDataCenterUtils.extractPortFromHost(host).isPresent()
        ? String.valueOf(PhysicalDataCenterUtils.extractPortFromHost(host).get())
        : defaultPort;
  }
}
