/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.healthsource;

import io.harness.cvng.core.entities.QueryParams;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "QueryParamKeys")
public class QueryParamsDTO {
  String serviceInstanceField;
  String index;
  String timeStampIdentifier;
  String timeStampFormat;
  String messageIdentifier;

  @JsonIgnore
  public static QueryParamsDTO getQueryParamsDTO(QueryParams queryParams) {
    if (queryParams == null) {
      return QueryParamsDTO.builder().build();
    }
    return QueryParamsDTO.builder()
        .serviceInstanceField(queryParams.getServiceInstanceField())
        .index(queryParams.getIndex())
        .messageIdentifier(queryParams.getMessageIdentifier())
        .timeStampIdentifier(queryParams.getTimeStampIdentifier())
        .timeStampFormat(queryParams.getTimeStampFormat())
        .build();
  }

  @JsonIgnore
  public QueryParams getQueryParamsEntity() {
    return QueryParams.builder()
        .serviceInstanceField(serviceInstanceField)
        .messageIdentifier(messageIdentifier)
        .timeStampFormat(timeStampFormat)
        .timeStampIdentifier(timeStampIdentifier)
        .index(index)
        .build();
  }
}