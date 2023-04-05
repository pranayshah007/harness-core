/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.common.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.ng.userprofile.commons.SCMType;

import java.util.List;

@OwnedBy(PIPELINE)
public interface UserSourceCodeManagerService {
  UserSourceCodeManagerDTO getByType(String accountIdentifier, String userIdentifier, SCMType type);
  List<UserSourceCodeManagerDTO> get(String accountIdentifier, String userIdentifier);

  UserSourceCodeManagerDTO save(UserSourceCodeManagerDTO userSourceCodeManager);

  long delete(String accountIdentifier, String userIdentifier, SCMType type);
}
