/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.services;

import io.harness.assessment.settings.beans.dto.AssessmentResultsResponse;
import io.harness.assessment.settings.beans.dto.UserAssessmentDTO;
import io.harness.assessment.settings.beans.dto.UserResponseRequestItem;
import io.harness.assessment.settings.beans.dto.UserResponsesRequest;
import io.harness.assessment.settings.beans.entities.Assessment;
import io.harness.assessment.settings.beans.entities.AssessmentResponse;
import io.harness.assessment.settings.beans.entities.AssessmentResponseStatus;
import io.harness.assessment.settings.beans.entities.OrganizationEvaluation;
import io.harness.assessment.settings.beans.entities.Question;
import io.harness.assessment.settings.beans.entities.QuestionOption;
import io.harness.assessment.settings.beans.entities.Score;
import io.harness.assessment.settings.beans.entities.ScoreType;
import io.harness.assessment.settings.beans.entities.User;
import io.harness.assessment.settings.beans.entities.UserInvitation;
import io.harness.assessment.settings.beans.entities.UserResponse;
import io.harness.assessment.settings.beans.entities.UserResponseItem;
import io.harness.assessment.settings.mappers.AssessmentMapper;
import io.harness.assessment.settings.mappers.AssessmentResponseMapper;
import io.harness.assessment.settings.repositories.AssessmentRepository;
import io.harness.assessment.settings.repositories.AssessmentResponseRepository;
import io.harness.assessment.settings.repositories.OrganizationEvaluationRepository;
import io.harness.assessment.settings.repositories.UserInvitationRepository;
import io.harness.assessment.settings.repositories.UserRepository;

import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.BadRequestException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.jetbrains.annotations.NotNull;

