/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.gitsync.common.dtos.UserDetailsResponseDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.gitsync.common.service.UserSourceCodeManagerService;
import io.harness.ng.userprofile.commons.SCMType;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class UserSourceCodeManagerHelper {
  @Inject UserSourceCodeManagerService userSourceCodeManagerService;

  public Optional<UserSourceCodeManagerDTO> fetchUserSourceCodeManagerDTO(
      String accountIdentifier, ScmConnector connectorDTO) {
    Optional<String> userIdentifier = GitSyncUtils.getUserIdentifier();
    Optional<UserSourceCodeManagerDTO> userSourceCodeManagerDTOOptional = Optional.empty();
    try {
      SCMType scmType = SCMType.valueOf(connectorDTO.getConnectorType().name());
      return userIdentifier.map(s -> userSourceCodeManagerService.getByType(accountIdentifier, s, scmType))
          .or(() -> userSourceCodeManagerDTOOptional);
    } catch (Exception ex) {
      log.error("Invalid type of connector: {}", connectorDTO.getConnectorType(), ex);
      return userSourceCodeManagerDTOOptional;
    }
  }

  public Optional<UserDetailsResponseDTO> getUserDetails(String accountIdentifier, ScmConnector connectorDTO) {
    Optional<UserSourceCodeManagerDTO> userSourceCodeManagerDTO =
        fetchUserSourceCodeManagerDTO(accountIdentifier, connectorDTO);
    return userSourceCodeManagerDTO
        .map(s -> UserDetailsResponseDTO.builder().userEmail(s.getUserEmail()).userName(s.getUserName()).build())
        .or(Optional::empty);
  }
}
