/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.services;

import io.harness.assessment.settings.beans.dto.BenchmarkDTO;
import io.harness.assessment.settings.beans.dto.BenchmarksListRequest;
import io.harness.assessment.settings.beans.dto.ScoreDTO;
import io.harness.assessment.settings.beans.entities.Assessment;
import io.harness.assessment.settings.beans.entities.Benchmark;
import io.harness.assessment.settings.beans.entities.Question;
import io.harness.assessment.settings.beans.entities.Score;
import io.harness.assessment.settings.beans.entities.ScoreType;
import io.harness.assessment.settings.mappers.BenchmarkMapper;
import io.harness.assessment.settings.repositories.AssessmentRepository;
import io.harness.assessment.settings.repositories.BenchmarkRepository;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.SetUtils;

@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class BenchmarkServiceImpl implements BenchmarkService {
  private BenchmarkRepository benchmarkRepository;
  private AssessmentRepository assessmentRepository;

  @Override
  public List<BenchmarkDTO> uploadBenchmark(BenchmarksListRequest benchmarksListRequest, String assessmentId) {
    // validations of question and scores TODO
    Long version = benchmarksListRequest.getMajorVersion();
    Optional<Assessment> assessmentOptional = assessmentRepository.findByAssessmentIdAndVersion(assessmentId, version);
    if (assessmentOptional.isEmpty()) {
      throw new RuntimeException("Assessment Not Found for Id : " + assessmentId);
    }
    Assessment assessment = assessmentOptional.get();
    List<BenchmarkDTO> benchmarks = benchmarksListRequest.getBenchmarks();
    benchmarks.stream()
        .filter(benchmarkDTO -> Objects.isNull(benchmarkDTO.getIsDefault()))
        .forEach(benchmarkDTO -> benchmarkDTO.setIsDefault(false));
    if (benchmarks.size() != new HashSet<>(benchmarks).size()) {
      throw new RuntimeException("All benchmark Ids have to be unique");
    }
    benchmarksListRequest.getBenchmarks().forEach(benchmarkDTO -> validateBenchmark(benchmarkDTO, assessment));
    if (benchmarks.stream().filter(BenchmarkDTO::getIsDefault).count() != 1) {
      throw new RuntimeException("There has to be one default benchmark for the assessment.");
    }
    for (BenchmarkDTO benchmarkDTO : benchmarks) {
      Optional<Benchmark> benchmarkOptional = benchmarkRepository.findOneByAssessmentIdAndVersionAndBenchmarkId(
          assessmentId, version, benchmarkDTO.getBenchmarkId());
      Benchmark benchmark = BenchmarkMapper.fromDTO(benchmarkDTO);
      // check if the assessment itself exists, then update
      benchmarkOptional.ifPresent(value -> benchmark.setId(value.getId()));
      double assessmentTotal = benchmark.getScores().stream().mapToDouble(Score::getScore).sum();
      long assessmentMaxScore = benchmark.getScores().stream().mapToLong(Score::getMaxScore).sum();
      // TODO add section level scores later.
      benchmark.getScores().add(Score.builder()
                                    .score(assessmentTotal)
                                    .scoreType(ScoreType.ASSESSMENT_LEVEL)
                                    .maxScore(assessmentMaxScore)
                                    .entityId(assessmentId)
                                    .build());
      benchmark.setAssessmentId(assessmentId);
      benchmark.setVersion(version);
      benchmarkRepository.save(benchmark);
    }
    List<Benchmark> benchmarksInDB = benchmarkRepository.findAllByAssessmentIdAndVersion(assessmentId, version);
    return benchmarksInDB.stream().map(BenchmarkMapper::toDTO).collect(Collectors.toList());
  }

  private void validateBenchmark(BenchmarkDTO benchmarkDTO, Assessment assessment) {
    Set<String> setOfQuestionInDb =
        assessment.getQuestions().stream().map(Question::getQuestionId).collect(Collectors.toSet());
    Set<String> setOfQuestionInBenchmark =
        benchmarkDTO.getScores().stream().map(ScoreDTO::getEntityId).collect(Collectors.toSet());
    if (!setOfQuestionInBenchmark.equals(setOfQuestionInDb)) {
      throw new RuntimeException(
          "Benchmark is incomplete : " + SetUtils.difference(setOfQuestionInDb, setOfQuestionInBenchmark)
          + " incorrect benchmark questions : " + SetUtils.difference(setOfQuestionInBenchmark, setOfQuestionInDb));
    }
  }

  @Override
  public List<BenchmarkDTO> getBenchmarks(String assessmentId, Long version) {
    List<Benchmark> benchmarksInDB = benchmarkRepository.findAllByAssessmentIdAndVersion(assessmentId, version);
    return benchmarksInDB.stream().map(BenchmarkMapper::toDTO).collect(Collectors.toList());
  }
}
