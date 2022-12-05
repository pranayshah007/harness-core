/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;

import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public abstract class MetricHealthSourceSpec extends HealthSourceSpec {
  @Valid Set<TimeSeriesMetricPackDTO> metricPacks;

  public abstract List<? extends HealthSourceMetricDefinition> getMetricDefinitions();
}
