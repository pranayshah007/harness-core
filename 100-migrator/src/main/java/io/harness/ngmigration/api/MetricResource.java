/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.api;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ClientType;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.security.annotations.HarnessApiKeyAuth;

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

/**
 * Rest End point to expose all the metrics in HarnessMetricRegistry
 * This API is used by Prometheus to scape all the metrics
 *
 * Created by Pranjal on 11/23/2018
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Api("metrics")
@Path("/metrics")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@HarnessApiKeyAuth(clientTypes = ClientType.PROMETHEUS)
@Slf4j
public class MetricResource {
  @Inject private HarnessMetricRegistry metricRegistry;

  @GET
  @Timed
  @ExceptionMetered
  public String get() throws IOException {
    final StringWriter writer = new StringWriter();
    try {
      TextFormat.write004(writer, metricRegistry.getMetric());
      writer.flush();
    } finally {
      writer.close();
    }
    return writer.getBuffer().toString();
  }
}
