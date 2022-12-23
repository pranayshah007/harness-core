/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.prometheus;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeName("PROMETHEUS_LABEL_VALUES_GET")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
public class PrometheusLabelValuesFetchRequest extends PrometheusRequest {
  public static final String DSL = PrometheusLabelValuesFetchRequest.readDSL(
      "prometheus-label-values.datacollection", PrometheusMetricListFetchRequest.class);

  private String labelName;

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> collectionEnvs = new HashMap<>();
    collectionEnvs.put("labelName", labelName);
    return collectionEnvs;
  }
}
