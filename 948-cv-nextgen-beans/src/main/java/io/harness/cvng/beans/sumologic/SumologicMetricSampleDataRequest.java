/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.sumologic;

import io.harness.cvng.beans.DataCollectionRequestType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Value
@JsonTypeName("SUMOLOGIC_METRIC_SAMPLE_DATA")
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "SumologicSampleDataRequestKeys")
public class SumologicMetricSampleDataRequest extends AbstractSumologicDataRequest {
  String query;
  long from;
  long to;
  String dsl;
  @Override
  public String getDSL() {
    return dsl;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> dslEnvVariables = super.fetchDslEnvVariables();
    dslEnvVariables.put(SumologicMetricSampleDataRequest.SumologicSampleDataRequestKeys.query, query);
    dslEnvVariables.put(SumologicMetricSampleDataRequest.SumologicSampleDataRequestKeys.from, from);
    dslEnvVariables.put(SumologicMetricSampleDataRequest.SumologicSampleDataRequestKeys.to, to);
    return dslEnvVariables;
  }

  public DataCollectionRequestType getType() {
    return DataCollectionRequestType.SUMOLOGIC_METRIC_SAMPLE_DATA;
  }
}
