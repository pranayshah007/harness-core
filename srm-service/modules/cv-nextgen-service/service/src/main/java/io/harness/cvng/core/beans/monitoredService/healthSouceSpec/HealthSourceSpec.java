/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.monitoredService.HealthSource.CVConfigUpdateResult;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.data.validator.EntityIdentifier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.NotEmpty;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel(description = "This is the Health Source entity defined in Harness",
    subTypes = {AppDynamicsHealthSourceSpec.class, NewRelicHealthSourceSpec.class, StackdriverLogHealthSourceSpec.class,
        SplunkHealthSourceSpec.class, PrometheusHealthSourceSpec.class, StackdriverMetricHealthSourceSpec.class,
        DatadogMetricHealthSourceSpec.class, DatadogLogHealthSourceSpec.class, DynatraceHealthSourceSpec.class,
        ErrorTrackingHealthSourceSpec.class, CustomHealthSourceMetricSpec.class, CustomHealthSourceLogSpec.class,
        SplunkMetricHealthSourceSpec.class, ELKHealthSourceSpec.class, CloudWatchMetricsHealthSourceSpec.class,
        AwsPrometheusHealthSourceSpec.class, NextGenHealthSourceSpec.class})
public abstract class HealthSourceSpec {
  @NotEmpty @EntityIdentifier(allowScoped = true) String connectorRef;

  public abstract CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService);
  @JsonIgnore public abstract DataSourceType getType();

  public void validate() {}
}
