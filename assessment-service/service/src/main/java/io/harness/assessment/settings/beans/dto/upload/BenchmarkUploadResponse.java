/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.beans.dto.upload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BenchmarkUploadResponse extends BenchmarksUploadRequest {
  List<AssessmentError> errors;

  @JsonIgnore
  public BenchmarksUploadRequest getRequest() {
    return BenchmarksUploadRequest.builder()
        .assessmentId(assessmentId)
        .type(type)
        .majorVersion(majorVersion)
        .benchmarks(benchmarks)
        .build();
  }
}
