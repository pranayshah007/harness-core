/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DwMetricContext extends AutoMetricContext {
  public DwMetricContext(String namespace, String containerName, String serviceName) {
    put("namespace", namespace);
    put("containerName", containerName);
    put("serviceName", serviceName);
  }

  public DwMetricContext(String httpMethod, String namespace, String containerName, String serviceName) {
    put("method", httpMethod);
    put("namespace", namespace);
    put("containerName", containerName);
    put("serviceName", serviceName);
  }

  public DwMetricContext(String method, String resource, String namespace, String containerName, String serviceName) {
    put("method", method);
    put("resource", resource);
    put("namespace", namespace);
    put("containerName", containerName);
    put("serviceName", serviceName);
  }

  public DwMetricContext(
      String method, String resource, String statusCode, String namespace, String containerName, String serviceName) {
    put("method", method);
    put("resource", resource);
    put("namespace", namespace);
    put("containerName", containerName);
    put("serviceName", serviceName);
    put("statusCode", statusCode);
  }
}
