/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.datadog;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("DATADOG_LOG_SAMPLE_DATA")
@Data
@NoArgsConstructor
@SuperBuilder
@FieldNameConstants(innerTypeName = "DatadogLogSampleDataRequestKeys")
@OwnedBy(CV)
@EqualsAndHashCode(callSuper = true)
public class DatadogLogSampleDataRequest extends DatadogRequest {
  public static final String DSL =
      DatadogLogSampleDataRequest.readDSL("datadog-logs.datacollection", DatadogLogSampleDataRequest.class);

  Long from;
  Long to;
  List<String> indexes;
  String query;
  Long limit;

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> dslEnvVariables = super.fetchDslEnvVariables();
    dslEnvVariables.put(DatadogLogSampleDataRequestKeys.query, query);
    dslEnvVariables.put(DatadogLogSampleDataRequestKeys.from, from);
    dslEnvVariables.put(DatadogLogSampleDataRequestKeys.to, to);
    dslEnvVariables.put(DatadogLogSampleDataRequestKeys.limit, limit);
    return dslEnvVariables;
  }
}
