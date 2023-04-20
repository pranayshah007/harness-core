/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.beans.dto.upload;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadedOption {
  @NotNull String optionId;
  @Size.List({
    @Size(min = 1, message = "Option text is too short."), @Size(max = 3000, message = "Option text is too long")
  })
  @NotNull
  String optionText;
  @Min(0) @Max(10) Long optionPoints;
}
