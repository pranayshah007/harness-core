/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.signalfx;

import io.harness.cvng.beans.DataCollectionRequestType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("SIGNALFX_METRIC_SAMPLE_DATA")
@Getter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "SignalFXSampleDataRequestKeys")
public class SignalFXMetricSampleDataRequest extends AbstractSignalFXDataRequest {
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
    dslEnvVariables.put(SignalFXSampleDataRequestKeys.query, query);
    dslEnvVariables.put(SignalFXSampleDataRequestKeys.from, from);
    dslEnvVariables.put(SignalFXSampleDataRequestKeys.to, to);
    return dslEnvVariables;
  }

  public DataCollectionRequestType getType() {
    return DataCollectionRequestType.SIGNALFX_METRIC_SAMPLE_DATA;
  }
}
