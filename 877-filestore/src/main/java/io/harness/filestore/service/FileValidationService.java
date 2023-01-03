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
public interface FileValidationService {
  /**
   * Check if file or folder with provided identifier exists in DB.
   *
   * @param fileDto file or folder
   * @return true or false
   */
  boolean isFileExistByName(FileDTO fileDto);

  /**
   * Check if file or folder with provided identifier exists in DB.
   *
   * @param fileDto file or folder
   * @return true or false
   */
  boolean isFileExistsByIdentifier(FileDTO fileDto);

  /**
   * Check if parent folder exists in DB by identifier.
   *
   * @param fileDto file or folder
   * @return true or false
   */
  boolean parentFolderExists(FileDTO fileDto);
}
