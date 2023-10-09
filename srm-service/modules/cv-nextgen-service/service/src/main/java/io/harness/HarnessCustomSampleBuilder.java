/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.prometheus.client.Collector;
import io.prometheus.client.dropwizard.samplebuilder.SampleBuilder;
import java.util.ArrayList;
import java.util.List;

public class HarnessCustomSampleBuilder implements SampleBuilder {
  public static final String CVNG_METRICS_PREFIX = "io_harness_cvng_";

  @Override
  public Collector.MetricFamilySamples.Sample createSample(String dropwizardName, String nameSuffix,
      List<String> additionalLabelNames, List<String> additionalLabelValues, double value) {
    String dropwizardNameWithPrefix = CVNG_METRICS_PREFIX + dropwizardName;
    List<String> allLabelNames = new ArrayList<>(additionalLabelNames);
    List<String> allLabelValues = new ArrayList<>(additionalLabelValues);
    allLabelNames.addAll(CVNGPrometheusExporterUtils.contextLabels.keySet());
    allLabelValues.addAll(CVNGPrometheusExporterUtils.contextLabels.values());
    final String suffix = nameSuffix == null ? "" : nameSuffix;
    return new Collector.MetricFamilySamples.Sample(
        Collector.sanitizeMetricName(dropwizardNameWithPrefix + suffix), allLabelNames, allLabelValues, value);
  }
}
