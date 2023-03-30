/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.slotargetspec;

import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("Request")
public class RequestBasedServiceLevelIndicatorSpec extends ServiceLevelIndicatorSpec {
  @NotNull RatioSLIMetricEventType eventType;
  @NotNull String metric1;
  @NotNull String metric2;

  @Override
  public SLIEvaluationType getType() {
    return SLIEvaluationType.REQUEST;
  }

  @Override
  public void generateNameAndIdentifier(
      String serviceLevelObjectiveIdentifier, ServiceLevelIndicatorDTO serviceLevelIndicatorDTO) {
    serviceLevelIndicatorDTO.setName(serviceLevelObjectiveIdentifier + "_"
        + ((RequestBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO.getSpec()).getMetric1());
    serviceLevelIndicatorDTO.setIdentifier(serviceLevelObjectiveIdentifier + "_"
        + ((RequestBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO.getSpec()).getMetric1());
  }
}
