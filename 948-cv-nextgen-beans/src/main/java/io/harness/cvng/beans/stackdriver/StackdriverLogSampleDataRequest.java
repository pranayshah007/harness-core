/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.stackdriver;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.cvng.utils.StackdriverUtils.Scope.LOG_SCOPE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.utils.StackdriverUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("STACKDRIVER_LOG_SAMPLE_DATA")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "StackdriverLogSampleDataRequestKeys")
@OwnedBy(CV)
public class StackdriverLogSampleDataRequest extends StackdriverLogRequest {
  private static final String timestampFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'";
  private static final String timestampFormatKey = "timestampFormat";
  Instant startTime;
  Instant endTime;
  String query;

  public static final String DSL = StackdriverLogSampleDataRequest.readDSL(
      "stackdriver-log-sample-data.datacollection", StackdriverLogSampleDataRequest.class);
  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> dslEnvVariables = StackdriverUtils.getCommonEnvVariables(getConnectorConfigDTO(), LOG_SCOPE);

    dslEnvVariables.put(StackdriverLogSampleDataRequestKeys.query, query);

    dslEnvVariables.put(StackdriverLogSampleDataRequestKeys.startTime, startTime.toString());
    dslEnvVariables.put(StackdriverLogSampleDataRequestKeys.endTime, endTime.toString());
    dslEnvVariables.put(timestampFormatKey, timestampFormat);

    return dslEnvVariables;
  }
}
