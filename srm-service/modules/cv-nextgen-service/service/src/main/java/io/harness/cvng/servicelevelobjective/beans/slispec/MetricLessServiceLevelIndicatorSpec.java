/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.slispec;

import static io.harness.cvng.CVConstants.METRIC_LESS_SLI_IDENTIFIER;

import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorSpec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
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
@JsonTypeName("Window")
public class MetricLessServiceLevelIndicatorSpec extends ServiceLevelIndicatorSpec {
  @Override
  public SLIEvaluationType getType() {
    return SLIEvaluationType.METRIC_LESS;
  }

  @Override
  public void generateNameAndIdentifier(
      String serviceLevelObjectiveIdentifier, ServiceLevelIndicatorDTO serviceLevelIndicatorDTO) {
    serviceLevelIndicatorDTO.setName(serviceLevelObjectiveIdentifier + "_" + METRIC_LESS_SLI_IDENTIFIER);
  }
}
