/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.beans.dto.upload;

import io.harness.assessment.settings.beans.entities.QuestionType;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadedQuestion {
  @NotNull String questionId;
  @Min(1) @Max(100) Long questionNumber;
  @NotNull QuestionType questionType;
  String sectionId;
  String sectionName;
  @NotNull
  @Size.List({
    @Size(min = 10, message = "Question text is too short."), @Size(max = 3000, message = "Question text is too long")
  })
  String questionText;
  @NotNull @Size(min = 2, max = 20) @Valid List<UploadedOption> possibleResponses;
}
