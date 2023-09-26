/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.metrics.resources;

import static io.harness.cvng.CVConstants.ENVIRONMENT;

import io.harness.cvng.CVConstants;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.security.annotations.PublicApi;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.prometheus.client.exporter.common.TextFormat;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
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
@PublicApi
@Slf4j
public class MetricResource {
  @Inject private HarnessMetricRegistry harnessMetricRegistry;

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get metrics from metric registry", nickname = "getMetricsFromMetricRegistry", hidden = true)
  public String get() throws IOException {
    final StringWriter writer = new StringWriter();
    Set<String> metrics = new HashSet<>();
    CVConstants.LEARNING_ENGINE_TASKS_METRIC_LIST.forEach(metricName -> {
      metrics.add(metricName);
      metrics.add(ENVIRONMENT + "_" + metricName);
    });
    try {
      TextFormat.write004(writer, harnessMetricRegistry.getMetric());
      writer.flush();
    } finally {
      writer.close();
    }
    return writer.getBuffer().toString();
  }
}
