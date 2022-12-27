/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.dynatrace;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeName("DYNATRACE_SAMPLE_DATA_REQUEST")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
public class DynatraceSampleDataRequest extends DynatraceRequest {
  public static final String DSL =
      DataCollectionRequest.readDSL("dynatrace-sample-data.datacollection", DynatraceSampleDataRequest.class);

  private static final String RESOLUTION_PARAM = "1m";
  private String serviceId;
  private long from;
  private long to;
  private String metricSelector;
  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> commonEnvVariables = super.fetchDslEnvVariables();
    commonEnvVariables.put("entitySelector", "type(\"dt.entity.service\"),entityId(".concat(serviceId).concat(")"));
    commonEnvVariables.put("resolution", RESOLUTION_PARAM);
    commonEnvVariables.put("metricSelector", metricSelector);
    commonEnvVariables.put("from", from);
    commonEnvVariables.put("to", to);
    return commonEnvVariables;
  }
}
