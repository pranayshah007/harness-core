/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.assessment.settings.beans.dto.BenchmarkDTO;
import io.harness.assessment.settings.beans.dto.EntityType;
import io.harness.assessment.settings.beans.dto.ScoreDTO;
import io.harness.assessment.settings.beans.dto.upload.AssessmentError;
import io.harness.assessment.settings.beans.entities.Assessment;
import io.harness.assessment.settings.beans.entities.Question;
import io.harness.assessment.settings.beans.entities.ScoreType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.SetUtils;

@Slf4j
@OwnedBy(HarnessTeam.SEI)
@UtilityClass
public class BenchmarkUtils {
  public static void checkUniqueBenchmarkIds(
      List<BenchmarkDTO> benchmarks, List<AssessmentError> errors, String assessmentId) {
    if (benchmarks.size() != new HashSet<>(benchmarks).size()) {
      List<String> duplicates = benchmarks.stream()
                                    .map(BenchmarkDTO::getBenchmarkId)
                                    .collect(Collectors.groupingBy(Function.identity()))
                                    .entrySet()
                                    .stream()
                                    .filter(e -> e.getValue().size() > 1)
                                    .map(Map.Entry::getKey)
                                    .collect(Collectors.toList());

      errors.add(AssessmentError.builder()
                     .entityType(EntityType.ASSESSMENT)
                     .entityId(assessmentId)
                     .errorMessages(List.of("All benchmark Ids have to be unique : " + duplicates))
                     .build());
    }
  }

  public static void validateBenchmarkQuestions(
      BenchmarkDTO benchmarkDTO, Assessment assessment, List<AssessmentError> errors) {
    Set<String> setOfQuestionInDb =
        assessment.getQuestions().stream().map(Question::getQuestionId).collect(Collectors.toSet());
    Set<String> setOfQuestionInBenchmark =
        benchmarkDTO.getScores().stream().map(ScoreDTO::getEntityId).collect(Collectors.toSet());
    if (SetUtils.difference(setOfQuestionInDb, setOfQuestionInBenchmark).size() > 0) {
      errors.add(AssessmentError.builder()
                     .entityType(EntityType.BENCHMARK)
                     .entityId(benchmarkDTO.getBenchmarkId())
                     .errorMessages(List.of("Benchmark is incomplete ID : "
                         + SetUtils.difference(setOfQuestionInDb, setOfQuestionInBenchmark)))
                     .build());
    }
    if (SetUtils.difference(setOfQuestionInBenchmark, setOfQuestionInDb).size() > 0) {
      errors.add(AssessmentError.builder()
                     .entityType(EntityType.BENCHMARK)
                     .entityId(benchmarkDTO.getBenchmarkId())
                     .errorMessages(List.of("Incorrect benchmark question ID : "
                         + SetUtils.difference(setOfQuestionInBenchmark, setOfQuestionInDb)))
                     .build());
    }

    validateBenchmarkScores(benchmarkDTO, assessment, errors);
    // TODO both max score and benchmark score should be in range.
  }

  public static void validateBenchmarkScores(
      BenchmarkDTO benchmarkDTO, Assessment assessment, List<AssessmentError> errors) {
    Map<String, Question> questionMap =
        assessment.getQuestions().stream().collect(Collectors.toMap(Question::getQuestionId, Function.identity()));
    for (ScoreDTO scoreDTO : benchmarkDTO.getScores()) {
      Question question = questionMap.getOrDefault(scoreDTO.getEntityId(), Question.builder().build());
      if (scoreDTO.getScoreType() == ScoreType.QUESTION_LEVEL
          && !Objects.equals(scoreDTO.getMaxScore(), question.getMaxScore())) {
        errors.add(AssessmentError.builder()
                       .entityType(EntityType.QUESTION)
                       .entityId(scoreDTO.getEntityId())
                       .errorMessages(List.of("Incorrect Max score for question : " + scoreDTO.getMaxScore()))
                       .build());
      }
      if (scoreDTO.getScoreType() == ScoreType.QUESTION_LEVEL && (scoreDTO.getScore() > scoreDTO.getMaxScore())) {
        errors.add(AssessmentError.builder()
                       .entityType(EntityType.QUESTION)
                       .entityId(scoreDTO.getEntityId())
                       .errorMessages(List.of("Incorrect Max score for question : " + scoreDTO.getScore()))
                       .build());
      }
    }
  }
}
