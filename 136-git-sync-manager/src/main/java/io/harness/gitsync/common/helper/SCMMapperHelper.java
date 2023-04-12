/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.UserSourceCodeManager;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.gitsync.common.mappers.UserSourceCodeManagerMapper;
import io.harness.ng.userprofile.commons.SCMType;

import com.google.inject.Inject;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class SCMMapperHelper {
  @Inject private Map<SCMType, UserSourceCodeManagerMapper> scmMapBinder;

  public UserSourceCodeManagerDTO toDTO(UserSourceCodeManager userSourceCodeManager) {
    return scmMapBinder.get(userSourceCodeManager.getType()).toDTO(userSourceCodeManager);
  }

  public UserSourceCodeManager toEntity(UserSourceCodeManagerDTO userSourceCodeManagerDTO) {
    return scmMapBinder.get(userSourceCodeManagerDTO.getType()).toEntity(userSourceCodeManagerDTO);
  }
}
