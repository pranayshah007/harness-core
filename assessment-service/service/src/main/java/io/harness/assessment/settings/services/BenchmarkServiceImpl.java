/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.services;

import io.harness.assessment.settings.beans.dto.BenchmarkDTO;
import io.harness.assessment.settings.beans.dto.EntityType;
import io.harness.assessment.settings.beans.dto.ScoreDTO;
import io.harness.assessment.settings.beans.dto.upload.AssessmentError;
import io.harness.assessment.settings.beans.dto.upload.BenchmarkUploadResponse;
import io.harness.assessment.settings.beans.dto.upload.BenchmarksUploadRequest;
import io.harness.assessment.settings.beans.entities.Assessment;
import io.harness.assessment.settings.beans.entities.Benchmark;
import io.harness.assessment.settings.beans.entities.Question;
import io.harness.assessment.settings.beans.entities.Score;
import io.harness.assessment.settings.beans.entities.ScoreType;
import io.harness.assessment.settings.mappers.BenchmarkMapper;
import io.harness.assessment.settings.mappers.BenchmarkUtils;
import io.harness.assessment.settings.repositories.AssessmentRepository;
import io.harness.assessment.settings.repositories.BenchmarkRepository;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;

@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class BenchmarkServiceImpl implements BenchmarkService {
  private BenchmarkRepository benchmarkRepository;
  private AssessmentRepository assessmentRepository;

  @Override
  public BenchmarkUploadResponse uploadBenchmark(BenchmarksUploadRequest benchmarksUploadRequest) {
    // TODO action needs to be taken based on if assessment and section level benchmark are always derived.
    List<AssessmentError> assessmentErrors = new ArrayList<>();
    String assessmentId = benchmarksUploadRequest.getAssessmentId();
    Long version = benchmarksUploadRequest.getMajorVersion();
    Optional<Assessment> assessmentOptional = assessmentRepository.findByAssessmentIdAndVersion(assessmentId, version);
    if (benchmarksUploadRequest.getType() != EntityType.BENCHMARK) {
      throw new RuntimeException("Type must be benchmark.");
    }
    if (assessmentOptional.isEmpty()) {
      throw new RuntimeException("Assessment Not Found for Id : " + assessmentId);
    }
    Assessment assessment = assessmentOptional.get();
    List<Benchmark> benchmarksInDB = benchmarkRepository.findAllByAssessmentIdAndVersion(assessmentId, version);
    Map<String, Boolean> defaultBenchmarkMap =
        CollectionUtils.emptyIfNull(benchmarksInDB)
            .stream()
            .collect(Collectors.toMap(Benchmark::getBenchmarkId, Benchmark::getIsDefault));
    List<BenchmarkDTO> benchmarks = benchmarksUploadRequest.getBenchmarks();
    benchmarks.forEach(benchmark -> defaultBenchmarkMap.put(benchmark.getBenchmarkId(), benchmark.getIsDefault()));
    if (defaultBenchmarkMap.values().stream().filter(x -> x).count() != 1) {
      assessmentErrors.add(AssessmentError.builder()
                               .entityType(EntityType.BENCHMARK)
                               .errorMessages(Collections.singletonList("Only one default is mandatory for a version."))
                               .build());
    }
    autoFill(assessment, benchmarks);
    validateBenchmarks(assessment, benchmarks, assessmentErrors);
    if (assessmentErrors.size() > 0) {
      return BenchmarkUploadResponse.builder()
          .assessmentId(assessmentId)
          .type(EntityType.BENCHMARK)
          .majorVersion(version)
          .errors(assessmentErrors)
          .build();
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
      // Should we only fill section and assessment level if its not supplied ?
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
    benchmarksInDB = benchmarkRepository.findAllByAssessmentIdAndVersion(assessmentId, version);
    List<BenchmarkDTO> benchmarkDTOList =
        benchmarksInDB.stream().map(BenchmarkMapper::toDTO).collect(Collectors.toList());
    return BenchmarkUploadResponse.builder()
        .assessmentId(assessmentId)
        .type(EntityType.BENCHMARK)
        .majorVersion(version)
        .benchmarks(filterOnlyQuestionLevel(benchmarkDTOList))
        .errors(assessmentErrors)
        .build();
  }

  private static void validateBenchmarks(
      Assessment assessment, List<BenchmarkDTO> benchmarks, List<AssessmentError> assessmentErrors) {
    BenchmarkUtils.checkUniqueBenchmarkIds(benchmarks, assessmentErrors, assessment.getAssessmentId());
    benchmarks.forEach(
        benchmarkDTO -> BenchmarkUtils.validateBenchmarkQuestions(benchmarkDTO, assessment, assessmentErrors));
  }

  private static void autoFill(Assessment assessment, List<BenchmarkDTO> benchmarks) {
    // set default as false
    benchmarks.stream()
        .filter(benchmarkDTO -> Objects.isNull(benchmarkDTO.getIsDefault()))
        .forEach(benchmarkDTO -> benchmarkDTO.setIsDefault(false));
    Map<String, Question> questionMap =
        assessment.getQuestions().stream().collect(Collectors.toMap(Question::getQuestionId, Function.identity()));
    // set max score same as question score.
    benchmarks.stream()
        .flatMap(benchmarkDTO -> benchmarkDTO.getScores().stream())
        .filter(scoreDTO -> Objects.isNull(scoreDTO.getMaxScore()))
        .forEach(scoreDTO -> scoreDTO.setMaxScore(questionMap.get(scoreDTO.getEntityId()).getMaxScore()));
  }

  @Override
  public BenchmarkUploadResponse getBenchmarks(String assessmentId, Long version) {
    List<Benchmark> benchmarksInDB = benchmarkRepository.findAllByAssessmentIdAndVersion(assessmentId, version);
    List<BenchmarkDTO> benchmarkDTOList =
        benchmarksInDB.stream().map(BenchmarkMapper::toDTO).collect(Collectors.toList());
    return BenchmarkUploadResponse.builder()
        .assessmentId(assessmentId)
        .type(EntityType.BENCHMARK)
        .majorVersion(version)
        .benchmarks(filterOnlyQuestionLevel(benchmarkDTOList))
        .build();
  }

  @Override
  public BenchmarkUploadResponse uploadNewBenchmarkYAML(InputStream uploadedInputStream) throws IOException {
    String fileAsString = IOUtils.toString(uploadedInputStream, Charset.defaultCharset());
    BenchmarksUploadRequest benchmarksUploadRequest =
        YamlPipelineUtils.read(fileAsString, BenchmarksUploadRequest.class);
    return uploadBenchmark(benchmarksUploadRequest);
  }

  List<BenchmarkDTO> filterOnlyQuestionLevel(List<BenchmarkDTO> benchmarkDTOList) {
    return benchmarkDTOList.stream()
        .peek(benchmarkDTO -> {
          List<ScoreDTO> filteredScores = benchmarkDTO.getScores()
                                              .stream()
                                              .filter(scoreDTO -> scoreDTO.getScoreType() == ScoreType.QUESTION_LEVEL)
                                              .collect(Collectors.toList());
          benchmarkDTO.setScores(filteredScores);
        })
        .collect(Collectors.toList());
  }
}
