/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.services;

import io.harness.assessment.settings.beans.dto.AssessmentResultsResponse;
import io.harness.assessment.settings.beans.dto.ScoreOverviewDTO;
import io.harness.assessment.settings.beans.dto.UserResponsesResponse;
import io.harness.assessment.settings.beans.entities.Assessment;
import io.harness.assessment.settings.beans.entities.AssessmentResponse;
import io.harness.assessment.settings.beans.entities.Benchmark;
import io.harness.assessment.settings.beans.entities.OrganizationEvaluation;
import io.harness.assessment.settings.beans.entities.Question;
import io.harness.assessment.settings.beans.entities.QuestionOption;
import io.harness.assessment.settings.beans.entities.Score;
import io.harness.assessment.settings.beans.entities.ScoreType;
import io.harness.assessment.settings.mappers.AssessmentResponseMapper;
import io.harness.assessment.settings.mappers.BenchmarkMapper;
import io.harness.assessment.settings.repositories.AssessmentRepository;
import io.harness.assessment.settings.repositories.AssessmentResponseRepository;
import io.harness.assessment.settings.repositories.BenchmarkRepository;
import io.harness.assessment.settings.repositories.OrganizationEvaluationRepository;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class AssessmentResultServiceImpl implements AssessmentResultService {
  public static final int TOP_BOTTOM_ENTRY_COUNT = 3;
  private AssessmentResponseRepository assessmentResponseRepository;
  private OrganizationEvaluationRepository organizationEvaluationRepository;
  private AssessmentRepository assessmentRepository;
  private BenchmarkRepository benchmarkRepository;

  @Override
  public AssessmentResultsResponse getResults(String resultCode, String benchmarkId) {
    Optional<AssessmentResponse> assessmentResponseOptional =
        assessmentResponseRepository.findOneByResultLink(resultCode);
    if (assessmentResponseOptional.isEmpty()) {
      throw new RuntimeException("Result link invalid : " + resultCode);
    }
    AssessmentResponse assessmentResponse = assessmentResponseOptional.get();
    AssessmentResultsResponse assessmentResultsResponse = AssessmentResponseMapper.toDTO(assessmentResponse);
    Optional<Assessment> assessmentOptional = assessmentRepository.findByAssessmentIdAndVersion(
        assessmentResponse.getAssessmentId(), assessmentResponse.getVersion());
    if (assessmentOptional.isEmpty()) {
      throw new RuntimeException("Assessment error");
    }
    Assessment assessment = assessmentOptional.get();
    assessmentResultsResponse.setMinorVersion(assessment.getMinorVersion());
    Optional<OrganizationEvaluation> organizationEvaluationOptional =
        organizationEvaluationRepository.findOneByAssessmentIdAndOrganizationIdAndVersion(
            assessmentResponse.getAssessmentId(), assessmentResponse.getOrganizationId(),
            assessmentResponse.getVersion());
    if (organizationEvaluationOptional.isEmpty()) {
      throw new RuntimeException("Org assessment calculations error.");
    }
    Map<String, Double> benchmarkMap =
        getBenchmarkMap(benchmarkId, assessment.getAssessmentId(), assessment.getVersion());
    Optional<Benchmark> benchmarkOptional = benchmarkRepository.findOneByAssessmentIdAndVersionAndIsDefault(
        assessment.getAssessmentId(), assessment.getVersion(), true);
    if (benchmarkOptional.isPresent()) {
      benchmarkId = benchmarkOptional.get().getBenchmarkId();
    }
    assessmentResultsResponse.setAssessmentName(assessment.getAssessmentName());
    OrganizationEvaluation organizationEvaluation = organizationEvaluationOptional.get();
    Map<String, Double> orgQuestionScoreMap = organizationEvaluation.getScores()
                                                  .stream()
                                                  .filter(score -> score.getScoreType() == ScoreType.QUESTION_LEVEL)
                                                  .collect(Collectors.toMap(Score::getEntityId, Score::getScore));
    assessmentResultsResponse.setOrganizationScores(organizationEvaluation.getScores());
    Map<String, Question> entityMap =
        assessment.getQuestions().stream().collect(Collectors.toMap(Question::getQuestionId, Function.identity()));
    assessmentResultsResponse.getResponses().forEach(userResponseDTO -> {
      Question question = entityMap.get(userResponseDTO.getQuestionId());
      userResponseDTO.setQuestionText(question.getQuestionText());
      userResponseDTO.setMaxScore(question.getMaxScore());
      userResponseDTO.setSectionId(question.getSectionId());
      userResponseDTO.setSectionText(question.getSectionName());
      userResponseDTO.setQuestionType(question.getQuestionType());
      userResponseDTO.setBenchmarkScore(benchmarkMap.get(question.getQuestionId()));
      userResponseDTO.setOrganizationScore(orgQuestionScoreMap.get(question.getQuestionId()));
      Map<String, QuestionOption> optionMap = question.getPossibleResponses().stream().collect(
          Collectors.toMap(QuestionOption::getOptionId, Function.identity()));
      // set the option texts
      userResponseDTO.getResponses().forEach(optionResponse -> {
        QuestionOption questionOption = optionMap.get(optionResponse.getOptionId());
        optionResponse.setOptionText(questionOption.getOptionText());
      });
    });
    // Adding section to set overview.
    Long numOfResponses = organizationEvaluation.getNumOfResponses();
    setResultsOverview(benchmarkId, assessmentResultsResponse, numOfResponses);
    return assessmentResultsResponse;
  }

  @NotNull
  private Map<String, Double> getBenchmarkMap(String benchmarkId, String assessmentId, Long version) {
    Map<String, Double> benchmarkMap = new HashMap<>();
    Benchmark benchmark = null;
    if (StringUtils.isNotEmpty(benchmarkId)) {
      Optional<Benchmark> benchmarkOptional =
          benchmarkRepository.findOneByAssessmentIdAndVersionAndBenchmarkId(assessmentId, version, benchmarkId);
      log.info("{}", benchmarkOptional);
      if (benchmarkOptional.isEmpty()) {
        throw new RuntimeException("Invalid benchmark Id");
      }
      benchmark = benchmarkOptional.get();

    } else {
      Optional<Benchmark> benchmarkOptional =
          benchmarkRepository.findOneByAssessmentIdAndVersionAndIsDefault(assessmentId, version, true);
      if (benchmarkOptional.isPresent()) {
        benchmark = benchmarkOptional.get();
      }
    }
    if (Objects.nonNull(benchmark)) {
      benchmark.getScores()
          .stream()
          .filter(score -> score.getScoreType() == ScoreType.QUESTION_LEVEL)
          .forEach(score -> benchmarkMap.put(score.getEntityId(), score.getScore()));
    }
    return benchmarkMap;
  }

  private void setResultsOverview(
      String benchmarkId, AssessmentResultsResponse assessmentResultsResponse, Long numOfResponses) {
    ScoreOverviewDTO scoreOverviewDTO =
        ScoreOverviewDTO.builder()
            .selfScore(assessmentResultsResponse.getUserScores()
                           .stream()
                           .filter(score -> score.getScoreType() == ScoreType.ASSESSMENT_LEVEL)
                           .map(BenchmarkMapper::fromScore)
                           .findFirst()
                           .orElse(null))
            .organizationScore(assessmentResultsResponse.getOrganizationScores()
                                   .stream()
                                   .filter(score -> score.getScoreType() == ScoreType.ASSESSMENT_LEVEL)
                                   .map(BenchmarkMapper::fromScore)
                                   .findFirst()
                                   .orElse(null))
            .build();
    assessmentResultsResponse.setScoreOverview(scoreOverviewDTO);
    scoreOverviewDTO.setNumberOfResponses(numOfResponses);
    Integer percentageDiffOrg = Math.toIntExact(
        Math.round(((scoreOverviewDTO.getSelfScore().getScore() - scoreOverviewDTO.getOrganizationScore().getScore())
                       / scoreOverviewDTO.getSelfScore().getMaxScore())
            * 100));
    scoreOverviewDTO.setPercentageDiffOrg(percentageDiffOrg);
    // Duplicate get code fix - TODO
    if (StringUtils.isNotEmpty(benchmarkId)) {
      Optional<Benchmark> benchmarkOptional = benchmarkRepository.findOneByAssessmentIdAndVersionAndBenchmarkId(
          assessmentResultsResponse.getAssessmentId(), assessmentResultsResponse.getMajorVersion(), benchmarkId);
      log.info("{}", benchmarkOptional);
      if (benchmarkOptional.isEmpty()) {
        throw new RuntimeException("Invalid benchmark Id");
      }
      Benchmark benchmark = benchmarkOptional.get();
      scoreOverviewDTO.setBenchmarkId(benchmark.getBenchmarkId());
      scoreOverviewDTO.setBenchmarkName(benchmark.getBenchmarkName());
      benchmark.getScores()
          .stream()
          .filter(score -> score.getScoreType() == ScoreType.ASSESSMENT_LEVEL)
          .findFirst()
          .ifPresent(benchmarkScore -> scoreOverviewDTO.setBenchmarkScore(BenchmarkMapper.fromScore(benchmarkScore)));
      Integer percentageDiffBenchmark = Math.toIntExact(
          Math.round(((scoreOverviewDTO.getSelfScore().getScore() - scoreOverviewDTO.getBenchmarkScore().getScore())
                         / scoreOverviewDTO.getSelfScore().getMaxScore())
              * 100));
      scoreOverviewDTO.setPercentageDiffBenchmark(percentageDiffBenchmark);
    }
    setTopAndBottomN(assessmentResultsResponse);
  }

  private void setTopAndBottomN(AssessmentResultsResponse assessmentResultsResponse) {
    ArrayList<UserResponsesResponse> responseArrayList = new ArrayList<>(assessmentResultsResponse.getResponses());
    responseArrayList.sort(Comparator.comparingDouble(x -> (x.getUserScore() - x.getMaxScore())));
    assessmentResultsResponse.getScoreOverview().setWorst(
        responseArrayList.stream().limit(TOP_BOTTOM_ENTRY_COUNT).collect(Collectors.toList()));
    assessmentResultsResponse.getScoreOverview().setBest(responseArrayList.subList(
        Math.max(responseArrayList.size() - TOP_BOTTOM_ENTRY_COUNT, 0), responseArrayList.size()));
  }
}
