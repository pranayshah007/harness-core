/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.mappers;

import static io.harness.assessment.settings.services.AssessmentUploadServiceImpl.QUESTION_SCORE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.assessment.settings.beans.dto.EntityType;
import io.harness.assessment.settings.beans.dto.upload.AssessmentError;
import io.harness.assessment.settings.beans.entities.Question;
import io.harness.assessment.settings.beans.entities.QuestionOption;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.SEI)
@UtilityClass
public class QuestionUtils {
  public static void checkSorted(Question question, List<AssessmentError> errors) {
    List<Long> pointsList =
        question.getPossibleResponses().stream().map(QuestionOption::getOptionPoints).collect(Collectors.toList());
    if (pointsList == pointsList.stream().sorted().collect(Collectors.toList())
        || pointsList == pointsList.stream().sorted(Collections.reverseOrder()).collect(Collectors.toList())) {
      errors.add(AssessmentError.builder()
                     .entityType(EntityType.QUESTION)
                     .entityId(question.getQuestionId())
                     .errorMessages(
                         List.of("The points should be in ascending or descending order : " + question.getQuestionId()))
                     .build());
    }
  }

  public static void checkSumOfPoints(Question question, List<AssessmentError> errors) {
    long sumOfOptionPoints = question.getPossibleResponses().stream().mapToLong(QuestionOption::getOptionPoints).sum();
    if (sumOfOptionPoints > QUESTION_SCORE) {
      errors.add(AssessmentError.builder()
                     .entityType(EntityType.QUESTION)
                     .entityId(question.getQuestionId())
                     .errorMessages(List.of("Points exceed total allowed for question : " + question.getQuestionId()))
                     .build());
    }
  }

  public static void checkOptionsCount(Question question, List<AssessmentError> errors, int count) {
    if (question.getPossibleResponses().size() != count) {
      errors.add(AssessmentError.builder()
                     .entityType(EntityType.QUESTION)
                     .entityId(question.getQuestionId())
                     .errorMessages(List.of("Question type Yes/No should have only options : " + count))
                     .build());
    }
  }

  public static void checkFullPoints(Question question, List<AssessmentError> errors) {
    if (question.getPossibleResponses()
            .stream()
            .filter(Objects::nonNull)
            .noneMatch(x -> x.getOptionPoints() == QUESTION_SCORE)) {
      errors.add(AssessmentError.builder()
                     .entityType(EntityType.QUESTION)
                     .entityId(question.getQuestionId())
                     .errorMessages(List.of("One option must have full points : " + question.getQuestionId()))
                     .build());
    }
  }

  public static void checkDuplicateOptions(List<AssessmentError> errors, Question question) {
    List<String> options =
        question.getPossibleResponses().stream().map(QuestionOption::getOptionId).collect(Collectors.toList());
    if (!options.stream().distinct().collect(Collectors.toList()).equals(options)) {
      List<String> duplicates = options.stream()
                                    .collect(Collectors.groupingBy(Function.identity()))
                                    .entrySet()
                                    .stream()
                                    .filter(e -> e.getValue().size() > 1)
                                    .map(Map.Entry::getKey)
                                    .collect(Collectors.toList());
      errors.add(AssessmentError.builder()
                     .entityType(EntityType.QUESTION)
                     .entityId(question.getQuestionId())
                     .errorMessages(List.of("Options are not distinct for question : " + duplicates))
                     .build());
      // add reason
    }
  }
}
