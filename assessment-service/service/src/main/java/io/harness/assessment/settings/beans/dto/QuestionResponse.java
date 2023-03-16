/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.beans.dto;

import io.harness.assessment.settings.beans.entities.QuestionType;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionResponse {
  String questionId;
  Long questionNumber;
  QuestionType questionType;
  String sectionId;
  String sectionName;
  String questionText;
  List<OptionResponse> possibleResponses;
}
