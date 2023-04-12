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
import io.harness.gitsync.common.beans.UserSourceCodeManager;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.gitsync.common.helper.SCMMapperHelper;
import io.harness.gitsync.common.service.UserSourceCodeManagerService;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.repositories.userSourceCodeManager.UserSourceCodeManagerRepository;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(PIPELINE)
@NoArgsConstructor
@AllArgsConstructor
public class UserSourceCodeManagerServiceImpl implements UserSourceCodeManagerService {
  @Inject private UserSourceCodeManagerRepository userSourceCodeManagerRepository;
  @Inject private SCMMapperHelper scmMapperHelper;

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
}
