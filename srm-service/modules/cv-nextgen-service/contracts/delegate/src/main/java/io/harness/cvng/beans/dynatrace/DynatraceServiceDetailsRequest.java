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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("DYNATRACE_SERVICE_DETAILS_REQUEST")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
@FieldNameConstants(innerTypeName = "DynatraceServiceDetailsRequestKeys")
public class DynatraceServiceDetailsRequest extends DynatraceRequest {
  private static final List<String> FIELDS = Arrays.asList("fromRelationships", "toRelationships");
  private static final String DSL =
      DataCollectionRequest.readDSL("dynatrace-service-details.datacollection", DynatraceServiceDetailsRequest.class);

  private String serviceId;

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> commonEnvVariables = super.fetchDslEnvVariables();
    String fieldsParam = String.join(",", FIELDS);
    commonEnvVariables.put("fields", fieldsParam);
    commonEnvVariables.put("entityId", serviceId);
    return commonEnvVariables;
  }
}
