/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.capability.CapabilitySubjectPermission.CapabilitySubjectPermissionBuilder;
import io.harness.capability.CapabilitySubjectPermission.PermissionResult;
import io.harness.capability.SocketConnectivityParameters;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SocketConnectivityExecutionCapability;

import com.google.inject.Singleton;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SocketConnectivityCapabilityCheck implements CapabilityCheck, ProtoCapabilityCheck {
  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    SocketConnectivityExecutionCapability socketConnCapability =
        (SocketConnectivityExecutionCapability) delegateCapability;
    try {
      boolean valid;
      if (socketConnCapability.getHostName() != null) {
        valid = connectableHost(socketConnCapability.getHostName(), Integer.parseInt(socketConnCapability.getPort()));
      } else {
        valid = connectableHost(socketConnCapability.getUrl(), Integer.parseInt(socketConnCapability.getPort()));
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
    if (parameters.getCapabilityCase() != CapabilityParameters.CapabilityCase.SOCKET_CONNECTIVITY_PARAMETERS) {
      return builder.permissionResult(PermissionResult.DENIED).build();
    }
    SocketConnectivityParameters socketParameters = parameters.getSocketConnectivityParameters();
    try {
      final String host =
          socketParameters.getHostName().isEmpty() ? socketParameters.getUrl() : socketParameters.getHostName();
      return builder
          .permissionResult(
              connectableHost(host, socketParameters.getPort()) ? PermissionResult.ALLOWED : PermissionResult.DENIED)
          .build();
    } catch (final Exception ex) {
      log.error("Error Occurred while checking socketConnCapability with proto", ex);
      return builder.permissionResult(PermissionResult.DENIED).build();
    }
  }

  public static boolean connectableHost(String host, int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 5000); // 5 sec timeout
      log.info("[Delegate Capability] Socket Connection Succeeded for url {} on port {}", host, port);
      return true;
    } catch (final IOException e) {
      log.info("[Delegate Capability] Socket Connection Failed for url " + host + " on port " + port, e);
    }
    return false; // Either timeout or unreachable or failed DNS lookup.
  }
}
