/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.assessment.settings.beans.dto.OptionResponse;
import io.harness.assessment.settings.beans.dto.QuestionResponse;
import io.harness.assessment.settings.beans.dto.UserAssessmentDTO;
import io.harness.assessment.settings.beans.entities.Assessment;

import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.SEI)
@UtilityClass
public class AssessmentMapper {
  public UserAssessmentDTO toDTO(Assessment assessment) {
    return UserAssessmentDTO.builder()
        .assessmentId(assessment.getAssessmentId())
        .assessmentName(assessment.getAssessmentName())
        .expectedCompletionDuration(assessment.getExpectedCompletionDuration())
        .questions(assessment.getQuestions()
                       .stream()
                       .map(question
                           -> QuestionResponse.builder()
                                  .questionId(question.getQuestionId())
                                  .questionText(question.getQuestionText())
                                  .questionNumber(question.getQuestionNumber())
                                  .questionType(question.getQuestionType())
                                  .sectionId(question.getSectionId())
                                  .sectionName(question.getSectionName())
                                  .possibleResponses(question.getPossibleResponses()
                                                         .stream()
                                                         .map(option
                                                             -> OptionResponse.builder()
                                                                    .optionId(option.getOptionId())
                                                                    .optionText(option.getOptionText())
                                                                    .build())
                                                         .collect(Collectors.toList()))
                                  .build())
                       .collect(Collectors.toList()))
        .baseScore(assessment.getBaseScore())
        .majorVersion(assessment.getVersion())
        .minorVersion(assessment.getMinorVersion())
        .build();
  }
}