@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class AssessmentEvaluationServiceImpl implements AssessmentEvaluationService {
  private AssessmentRepository assessmentRepository;
  private UserRepository userRepository;

  private UserInvitationRepository userInvitationRepository;
  private AssessmentResponseRepository assessmentResponseRepository;

  private OrganizationEvaluationRepository organizationEvaluationRepository;
  @Override
  public AssessmentResultsResponse submitAssessmentResponse(UserResponsesRequest userResponseRequest, String token) {
    // check if this user has already an entry for this assessment, then update it.
    // A first time partial TODO
    // TODO also remove extra data
    Optional<UserInvitation> userInvitationOptional = userInvitationRepository.findOneByGeneratedCode(token);
    if (userInvitationOptional.isEmpty()) {
      throw new RuntimeException("Token error");
    }
    UserInvitation userInvitation = userInvitationOptional.get();
    Optional<Assessment> assessmentOptional =
        assessmentRepository.findFirstByAssessmentIdOrderByVersionDesc(userInvitation.getAssessmentId());

    Optional<User> userOptional = userRepository.findOneByUserId(userInvitation.getUserId());
    if (userOptional.isEmpty() || assessmentOptional.isEmpty()) {
      throw new RuntimeException("User error");
    }
    Assessment assessment = assessmentOptional.get();
    User user = userOptional.get();
    // check all responses are there
    List<UserResponseRequestItem> userResponses = userResponseRequest.getResponses();
    Map<String, List<String>> userResponseQuestionMap = userResponses.stream().collect(
        Collectors.toMap(UserResponseRequestItem::getQuestionId, UserResponseRequestItem::getResponseIds));
    // compare the two sets
    validateQuestionResponses(assessment, userResponses, userResponseQuestionMap);
    Optional<AssessmentResponse> previouslySubmittedResponse =
        assessmentResponseRepository.findOneByAssessmentIdAndUserIdAndVersion(
            assessment.getAssessmentId(), user.getUserId(), assessment.getVersion());
    String id = null;
    if (previouslySubmittedResponse.isPresent()) {
      id = previouslySubmittedResponse.get().getId();
      if (previouslySubmittedResponse.get().getStatus() == AssessmentResponseStatus.COMPLETED) {
        throw new RuntimeException("Assessment is already completed for this version");
      }
    }
    // only done for same version now
    /*    List<UserResponse> userResponseEntity = userResponses.stream()
                .map(userResponseRequestItem
                        -> UserResponse.builder()
                        .questionId(userResponseRequestItem.getQuestionId())
                        .responseIds(userResponseRequestItem.getResponseIds())
                        .score(0d)
                        .build())
                .collect(Collectors.toList());*/
    List<Score> userScores = calculateScores(assessment, userResponseQuestionMap);
    // This fixes the correct ordering also.
    List<UserResponse> userResponseEntity = getUserResponses(assessment, userResponseQuestionMap, userScores);
    AssessmentResponse assessmentResponse = AssessmentResponse.builder()
                                                .id(id)
                                                .assessmentId(assessment.getAssessmentId())
                                                .version(assessment.getVersion())
                                                .userId(user.getUserId())
                                                .organizationId(user.getOrganizationId())
                                                .responses(userResponseEntity)
                                                .role(userResponseRequest.getRole())
                                                .status(AssessmentResponseStatus.COMPLETED)
                                                .scores(userScores)
                                                .build();
    assessmentResponse = assessmentResponseRepository.save(assessmentResponse);
    updateOrganizationScore(assessment, userScores, user);
    Optional<AssessmentResponse> assessmentResponseInDbOptional =
        assessmentResponseRepository.findOneByAssessmentIdAndUserIdAndVersion(
            assessmentResponse.getAssessmentId(), user.getUserId(), assessment.getVersion());
    if (assessmentResponseInDbOptional.isEmpty()) {
      throw new RuntimeException("error");
    }
    AssessmentResponse assessmentResponseInDb = assessmentResponseInDbOptional.get();
    try {
      String resultLink = TokenGenerationUtil.generateResultLinkFromEmail(
          assessmentResponseInDb.getUserId(), assessmentResponseInDb.getId());
      assessmentResponseInDb.setResultLink(resultLink);
      assessmentResponseRepository.save(assessmentResponseInDb);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    // TODO actual evaluation logic.

    // TODO org score calculations
    return AssessmentResponseMapper.toDTO(assessmentResponseInDb);
  }

  private static List<UserResponse> getUserResponses(
      Assessment assessment, Map<String, List<String>> userResponseQuestionMap, List<Score> userScores) {
    Map<String, Question> questionMap =
        assessment.getQuestions().stream().collect(Collectors.toMap(Question::getQuestionId, Function.identity()));
    return userScores.stream()
        .filter(score -> score.getScoreType() == ScoreType.QUESTION_LEVEL)
        .map(score -> {
          String questionId = score.getEntityId();
          List<String> responsesIds = userResponseQuestionMap.get(questionId);
          Question question = questionMap.get(questionId);
          return UserResponse.builder()
              .questionId(questionId)
              .sectionId(question.getSectionId())
              .responses(question.getPossibleResponses()
                             .stream()
                             .map(QuestionOption::getOptionId)
                             .map(optionId
                                 -> UserResponseItem.builder()
                                        .optionId(optionId)
                                        .isSelected(responsesIds.contains(optionId))
                                        .build())
                             .collect(Collectors.toList()))
              .userScore(score.getScore())
              .maxScore(question.getMaxScore())
              .build();
        })
        .collect(Collectors.toList());
  }

  private static void validateQuestionResponses(Assessment assessment, List<UserResponseRequestItem> userResponses,
      Map<String, List<String>> userResponseQuestionMap) {
    Set<String> setOfQuestionInDb =
        assessment.getQuestions().stream().map(Question::getQuestionId).collect(Collectors.toSet());
    Set<String> setOfQuestionInResponse = userResponseQuestionMap.keySet();

    if (!setOfQuestionInResponse.equals(setOfQuestionInDb)) {
      throw new RuntimeException(
          "Assessment is incomplete : " + SetUtils.difference(setOfQuestionInDb, setOfQuestionInResponse)
          + " incorrect responses : " + SetUtils.difference(setOfQuestionInResponse, setOfQuestionInDb));
    }
    userResponses.forEach(userResponse -> {
      Set<String> responseFromUser = new HashSet<>(userResponse.getResponseIds());
      Set<String> responseOptions = new HashSet<>(userResponseQuestionMap.get(userResponse.getQuestionId()));
      if (!responseOptions.containsAll(responseFromUser)) {
        throw new RuntimeException("Invalid Option specified for question : " + userResponse.getQuestionId());
      }
    });
  }

  private void updateOrganizationScore(Assessment assessment, List<Score> userScores, User user) {
    Optional<OrganizationEvaluation> organizationEvaluationOptional =
        organizationEvaluationRepository.findOneByAssessmentIdAndOrganizationIdAndVersion(
            assessment.getAssessmentId(), user.getOrganizationId(), assessment.getVersion());
    if (organizationEvaluationOptional.isEmpty()) {
      organizationEvaluationRepository.save(OrganizationEvaluation.builder()
                                                .organizationId(user.getOrganizationId())
                                                .assessmentId(assessment.getAssessmentId())
                                                .version(assessment.getVersion())
                                                .scores(userScores)
                                                .numOfResponses(1L)
                                                .build());
    } else {
      OrganizationEvaluation organizationEvaluation = organizationEvaluationOptional.get();
      Long currentNumOfResponse = organizationEvaluation.getNumOfResponses();
      long updatedNumOfResponses = currentNumOfResponse + 1;
      organizationEvaluation.setNumOfResponses(updatedNumOfResponses);
      Map<String, Score> currentOrgScores = organizationEvaluation.getScores().stream().collect(
          Collectors.toMap(Score::getEntityId, Function.identity()));
      for (Score userScore : userScores) {
        Score score = currentOrgScores.get(userScore.getEntityId());
        Double currentOrgScoreMean = score.getScore();
        double updatedOrgScore =
            (userScore.getScore() + (currentNumOfResponse * currentOrgScoreMean)) / updatedNumOfResponses;
        BigDecimal bd = new BigDecimal(updatedOrgScore).setScale(2, RoundingMode.HALF_UP);
        double roundedScore = bd.doubleValue();
        score.setScore(roundedScore);
      }
      // 3 type of changes question level , section level org level
      organizationEvaluationRepository.save(organizationEvaluation);
      // use weighted mean TODO
      // calculation across version TODO
    }
  }

  @NotNull
  private static List<Score> calculateScores(Assessment assessment, Map<String, List<String>> userResponseMap) {
    List<Score> UserScores = new ArrayList<>();
    // ensure correct order of UserScores
    Map<String, MutablePair<Double, Long>> sectionTotalScoresMap = new HashMap<>();
    double assessmentTotalScore = 0d;
    Long assessmentMaxScore = 0L;
    for (Question question : assessment.getQuestions()) {
      String questionId = question.getQuestionId();
      Map<String, Long> questionScoreMap = question.getPossibleResponses().stream().collect(
          Collectors.toMap(QuestionOption::getOptionId, QuestionOption::getOptionPoints));
      List<String> responseIds = userResponseMap.get(questionId);
      Double totalQuestionScore = responseIds.stream().mapToDouble(questionScoreMap::get).sum();
      Score userQuestionScore = Score.builder()
                                    .entityId(questionId)
                                    .scoreType(ScoreType.QUESTION_LEVEL)
                                    .score(totalQuestionScore)
                                    .maxScore(question.getMaxScore())
                                    .build();
      UserScores.add(userQuestionScore);
      MutablePair<Double, Long> scoreMaxScoreTuple =
          sectionTotalScoresMap.getOrDefault(question.getSectionId(), new MutablePair<>(0d, 0L));
      double sectionScore = scoreMaxScoreTuple.left + totalQuestionScore;
      Long sectionMaxScore = scoreMaxScoreTuple.right + question.getMaxScore();
      sectionTotalScoresMap.put(question.getSectionId(), new MutablePair<>(sectionScore, sectionMaxScore));
      assessmentTotalScore += totalQuestionScore;
      assessmentMaxScore += question.getMaxScore();
    }
    for (Map.Entry<String, MutablePair<Double, Long>> sectionScore : sectionTotalScoresMap.entrySet()) {
      Score userSectionScore = Score.builder()
                                   .entityId(sectionScore.getKey())
                                   .scoreType(ScoreType.SECTION_LEVEL)
                                   .score(sectionScore.getValue().left)
                                   .maxScore(sectionScore.getValue().right)
                                   .build();
      UserScores.add(userSectionScore);
    }
    Score userAssessmentScore = Score.builder()
                                    .entityId(assessment.getAssessmentId())
                                    .scoreType(ScoreType.ASSESSMENT_LEVEL)
                                    .score(assessmentTotalScore)
                                    .maxScore(assessmentMaxScore)
                                    .build();
    UserScores.add(userAssessmentScore);
    return UserScores;
  }

  @Override
  public UserAssessmentDTO saveAssessmentResponse(UserResponsesRequest userResponsesRequest, String token) {
    // check if this user has already a entry for this assessment, then update it.
    Optional<UserInvitation> userInvitationOptional = userInvitationRepository.findOneByGeneratedCode(token);
    if (userInvitationOptional.isEmpty()) {
      throw new RuntimeException("Token error");
    }
    UserInvitation userInvitation = userInvitationOptional.get();
    Optional<User> userOptional = userRepository.findOneByUserId(userInvitation.getUserId());
    // A first time partial TODO
    Optional<Assessment> assessmentOptional =
        assessmentRepository.findFirstByAssessmentIdOrderByVersionDesc(userInvitation.getAssessmentId());
    if (userOptional.isEmpty() || assessmentOptional.isEmpty()) {
      throw new RuntimeException("User token error");
    }
    Assessment assessment = assessmentOptional.get();
    User user = userOptional.get();
    Optional<AssessmentResponse> previouslySubmittedResponse =
        assessmentResponseRepository.findOneByAssessmentIdAndUserIdAndVersion(
            assessment.getAssessmentId(), user.getUserId(), assessment.getVersion());
    if (previouslySubmittedResponse.isPresent()
        && previouslySubmittedResponse.get().getStatus() == AssessmentResponseStatus.COMPLETED) {
      throw new RuntimeException("Assessment is already completed for this version");
    }
    // TODO fix below code.
    List<UserResponse> userResponseEntity =
        userResponsesRequest.getResponses()
            .stream()
            .map(userResponseRequestItem
                -> UserResponse.builder()
                       .questionId(userResponseRequestItem.getQuestionId())
                       //                                                           .responses()
                       .build())
            .collect(Collectors.toList());
    AssessmentResponse assessmentResponse = AssessmentResponse.builder()
                                                .assessmentId(userInvitation.getAssessmentId())
                                                .version(assessment.getVersion())
                                                .userId(user.getId())
                                                .organizationId(user.getOrganizationId())
                                                .responses(userResponseEntity)
                                                .role(userResponsesRequest.getRole())
                                                .status(AssessmentResponseStatus.ONGOING)
                                                .build();
    assessmentResponseRepository.save(assessmentResponse);
    UserAssessmentDTO userAssessmentDTO = AssessmentMapper.toDTO(assessment);
    // should fetch user response from db. TODO
    //    userAssessmentDTO.setUserResponse(Optional.of(userResponsesRequest));
    return userAssessmentDTO;
  }

  @Override
  public UserAssessmentDTO getAssessmentForUser(String assessmentInviteId) {
    // can be filled partially before, if then fetch.
    Optional<UserInvitation> userInvitationOptional =
        userInvitationRepository.findOneByGeneratedCode(assessmentInviteId);
    if (userInvitationOptional.isEmpty()) {
      throw new RuntimeException("User token error");
    }
    UserInvitation userInvitation = userInvitationOptional.get();
    Optional<Assessment> assessmentOptional =
        assessmentRepository.findFirstByAssessmentIdOrderByVersionDesc(userInvitation.getAssessmentId());
    // Check it should be in published state.
    // check token is valid. TODO
    if (assessmentOptional.isEmpty()) {
      throw new BadRequestException("Assessment not found, for Id : " + assessmentInviteId);
    }
    log.info("{}", assessmentOptional);
    Assessment assessment = assessmentOptional.get();
    Optional<AssessmentResponse> assessmentResponseInDbOptional =
        assessmentResponseRepository.findOneByAssessmentIdAndUserIdAndVersion(
            assessment.getAssessmentId(), userInvitation.getUserId(), assessment.getVersion());
    UserAssessmentDTO userAssessmentDTO = AssessmentMapper.toDTO(assessment);
    if (assessmentResponseInDbOptional.isPresent()) {
      AssessmentResponse assessmentResponse = assessmentResponseInDbOptional.get();
      userAssessmentDTO.setStatus(assessmentResponse.getStatus());
      userAssessmentDTO.setResultLink(assessmentResponse.getResultLink());
      userAssessmentDTO.setUserResponse(assessmentResponse.getResponses()
                                            .stream()
                                            .map(userResponse
                                                -> UserResponseRequestItem.builder()
                                                       .questionId(userResponse.getQuestionId())
                                                       .responseIds(userResponse.getResponses()
                                                                        .stream()
                                                                        .filter(UserResponseItem::isSelected)
                                                                        .map(UserResponseItem::getOptionId)
                                                                        .collect(Collectors.toList()))
                                                       .build())
                                            .collect(Collectors.toList()));
    } else {
      userAssessmentDTO.setStatus(AssessmentResponseStatus.NOT_STARTED);
    }
    return userAssessmentDTO;
  }
}
