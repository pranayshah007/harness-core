/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.monitoredservice.healthsource;

import com.google.inject.Inject;
import java.util.Optional;

public class HealthSourceGeneratorFactory {
  @Inject private PrometheusHealthSourceGenerator prometheusHealthSourceGenerator;
  @Inject private DataDogMetricHealthSourceGenerator dataDogMetricHealthSourceGenerator;

  @Inject private ELKHealthSourceGenerator elkHealthSourceGenerator;

  @Inject private SplunkHealthSourceGenerator splunkHealthSourceGenerator;

  public Optional<HealthSourceGenerator> getHealthSourceGenerator(String stepType) {
    switch (stepType) {
      case "PROMETHEUS":
        return Optional.of(prometheusHealthSourceGenerator);
      case "DATA_DOG":
        return Optional.of(dataDogMetricHealthSourceGenerator);
      case "ELK":
        return Optional.of(elkHealthSourceGenerator);
      case "SPLUNKV2":
        return Optional.of(splunkHealthSourceGenerator);
      default:
        return Optional.empty();
    }
  }
}
