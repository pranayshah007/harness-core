/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.services;

import io.harness.assessment.settings.beans.dto.BenchmarkDTO;
import io.harness.assessment.settings.beans.dto.BenchmarksListRequest;
import io.harness.assessment.settings.beans.entities.Assessment;
import io.harness.assessment.settings.beans.entities.Benchmark;
import io.harness.assessment.settings.beans.entities.Score;
import io.harness.assessment.settings.beans.entities.ScoreType;
import io.harness.assessment.settings.mappers.BenchmarkMapper;
import io.harness.assessment.settings.repositories.AssessmentRepository;
import io.harness.assessment.settings.repositories.BenchmarkRepository;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class BenchmarkServiceImpl implements BenchmarkService {
  private BenchmarkRepository benchmarkRepository;
  private AssessmentRepository assessmentRepository;

  @Override
  public BenchmarkDTO uploadBenchmark(BenchmarksListRequest benchmarksListRequest, String assessmentId) {
    // validations of question and scores TODO
    Long version = benchmarksListRequest.getVersion();
    Optional<Assessment> assessmentOptional = assessmentRepository.findByAssessmentIdAndVersion(assessmentId, version);
    if (assessmentOptional.isEmpty()) {
      throw new RuntimeException("Assessment Not Found for Id : " + assessmentId);
    }

    for (BenchmarkDTO benchmarkDTO : benchmarksListRequest.getBenchmarks()) {
      Optional<Benchmark> benchmarkOptional = benchmarkRepository.findOneByAssessmentIdAndVersionAndBenchmarkId(
          assessmentId, version, benchmarkDTO.getBenchmarkId());
      if (benchmarkOptional.isEmpty()) {
        // check if the assessment itself exists.
        Benchmark benchmark = BenchmarkMapper.fromDTO(benchmarkDTO);
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
      } else {
        // Update and overwrite
      }
    }
    return null;
  }

  @Override
  public List<BenchmarkDTO> getBenchmarks(String assessmentId, Long version) {
    //    return benchmarkRepository.findAllByAssessmentIdAndVersion(assessmentId, 1L).stream().map();
    return null;
  }
}
