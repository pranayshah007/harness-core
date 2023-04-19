/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.common;

import static io.harness.assessment.settings.services.AssessmentUploadServiceImpl.QUESTION_SCORE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.assessment.settings.beans.entities.Question;
import io.harness.assessment.settings.beans.entities.QuestionOption;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.SEI)
@UtilityClass
public class QuestionUtils {
  public static void checkSorted(Question question, List<String> errors) {
    List<Long> pointsList =
        question.getPossibleResponses().stream().map(QuestionOption::getOptionPoints).collect(Collectors.toList());
    if (pointsList == pointsList.stream().sorted().collect(Collectors.toList())
        || pointsList == pointsList.stream().sorted(Collections.reverseOrder()).collect(Collectors.toList())) {
      errors.add("The points should be in ascending or descending order : " + question.getQuestionId());
    }
  }

  public static void checkSumOfPoints(Question question, List<String> errors) {
    long sumOfOptionPoints = question.getPossibleResponses().stream().mapToLong(QuestionOption::getOptionPoints).sum();
    if (sumOfOptionPoints > QUESTION_SCORE) {
      errors.add("Points exceed total allowed for question : " + question.getQuestionId());
    }
  }

  public static void checkOptionsCount(Question question, List<String> errors, int count) {
    if (question.getPossibleResponses().size() != count) {
      errors.add("Question type Yes/No should have only options : " + count);
    }
  }

  public static void checkFullPoints(Question question, List<String> errors) {
    if (question.getPossibleResponses()
            .stream()
            .filter(Objects::nonNull)
            .noneMatch(x -> x.getOptionPoints() == QUESTION_SCORE)) {
      errors.add("One option must have full points : " + question.getQuestionId());
    }
  }
}
