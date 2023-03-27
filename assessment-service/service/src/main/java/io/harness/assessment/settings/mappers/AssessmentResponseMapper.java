/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.assessment.settings.beans.dto.AssessmentResultsResponse;
import io.harness.assessment.settings.beans.dto.OptionResponseWithSelection;
import io.harness.assessment.settings.beans.dto.UserResponsesResponse;
import io.harness.assessment.settings.beans.entities.AssessmentResponse;
import io.harness.assessment.settings.beans.entities.UserResponse;

import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.SEI)
@UtilityClass
public class AssessmentResponseMapper {
  public AssessmentResultsResponse toDTO(AssessmentResponse assessmentResponse) {
    return AssessmentResultsResponse.builder()
        .assessmentId(assessmentResponse.getAssessmentId())
        .responses(assessmentResponse.getResponses()
                       .stream()
                       .map(AssessmentResponseMapper::toUserResponseDTO)
                       .collect(Collectors.toList()))
        .userScores(assessmentResponse.getScores())
        .status(assessmentResponse.getStatus())
        .build();
  }

  public UserResponsesResponse toUserResponseDTO(UserResponse userResponse) {
    return UserResponsesResponse.builder()
        .questionId(userResponse.getQuestionId())
        .userScore(userResponse.getUserScore())
        .responses(userResponse.getResponses()
                       .stream()
                       .map(userResponseItem
                           -> OptionResponseWithSelection.builder()
                                  .optionId(userResponseItem.getOptionId())
                                  .isSelected(userResponseItem.isSelected())
                                  .build())
                       .collect(Collectors.toList()))
        .build();
  }
}
