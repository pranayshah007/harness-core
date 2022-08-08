/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.CEViewFolder;

import java.util.List;

public interface CEViewFolderService {
  CEViewFolder save(CEViewFolder ceViewFolder);
  long numberOfFolders(String accountId);
  long numberOfFolders(String accountId, List<String> folderIds);
  List<CEViewFolder> getFolders(String accountId);
  List<CEViewFolder> getFolders(String accountId, List<String> folderIds);
  void createDefaultFolders(String accountId);
  CEViewFolder updateFolder(String accountId, CEViewFolder ceViewFolder);
  List<CEView> moveMultipleCEViews(String accountId, List<String> ceViewIds, String toFolderId);
  boolean delete(String accountId, String uuid);
}
