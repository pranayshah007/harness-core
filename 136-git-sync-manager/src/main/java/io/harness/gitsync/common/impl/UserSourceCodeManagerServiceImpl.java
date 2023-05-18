/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.ErrorDetails;
import io.harness.gitsync.GitAccessRequest;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc;
import io.harness.gitsync.UserDetailsRequest;
import io.harness.gitsync.common.beans.UserSourceCodeManager;
import io.harness.gitsync.common.beans.UserSourceCodeManager.UserSourceCodeManagerKeys;
import io.harness.gitsync.common.dtos.UserDetailsResponseDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.gitsync.common.helper.GitSyncGrpcClientUtils;
import io.harness.gitsync.common.helper.SCMMapperHelper;
import io.harness.gitsync.common.helper.ScmErrorHandler;
import io.harness.gitsync.common.mappers.GitAccessRequestMapper;
import io.harness.gitsync.common.service.UserSourceCodeManagerService;
import io.harness.gitsync.scm.beans.ScmErrorDetails;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.repositories.userSourceCodeManager.UserSourceCodeManagerRepository;
import io.harness.security.PrincipalProtoMapper;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PIPELINE)
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class UserSourceCodeManagerServiceImpl implements UserSourceCodeManagerService {
  @Inject private UserSourceCodeManagerRepository userSourceCodeManagerRepository;
  @Inject private SCMMapperHelper scmMapperHelper;
  @Inject
  private HarnessToGitPushInfoServiceGrpc
      .HarnessToGitPushInfoServiceBlockingStub harnessToGitPushInfoServiceBlockingStub;

  @Override
  public UserSourceCodeManagerDTO getByType(String accountIdentifier, String userIdentifier, SCMType type) {
    UserSourceCodeManager userSourceCodeManager =
        userSourceCodeManagerRepository.findByAccountIdentifierAndUserIdentifierAndType(
            accountIdentifier, userIdentifier, type);
    if (userSourceCodeManager == null) {
      return null;
    }
    return scmMapperHelper.toDTO(userSourceCodeManager);
  }

  @Override
  public List<UserSourceCodeManagerDTO> get(String accountIdentifier, String userIdentifier) {
    List<UserSourceCodeManagerDTO> userSourceCodeManagerDTOs = new ArrayList<>();
    List<UserSourceCodeManager> userSourceCodeManagerList;
    userSourceCodeManagerList =
        userSourceCodeManagerRepository.findByAccountIdentifierAndUserIdentifier(accountIdentifier, userIdentifier);
    userSourceCodeManagerList.forEach(scm -> userSourceCodeManagerDTOs.add(scmMapperHelper.toDTO(scm)));
    return userSourceCodeManagerDTOs;
  }

  @Override
  public UserSourceCodeManagerDTO save(UserSourceCodeManagerDTO userSourceCodeManager) {
    String userIdentifier = userSourceCodeManager.getUserIdentifier();
    if (userIdentifier != null) {
      UserSourceCodeManager savedUserSourceCodeManager;
      UserDetailsResponseDTO userResponse = getUserDetails(userSourceCodeManager);
      userSourceCodeManager.setUserName(userResponse.getUserName());
      userSourceCodeManager.setUserEmail(userResponse.getUserEmail());
      try {
        savedUserSourceCodeManager =
            userSourceCodeManagerRepository.save(scmMapperHelper.toEntity(userSourceCodeManager));
      } catch (DuplicateKeyException e) {
        throw new DuplicateFieldException(
            format("User Source Code Manager with userId [%s], accountId [%s], and type [%s] already exists",
                userIdentifier, userSourceCodeManager.getAccountIdentifier(), userSourceCodeManager.getType()));
      }
      return scmMapperHelper.toDTO(savedUserSourceCodeManager);
    } else {
      throw new InvalidRequestException("User identifier cannot be null");
    }
  }

  @Override
  public long delete(String accountIdentifier, String userIdentifier, SCMType type) {
    if (type == null) {
      return userSourceCodeManagerRepository.deleteByAccountIdentifierAndUserIdentifier(
          accountIdentifier, userIdentifier);
    } else {
      return userSourceCodeManagerRepository.deleteByAccountIdentifierAndUserIdentifierAndType(
          accountIdentifier, userIdentifier, type);
    }
  }

  @Override
  public UserSourceCodeManagerDTO update(UserSourceCodeManagerDTO userSourceCodeManagerDTO) {
    Criteria criteria = Criteria.where(UserSourceCodeManagerKeys.accountIdentifier)
                            .is(userSourceCodeManagerDTO.getAccountIdentifier())
                            .and(UserSourceCodeManagerKeys.userIdentifier)
                            .is(userSourceCodeManagerDTO.getUserIdentifier())
                            .and(UserSourceCodeManagerKeys.type)
                            .is(userSourceCodeManagerDTO.getType());
    return scmMapperHelper.toDTO(userSourceCodeManagerRepository.update(
        new Query(criteria), scmMapperHelper.getUpdateOperationForApiAccess(userSourceCodeManagerDTO)));
  }

  private UserDetailsResponseDTO getUserDetails(UserSourceCodeManagerDTO userSourceCodeManagerDTO) {
    GitAccessRequest gitAccessRequest = GitAccessRequestMapper.buildGitAccessRequest(userSourceCodeManagerDTO);
    io.harness.security.dto.Principal currentPrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    if (currentPrincipal == null) {
      currentPrincipal = SecurityContextBuilder.getPrincipal();
    }
    UserDetailsRequest userDetailsRequest = UserDetailsRequest.newBuilder()
                                                .setGitAccessRequest(gitAccessRequest)
                                                .setAccountIdentifier(userSourceCodeManagerDTO.getAccountIdentifier())
                                                .setPrincipal(PrincipalProtoMapper.toPrincipalProto(currentPrincipal))
                                                .build();
    final io.harness.gitsync.UserDetailsResponse userDetailsResponse = GitSyncGrpcClientUtils.retryAndProcessException(
        harnessToGitPushInfoServiceBlockingStub::getUserDetails, userDetailsRequest);
    if (isFailureResponse(userDetailsResponse.getStatusCode())) {
      log.error("Error in getting user details from SCM: {}", userDetailsResponse);
      ScmErrorHandler.processAndThrowException(userDetailsResponse.getStatusCode(),
          getScmErrorDetailsFromGitProtoResponse(userDetailsResponse.getError()), null);
    }

    return UserDetailsResponseDTO.builder()
        .userName(userDetailsResponse.getUserName())
        .userEmail(userDetailsResponse.getUserEmail())
        .build();
  }

  private ScmErrorDetails getScmErrorDetailsFromGitProtoResponse(ErrorDetails errorDetails) {
    return ScmErrorDetails.builder()
        .errorMessage(errorDetails.getErrorMessage())
        .explanationMessage(errorDetails.getExplanationMessage())
        .hintMessage(errorDetails.getHintMessage())
        .build();
  }

  private boolean isFailureResponse(int statusCode) {
    return statusCode >= 300;
  }
}
