/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static io.harness.delegate.task.executioncapability.SocketConnectivityCapabilityCheck.connectableHost;
import static io.harness.delegate.task.utils.PhysicalDataCenterUtils.extractHostnameFromHost;

import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.capability.CapabilitySubjectPermission.CapabilitySubjectPermissionBuilder;
import io.harness.capability.CapabilitySubjectPermission.PermissionResult;
import io.harness.capability.SocketConnectivityBulkOrParameters;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SocketConnectivityBulkOrExecutionCapability;

import com.google.inject.Singleton;
import com.google.protobuf.ProtocolStringList;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SocketConnectivityBulkOrCapabilityCheck implements CapabilityCheck, ProtoCapabilityCheck {
  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    SocketConnectivityBulkOrExecutionCapability socketConnCapability =
        (SocketConnectivityBulkOrExecutionCapability) delegateCapability;
    try {
      boolean valid = false;

      if (null != socketConnCapability.getPort() && EmptyPredicate.isNotEmpty(socketConnCapability.getHostNames())) {
        for (String host : socketConnCapability.getHostNames()) {
          Optional<String> optionalHost = extractHostnameFromHost(host);
          if (optionalHost.isEmpty()) {
            log.error("Cannot extract hostname from the host {}", host);
            break;
          }
          valid = connectableHost(optionalHost.get(), socketConnCapability.getPort());
          if (valid) {
            break;
          }
        }
      }

      return CapabilityResponse.builder().delegateCapability(socketConnCapability).validated(valid).build();
    } catch (final Exception ex) {
      log.error("Error Occurred while checking socketConnCapability", ex);
      return CapabilityResponse.builder().delegateCapability(socketConnCapability).validated(false).build();
    }
  }

  @Override
  public CapabilitySubjectPermission performCapabilityCheckWithProto(CapabilityParameters parameters) {
    CapabilitySubjectPermissionBuilder builder = CapabilitySubjectPermission.builder();
    if (parameters.getCapabilityCase() != CapabilityParameters.CapabilityCase.SOCKET_CONNECTIVITY_BULK_OR_PARAMETERS) {
      return builder.permissionResult(PermissionResult.DENIED).build();
    }
    SocketConnectivityBulkOrParameters socketParameters = parameters.getSocketConnectivityBulkOrParameters();
    try {
      ProtocolStringList hostNames = socketParameters.getHostNamesList();
      PermissionResult result = PermissionResult.DENIED;

      if (EmptyPredicate.isNotEmpty(hostNames)) {
        for (String host : hostNames) {
          Optional<String> optionalHost = extractHostnameFromHost(host);
          if (optionalHost.isEmpty()) {
            log.error("Cannot extract hostname from the host {}", host);
            break;
          }
          if (connectableHost(optionalHost.get(), socketParameters.getPort())) {
            result = PermissionResult.ALLOWED;
            break;
          }
        }
      }

      return builder.permissionResult(result).build();
    } catch (final Exception ex) {
      log.error("Error Occurred while checking socketConnCapability with proto", ex);
      return builder.permissionResult(PermissionResult.DENIED).build();
    }
  }
}
