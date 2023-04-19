/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.assessment.settings.beans.dto.upload.AssessmentError;
import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadRequest;
import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadResponse;
import io.harness.assessment.settings.beans.entities.Assessment;
import io.harness.assessment.settings.beans.entities.Question;
import io.harness.assessment.settings.beans.entities.QuestionOption;
import io.harness.assessment.settings.mappers.AssessmentUploadMapper;
import io.harness.assessment.settings.mappers.QuestionUtils;
import io.harness.assessment.settings.repositories.AssessmentRepository;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.BadRequestException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.SEI)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class AssessmentUploadServiceImpl implements AssessmentUploadService {
  public static final long QUESTION_SCORE = 10L;
  public static final long QUESTION_SCORE_WEIGHTAGE = 1L;
  public static final String DEFAULT_SECTION = "DEFAULT_SECTION_ID";
  private AssessmentRepository assessmentRepository;
  @Override
  public AssessmentUploadResponse uploadNewAssessment(AssessmentUploadRequest assessmentUploadRequest) {
    // TODO all the entity Id has to be unique in context of a assessment.: assessment,question,section
    // TODO validation by question types
    Optional<Assessment> assessmentOptional =
        assessmentRepository.findFirstByAssessmentIdOrderByVersionDesc(assessmentUploadRequest.getAssessmentId());
    if (assessmentOptional.isPresent()) {
      throw new BadRequestException("Assessment already exists, for Id : " + assessmentUploadRequest.getAssessmentId());
    }
    Assessment assessmentUploaded = AssessmentUploadMapper.fromDTO(assessmentUploadRequest);
    assessmentUploadRequest.getQuestions()
        .stream()
        .filter(uploadedQuestion -> StringUtils.isEmpty(uploadedQuestion.getSectionId()))
        .forEach(uploadedQuestion -> uploadedQuestion.setSectionId(DEFAULT_SECTION));
    List<AssessmentError> errorsList = validateAssessment(assessmentUploaded);
    if (errorsList.size() > 0) {
      return AssessmentUploadResponse.builder().errors(errorsList).build();
    }
    assessmentUploaded.setVersion(1L);
    assessmentUploaded.setMinorVersion(0L);
    int numOfQuestions = assessmentUploadRequest.getQuestions().size();
    assessmentUploaded.setBaseScore(numOfQuestions * QUESTION_SCORE); // calculate
    assessmentUploaded.getQuestions().forEach(question -> {
      question.setScoreWeightage(QUESTION_SCORE_WEIGHTAGE);
      question.setMaxScore(QUESTION_SCORE);
    });
    assessmentUploaded.setIsPublished(false);
    assessmentUploaded.setCreatedBy("System"); // TODO implement some form of basic auth
    assessmentRepository.save(assessmentUploaded);
    return AssessmentUploadMapper.toDTO(assessmentUploaded);
  }

  List<AssessmentError> validateAssessment(Assessment assessment) {
    List<AssessmentError> errors = new ArrayList<>();
    // add option id checks and section id checks TODO
    QuestionUtils.checkUniqueQuestion(assessment, errors);
    for (Question question : assessment.getQuestions()) {
      QuestionUtils.validateQuestionType(question, errors);
      QuestionUtils.checkDuplicateOptions(question, errors);
    }
    return errors;
  }
  @Override
  public AssessmentUploadResponse updateAssessment(AssessmentUploadRequest assessmentUploadRequest) {
    // TODO check if its a allowed update.
    // TODO Change of question type is also new assessment.
    log.info("Updating assessment with request: {}", assessmentUploadRequest);
    String assessmentId = assessmentUploadRequest.getAssessmentId();
    Assessment assessmentUploaded = AssessmentUploadMapper.fromDTO(assessmentUploadRequest);
    List<AssessmentError> errorsList = validateAssessment(assessmentUploaded);
    if (errorsList.size() > 0) {
      return AssessmentUploadResponse.builder().errors(errorsList).build();
    }
    Optional<Assessment> assessmentOptional =
        assessmentRepository.findFirstByAssessmentIdOrderByVersionDesc(assessmentId);
    if (assessmentOptional.isEmpty()) {
      throw new BadRequestException("Assessment not found, for Id : " + assessmentId);
    }
    Assessment assessmentInDb = assessmentOptional.get();
    boolean areTheyEqual = compareAssessmentsDiff(assessmentInDb, assessmentUploaded);

    if (!areTheyEqual) {
      assessmentUploaded.setVersion(assessmentInDb.getVersion() + 1);
      assessmentUploaded.setMinorVersion(0L);
      log.info("updating major version for assessment: {}", assessmentUploadRequest.getAssessmentId());
      // do other things TODO
    } else {
      assessmentUploaded.setVersion(assessmentInDb.getVersion());
      assessmentUploaded.setMinorVersion(assessmentInDb.getMinorVersion() + 1);
      assessmentUploaded.setId(assessmentInDb.getId()); // TODO a good update ?
      assessmentUploaded.setCreatedAt(assessmentInDb.getCreatedAt());
      // this should be updated to old, not new version
      log.info("updating minor version for assessment: {}", assessmentUploadRequest.getAssessmentId());
    }
    assessmentUploaded.setBaseScore(assessmentInDb.getBaseScore()); // calculate
    assessmentUploaded.getQuestions().forEach(question -> {
      question.setScoreWeightage(QUESTION_SCORE_WEIGHTAGE); // change this TODO
      question.setMaxScore(QUESTION_SCORE);
    });
    assessmentUploaded.setIsPublished(false);
    assessmentUploaded.setCreatedBy("System"); // TODO Update with new token
    return AssessmentUploadMapper.toDTO(assessmentRepository.saveOrUpdate(assessmentUploaded));
    // Algorithm to find update without version change
    // check all question Ids are present
  }

  private static boolean compareAssessmentsDiff(Assessment assessmentInDb, Assessment assessmentUploaded) {
    LinkedHashMap<String, List<QuestionOption>> questionsInDBMap = new LinkedHashMap<>();
    assessmentInDb.getQuestions().forEach(question -> {
      String questionKey = getQuestionKey(question);
      questionsInDBMap.put(questionKey, question.getPossibleResponses());
    });
    boolean areTheyEqual = true;
    if (assessmentUploaded.getQuestions().size() != questionsInDBMap.size()) {
      areTheyEqual = false;
    } else {
      // check questions ids & sectionIs with order and ids
      List<String> questionsUploaded = assessmentUploaded.getQuestions()
                                           .stream()
                                           .map(AssessmentUploadServiceImpl::getQuestionKey)
                                           .collect(Collectors.toList());
      if (!questionsUploaded.equals(new ArrayList<>(questionsInDBMap.keySet()))) {
        // TODO this might be wrong , verify.
        areTheyEqual = false;
      }
      // Options and points compare
      for (Question question : assessmentUploaded.getQuestions()) {
        String questionKey = getQuestionKey(question);
        List<QuestionOption> optionsInDB = questionsInDBMap.get(questionKey); // this must be found
        List<QuestionOption> optionsInRequest = question.getPossibleResponses();
        // TODO skip text check,only compare ids and points
        if (!optionsInRequest.equals(optionsInDB)) {
          areTheyEqual = false;
        }
      }
    }
    return areTheyEqual;
  }

  @NotNull
  private static String getQuestionKey(Question question) {
    return question.getQuestionId() + "_" + question.getSectionId();
  }

  @Override
  public AssessmentUploadResponse publishAssessment(String assessmentId) {
    Optional<Assessment> assessmentOptional =
        assessmentRepository.findFirstByAssessmentIdOrderByVersionDesc(assessmentId);
    Assessment assessment;
    if (assessmentOptional.isPresent()) {
      assessment = assessmentOptional.get();
      assessment.setIsPublished(true);
      assessmentRepository.saveOrUpdate(assessment);
    } else {
      throw new BadRequestException("Assessment not found, for Id : " + assessmentId);
    }
    log.info("{}", assessment);
    return AssessmentUploadMapper.toDTO(assessment);
  }

  @Override
  public AssessmentUploadResponse getAssessment(String assessmentId) {
    Optional<Assessment> assessmentOptional =
        assessmentRepository.findFirstByAssessmentIdOrderByVersionDesc(assessmentId);
    if (assessmentOptional.isEmpty()) {
      throw new BadRequestException("Assessment not found, for Id : " + assessmentId);
    }
    log.info("{}", assessmentOptional.get());
    return AssessmentUploadMapper.toDTO(assessmentOptional.get());
  }
}
