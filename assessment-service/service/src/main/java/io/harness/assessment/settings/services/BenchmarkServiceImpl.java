/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.services;

import io.harness.assessment.settings.beans.dto.BenchmarkDTO;
import io.harness.assessment.settings.beans.dto.BenchmarksListRequest;
import io.harness.assessment.settings.beans.dto.upload.AssessmentError;
import io.harness.assessment.settings.beans.dto.upload.BenchmarkUploadResponse;
import io.harness.assessment.settings.beans.entities.Assessment;
import io.harness.assessment.settings.beans.entities.Benchmark;
import io.harness.assessment.settings.beans.entities.Score;
import io.harness.assessment.settings.beans.entities.ScoreType;
import io.harness.assessment.settings.mappers.BenchmarkMapper;
import io.harness.assessment.settings.mappers.BenchmarkUtils;
import io.harness.assessment.settings.repositories.AssessmentRepository;
import io.harness.assessment.settings.repositories.BenchmarkRepository;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class BenchmarkServiceImpl implements BenchmarkService {
  private BenchmarkRepository benchmarkRepository;
  private AssessmentRepository assessmentRepository;

  @Override
  public BenchmarkUploadResponse uploadBenchmark(BenchmarksListRequest benchmarksListRequest, String assessmentId) {
    // validations of question and scores TODO
    Long version = benchmarksListRequest.getMajorVersion();
    List<AssessmentError> assessmentErrors = new ArrayList<>();
    Optional<Assessment> assessmentOptional = assessmentRepository.findByAssessmentIdAndVersion(assessmentId, version);
    if (assessmentOptional.isEmpty()) {
      throw new RuntimeException("Assessment Not Found for Id : " + assessmentId);
    }
    Assessment assessment = assessmentOptional.get();
    List<BenchmarkDTO> benchmarks = benchmarksListRequest.getBenchmarks();
    benchmarks.stream()
        .filter(benchmarkDTO -> Objects.isNull(benchmarkDTO.getIsDefault()))
        .forEach(benchmarkDTO -> benchmarkDTO.setIsDefault(false));

    BenchmarkUtils.checkUniqueBenchmarkIds(benchmarks, assessmentErrors, assessmentId);
    benchmarksListRequest.getBenchmarks().forEach(
        benchmarkDTO -> BenchmarkUtils.validateBenchmarkQuestions(benchmarkDTO, assessment, assessmentErrors));
    if (assessmentErrors.size() > 0) {
      return BenchmarkUploadResponse.builder().errors(assessmentErrors).build();
    }
    // Mantain one default very Imp TODO
    /*if (benchmarks.stream().filter(BenchmarkDTO::getIsDefault).count() != 1) {
      throw new RuntimeException("There has to be one default benchmark for the assessment.");
    }*/
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
    List<BenchmarkDTO> benchmarkDTOList =
        benchmarksInDB.stream().map(BenchmarkMapper::toDTO).collect(Collectors.toList());

    return BenchmarkUploadResponse.builder().benchmarks(benchmarkDTOList).errors(assessmentErrors).build();
  }
  @Override
  public List<BenchmarkDTO> getBenchmarks(String assessmentId, Long version) {
    List<Benchmark> benchmarksInDB = benchmarkRepository.findAllByAssessmentIdAndVersion(assessmentId, version);
    return benchmarksInDB.stream().map(BenchmarkMapper::toDTO).collect(Collectors.toList());
  }
}
