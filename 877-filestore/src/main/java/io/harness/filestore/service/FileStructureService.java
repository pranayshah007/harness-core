/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service;

import io.harness.beans.Scope;
import io.harness.filestore.dto.node.FolderNodeDTO;

public interface FileStructureService {
  /**
   * Create folder tree structure.
   *
   * @param folder folder to populate structure
   * @param scope scope
   * @param includeContent include file content
   */
  void createFolderTreeStructure(FolderNodeDTO folder, Scope scope, boolean includeContent);

  /**
   * Get file content.
   *
   * @param fileUuid file UUID
   * @return file content
   */
  String getFileContent(String fileUuid);
}
