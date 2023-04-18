/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.beans.dto.upload;

import io.harness.assessment.settings.beans.dto.BenchmarkDTO;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BenchmarksUploadRequest {
  @NotNull Long majorVersion;
  @Size(min = 1, max = 50) @NotNull @Valid List<BenchmarkDTO> benchmarks;
}
