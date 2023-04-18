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
import io.harness.assessment.settings.beans.entities.Assessment;
import io.harness.assessment.settings.beans.entities.Question;
import io.harness.assessment.settings.beans.entities.QuestionOption;
import io.harness.assessment.settings.beans.entities.QuestionType;

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
  public static void validateQuestionType(Question question, List<AssessmentError> errors) {
    QuestionType questionType = question.getQuestionType();
    switch (questionType) {
      case RATING:
      case LIKERT:
        QuestionUtils.checkOptionsCount(question, errors, 5);
        QuestionUtils.checkSorted(question, errors);
        // Should be ascending or descending
        break;
      case CHECKBOX:
        QuestionUtils.checkSumOfPoints(question, errors);
        break;
      case RADIO_BUTTON:
        QuestionUtils.checkFullPoints(question, errors);
        break;
      case YES_NO:
        QuestionUtils.checkOptionsCount(question, errors, 2);
        QuestionUtils.checkFullPoints(question, errors);
        break;
    }
  }
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
                     .errorMessages(List.of("Invalid number of options for the question type : " + count))
                     .build());
    }
  }

  public static void checkFullPoints(Question question, List<AssessmentError> errors) {
    if (question.getPossibleResponses()
            .stream()
            .filter(Objects::nonNull)
            .map(QuestionOption::getOptionPoints)
            .filter(Objects::nonNull)
            .noneMatch(point -> QUESTION_SCORE == point)) {
      errors.add(AssessmentError.builder()
                     .entityType(EntityType.QUESTION)
                     .entityId(question.getQuestionId())
                     .errorMessages(List.of("One option must have full points : " + question.getQuestionId()))
                     .build());
    }
  }

  public static void checkDuplicateOptions(Question question, List<AssessmentError> errors) {
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
    }
  }

  public static void checkUniqueQuestion(Assessment assessment, List<AssessmentError> errors) {
    List<String> questionsUploaded =
        assessment.getQuestions().stream().map(Question::getQuestionId).collect(Collectors.toList());
    if (!questionsUploaded.stream().distinct().collect(Collectors.toList()).equals(questionsUploaded)) {
      List<String> duplicates = questionsUploaded.stream()
                                    .collect(Collectors.groupingBy(Function.identity()))
                                    .entrySet()
                                    .stream()
                                    .filter(e -> e.getValue().size() > 1)
                                    .map(Map.Entry::getKey)
                                    .collect(Collectors.toList());
      AssessmentError assessmentError = AssessmentError.builder()
                                            .entityType(EntityType.ASSESSMENT)
                                            .entityId(assessment.getAssessmentId())
                                            .errorMessages(List.of("The question ids are not distinct : " + duplicates))
                                            .build();
      errors.add(assessmentError);
      List<Long> duplicateQuestionNumbers = assessment.getQuestions()
                                                .stream()
                                                .map(Question::getQuestionNumber)
                                                .collect(Collectors.groupingBy(Function.identity()))
                                                .entrySet()
                                                .stream()
                                                .filter(e -> e.getValue().size() > 1)
                                                .map(Map.Entry::getKey)
                                                .collect(Collectors.toList());
      AssessmentError duplicateNumbers =
          AssessmentError.builder()
              .entityType(EntityType.ASSESSMENT)
              .entityId(assessment.getAssessmentId())
              .errorMessages(List.of("There are duplicate question numbers : " + duplicateQuestionNumbers))
              .build();
      errors.add(duplicateNumbers);
    }
  }

  public static void checkMissingOptionPoints(Question question, List<AssessmentError> errors) {
    long assignedOptionsCount =
        question.getPossibleResponses().stream().filter(option -> Objects.nonNull(option.getOptionPoints())).count();
    if (assignedOptionsCount != question.getPossibleResponses().size()) {
      List<String> missingPointAssignmentOptions = question.getPossibleResponses()
                                                       .stream()
                                                       .filter(option -> Objects.isNull(option.getOptionPoints()))
                                                       .map(QuestionOption::getOptionId)
                                                       .collect(Collectors.toList());
      errors.add(AssessmentError.builder()
                     .entityType(EntityType.QUESTION)
                     .entityId(question.getQuestionId())
                     .errorMessages(List.of("Options points are missing for : " + missingPointAssignmentOptions))
                     .build());
    }
  }
}
