/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.taskagent.servicediscovery;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class ServiceDiscovery {
  private static final String SERVICE_HOST_TEMPLATE = "%s_SERVICE_HOST";
  private static final String SERVICE_PORT_TEMPLATE = "%s_SERVICE_PORT";
  public ServiceEndpoint getDelegateServiceEndpoint(final String delegateName) {
    final String delegateServiceName = getDelegateServiceName(delegateName);
    final String delegateHost = System.getenv(String.format(SERVICE_HOST_TEMPLATE, delegateServiceName));
    final String delegatePort = System.getenv(String.format(SERVICE_PORT_TEMPLATE, delegateServiceName));

    try {
      return new ServiceEndpoint(delegateHost, Integer.parseInt(delegatePort));
    } catch (final NullPointerException e) {
      log.error("Delegate service host ({}) or port ({}) don't exist for delegate {}", delegateHost, delegatePort,
          delegateName, e);
    } catch (final NumberFormatException e) {
      log.error("Error parsing delegate service port {} for delegate {}", delegatePort, delegateName, e);
    }
    throw new IllegalStateException("Delegate ServiceEndpoint not present for " + delegateName);
  }

  private static String getDelegateServiceName(final String delegateName) {
    return "delegate-service";
  }
}
