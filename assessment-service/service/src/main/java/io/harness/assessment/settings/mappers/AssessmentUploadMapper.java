/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadRequest;
import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadResponse;
import io.harness.assessment.settings.beans.dto.upload.UploadedOption;
import io.harness.assessment.settings.beans.dto.upload.UploadedQuestion;
import io.harness.assessment.settings.beans.entities.Assessment;
import io.harness.assessment.settings.beans.entities.Question;
import io.harness.assessment.settings.beans.entities.QuestionOption;

import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.SEI)
@UtilityClass
public class AssessmentUploadMapper {
  public AssessmentUploadResponse toDTO(Assessment assessment) {
    return AssessmentUploadResponse.builder()
        .assessmentId(assessment.getAssessmentId())
        .assessmentName(assessment.getAssessmentName())
        .createdAt(assessment.getCreatedAt())
        .createdBy(assessment.getCreatedBy())
        .expectedCompletionDuration(assessment.getExpectedCompletionDuration())
        .questions(assessment.getQuestions())
        .baseScore(assessment.getBaseScore())
        .isPublished(assessment.getIsPublished())
        .majorVersion(assessment.getVersion())
        .minorVersion(assessment.getMinorVersion())
        .lastUpdatedAt(assessment.getLastUpdatedAt())
        .build();
  }

  public Assessment fromDTO(AssessmentUploadRequest assessmentUploadRequest) {
    return Assessment.builder()
        .assessmentId(assessmentUploadRequest.getAssessmentId())
        .assessmentName(assessmentUploadRequest.getAssessmentName())
        .questions(assessmentUploadRequest.getQuestions()
                       .stream()
                       .map(AssessmentUploadMapper::fromQuestionDTO)
                       .collect(Collectors.toList()))
        .expectedCompletionDuration(assessmentUploadRequest.getExpectedCompletionDuration())
        .build();
  }

  public Question fromQuestionDTO(UploadedQuestion uploadedQuestion) {
    return Question.builder()
        .questionId(uploadedQuestion.getQuestionId())
        .questionNumber(uploadedQuestion.getQuestionNumber())
        .questionText(uploadedQuestion.getQuestionText())
        .questionType(uploadedQuestion.getQuestionType())
        .possibleResponses(uploadedQuestion.getPossibleResponses()
                               .stream()
                               .map(AssessmentUploadMapper::fromOptionDTO)
                               .collect(Collectors.toList()))
        .sectionId(uploadedQuestion.getSectionId())
        .sectionName(uploadedQuestion.getSectionName())
        .build();
  }

  public QuestionOption fromOptionDTO(UploadedOption uploadedOption) {
    return QuestionOption.builder()
        .optionId(uploadedOption.getOptionId())
        .optionPoints(uploadedOption.getOptionPoints())
        .optionText(uploadedOption.getOptionText())
        .build();
  }
}
