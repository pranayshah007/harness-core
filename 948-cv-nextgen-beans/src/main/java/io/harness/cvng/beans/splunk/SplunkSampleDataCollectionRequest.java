/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.splunk;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequestType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@JsonTypeName("SPLUNK_SAMPLE_DATA")
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "SplunkValidationDataCollectionRequestKeys")
@OwnedBy(CV)
public class SplunkSampleDataCollectionRequest extends SplunkDataCollectionRequest {
  private static int DATA_COLLECTION_DURATION_IN_DAYS = 7;

  private String query;

  @Override
  public String getDSL() {
    return SplunkSampleDataCollectionRequest.readDSL(
        "splunk-sample-data.datacollection", SplunkSampleDataCollectionRequest.class);
  }

  @Override
  public DataCollectionRequestType getType() {
    return DataCollectionRequestType.SPLUNK_SAMPLE_DATA;
  }

  @Override
  public Instant getEndTime(Instant currentTime) {
    return currentTime;
  }

  @Override
  public Instant getStartTime(Instant currentTime) {
    return currentTime.minus(Duration.ofDays(DATA_COLLECTION_DURATION_IN_DAYS));
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    return new HashMap<String, Object>() {
      { put(SplunkValidationDataCollectionRequestKeys.query, query); }
    };
  }
}
