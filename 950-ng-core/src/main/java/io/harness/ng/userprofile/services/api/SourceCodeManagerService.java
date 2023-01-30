/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.userprofile.services.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.userprofile.commons.SourceCodeManagerDTO;

import java.util.List;

@OwnedBy(PL)
public interface SourceCodeManagerService {
  List<SourceCodeManagerDTO> get(String accountIdentifier);

  List<SourceCodeManagerDTO> get(String userIdentifier, String accountIdentifier);

  SourceCodeManagerDTO save(SourceCodeManagerDTO sourceCodeManagerDTO);

  SourceCodeManagerDTO update(String sourceCodeManagerIdentifier, SourceCodeManagerDTO sourceCodeManagerDTO);

  boolean delete(String scmIdentifier, String accountIdentifier);

  void deleteByAccount(String accountId);
}
