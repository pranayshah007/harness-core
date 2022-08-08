/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.filestore.dto.FileDTO;

@OwnedBy(CDP)
public interface FileActivityService {
  /**
   *  Create File creation activity.
   *
   * @param accountIdentifier the account identifier
   * @param fileDTO the file DTO
   */
  void createFileCreationActivity(String accountIdentifier, FileDTO fileDTO);

  /**
   * Create File update activity.
   *
   * @param accountIdentifier the account identifier
   * @param fileDTO the file DTO
   */
  void createFileUpdateActivity(String accountIdentifier, FileDTO fileDTO);

  /**
   * Delete all file activities.
   *
   * @param accountIdentifier the account identifier
   * @param fileFQN the file full qualified name
   */
  void deleteAllActivities(String accountIdentifier, String fileFQN);
}
