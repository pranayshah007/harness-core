/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.mixin;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SocketConnectivityExecutionCapability;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class SocketConnectivityCapabilityGenerator {
  public static SocketConnectivityExecutionCapability buildSocketConnectivityCapability(
      @NotNull String hostName, @NotNull String port) {
    return SocketConnectivityExecutionCapability.builder()
        .hostName(hostName)
        .port(port)
        .url(hostName + ":" + port)
        .build();
  }
  public void addSocketConnectivityExecutionCapability(String repoUrl, List<ExecutionCapability> capabilities) {
    try {
      URI url = new URI(repoUrl);
      int port = url.getPort();
      if (port <= -1) {
        if (url.getScheme() != null) {
          port = url.getScheme().equals("https") ? 443 : 80;
        } else {
          port = 443;
        }
      }
      capabilities.add(SocketConnectivityExecutionCapability.builder()
                           .url(repoUrl)
                           .scheme(url.getScheme())
                           .hostName(url.getHost())
                           .port(String.valueOf(port))
                           .build());
    } catch (URISyntaxException e) {
      log.error("Unable to process URL: " + e.getMessage());
    }
  }
}
