/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.metrics;

import static io.harness.delegate.metrics.DelegateMetricsConstants.DELEGATE_AGENT_METRIC_MAP;

import io.harness.metrics.HarnessMetricRegistry;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.prometheus.client.exporter.common.TextFormat;
import io.swagger.annotations.Api;
import java.io.IOException;
import java.io.StringWriter;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

@Api("metrics")
@Path("/metrics")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class DelegateAgentMetricResource {
  @Inject private HarnessMetricRegistry metricRegistry;

  @GET
  @Timed
  @ExceptionMetered
  public String get() throws IOException {
    try (StringWriter writer = new StringWriter()) {
      TextFormat.write004(writer, metricRegistry.getMetric(DELEGATE_AGENT_METRIC_MAP.keySet()));
      writer.flush();
      return writer.getBuffer().toString();
    }
  }
}
