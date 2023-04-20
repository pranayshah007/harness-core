/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.assessment.settings.beans.dto.EntityType;
import io.harness.assessment.settings.beans.dto.upload.AssessmentError;
import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadRequest;
import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadResponse;
import io.harness.assessment.settings.beans.entities.Assessment;
import io.harness.assessment.settings.beans.entities.Question;
import io.harness.assessment.settings.beans.entities.QuestionOption;
import io.harness.assessment.settings.beans.entities.QuestionType;
import io.harness.assessment.settings.mappers.AssessmentUploadMapper;
import io.harness.assessment.settings.mappers.QuestionUtils;
import io.harness.assessment.settings.repositories.AssessmentRepository;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.BadRequestException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
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
    //  TODO all the entity Id has to be unique in context of a assessment.: assessment,question,section
    //  TODO validation by question types
    if (assessmentUploadRequest.getType() != EntityType.ASSESSMENT) {
      throw new RuntimeException("Type must be assessment.");
    }
    Optional<Assessment> assessmentOptional =
        assessmentRepository.findFirstByAssessmentIdOrderByVersionDesc(assessmentUploadRequest.getAssessmentId());
    Assessment assessmentInDb = null;
    if (assessmentOptional.isPresent()) {
      assessmentInDb = assessmentOptional.get();
    }
    Assessment assessmentUploaded = AssessmentUploadMapper.fromDTO(assessmentUploadRequest);
    autofillQuestions(assessmentUploaded);
    List<AssessmentError> errorsList = validateAssessment(assessmentUploaded);
    if (errorsList.size() > 0) {
      return AssessmentUploadResponse.builder().errors(errorsList).build();
    }
    boolean areTheyEqual = compareAssessmentsDiff(assessmentInDb, assessmentUploaded);
    if (!areTheyEqual) {
      long version = assessmentOptional.isPresent() ? assessmentInDb.getVersion() : 0;
      assessmentUploaded.setVersion(version + 1);
      assessmentUploaded.setMinorVersion(0L);
      assessmentUploaded.setIsPublished(false);
      log.info("Adding new major version for assessment: {}", assessmentUploadRequest.getAssessmentId());
      // do other things TODO
    } else {
      assessmentUploaded.setVersion(assessmentInDb.getVersion());
      assessmentUploaded.setMinorVersion(assessmentInDb.getMinorVersion() + 1);
      assessmentUploaded.setId(assessmentInDb.getId());
      assessmentUploaded.setCreatedAt(assessmentInDb.getCreatedAt());
      assessmentUploaded.setIsPublished(assessmentInDb.getIsPublished());
      // this should be updated to old, not new version
      log.info("updating minor version for assessment: {}", assessmentUploadRequest.getAssessmentId());
    }
    int numOfQuestions = assessmentUploadRequest.getQuestions().size();
    assessmentUploaded.setBaseScore(numOfQuestions * QUESTION_SCORE); // calculate
    assessmentUploaded.getQuestions().forEach(question -> {
      question.setScoreWeightage(QUESTION_SCORE_WEIGHTAGE);
      question.setMaxScore(QUESTION_SCORE);
    });
    assessmentUploaded.setCreatedBy("System"); // TODO implement some form of basic auth
    assessmentRepository.save(assessmentUploaded);
    return AssessmentUploadMapper.toDTO(assessmentUploaded);
  }

  private void autofillQuestions(Assessment assessmentUploaded) {
    assessmentUploaded.getQuestions()
        .stream()
        .filter(uploadedQuestion -> StringUtils.isEmpty(uploadedQuestion.getSectionId()))
        .forEach(uploadedQuestion -> uploadedQuestion.setSectionId(DEFAULT_SECTION));
    // TODO Fix auto fill code.
    for (Question question : assessmentUploaded.getQuestions()) {
      try {
        long assignedOptionsCount = question.getPossibleResponses()
                                        .stream()
                                        .filter(option -> Objects.nonNull(option.getOptionPoints()))
                                        .count();
        if (assignedOptionsCount == 0) {
          QuestionType questionType = question.getQuestionType();
          int totalOptionsCount = question.getPossibleResponses().size();
          switch (questionType) {
            case RATING:
            case LIKERT:
              if (totalOptionsCount == 5) {
                question.getPossibleResponses().get(0).setOptionPoints(2L);
                question.getPossibleResponses().get(1).setOptionPoints(4L);
                question.getPossibleResponses().get(2).setOptionPoints(6L);
                question.getPossibleResponses().get(3).setOptionPoints(8L);
                question.getPossibleResponses().get(4).setOptionPoints(10L);
              }
              break;
            case CHECKBOX:
              if (QUESTION_SCORE % totalOptionsCount == 0) {
                question.getPossibleResponses().forEach(
                    questionOption -> questionOption.setOptionPoints(QUESTION_SCORE / totalOptionsCount));
              }
              break;
            case RADIO_BUTTON:
              break;
            case YES_NO:
              question.getPossibleResponses().get(0).setOptionPoints(QUESTION_SCORE);
              question.getPossibleResponses().get(1).setOptionPoints(0L);
              break;
          }
        }
      } catch (Exception e) {
        log.error("Could not assign points for question {}", question.getQuestionId());
      }
    }
  }

  List<AssessmentError> validateAssessment(Assessment assessment) {
    List<AssessmentError> errors = new ArrayList<>();
    // add option id checks and section id checks TODO
    QuestionUtils.checkUniqueQuestion(assessment, errors);
    for (Question question : assessment.getQuestions()) {
      QuestionUtils.checkMissingOptionPoints(question, errors);
      QuestionUtils.validateQuestionType(question, errors);
      QuestionUtils.checkDuplicateOptions(question, errors);
    }
    return errors;
  }

  private static boolean compareAssessmentsDiff(Assessment assessmentInDb, Assessment assessmentUploaded) {
    if (assessmentInDb == null) {
      return false;
    }
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

  @Override
  public AssessmentUploadResponse uploadNewAssessmentYAML(InputStream uploadedInputStream) throws IOException {
    String fileAsString = IOUtils.toString(uploadedInputStream, Charset.defaultCharset());
    AssessmentUploadRequest assessmentUploadRequest =
        YamlPipelineUtils.read(fileAsString, AssessmentUploadRequest.class);
    return uploadNewAssessment(assessmentUploadRequest);
  }
}
