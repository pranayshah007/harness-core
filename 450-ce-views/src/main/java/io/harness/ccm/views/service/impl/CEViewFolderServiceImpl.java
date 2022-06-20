/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.dao.CEViewFolderDao;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.ccm.views.entities.ViewType;
import io.harness.ccm.views.service.CEViewFolderService;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@OwnedBy(CE)
public class CEViewFolderServiceImpl implements CEViewFolderService {
  @Inject private CEViewDao ceViewDao;
  @Inject private CEViewFolderDao ceViewFolderDao;

  private static final long FOLDERS_LIMIT = 500;

  @Override
  public CEViewFolder save(CEViewFolder ceViewFolder) {
    if (numberOfFolders(ceViewFolder.getAccountId()) >= FOLDERS_LIMIT) {
      throw new InvalidRequestException("Folders limit reached. Please delete existing ones to create a new one.");
    }
    if (ceViewFolderDao.findByAccountIdAndName(ceViewFolder.getAccountId(), ceViewFolder.getName()) != null) {
      throw new InvalidRequestException("Folder with same name already exists.");
    }
    return ceViewFolderDao.save(ceViewFolder);
  }

  @Override
  public long numberOfFolders(String accountId) {
    return ceViewFolderDao.getNumberOfFolders(accountId);
  }

  @Override
  public long numberOfFolders(String accountId, List<String> folderIds) {
    return ceViewFolderDao.getNumberOfFolders(accountId, folderIds);
  }

  @Override
  public List<CEViewFolder> getFolders(String accountId) {
    return ceViewFolderDao.getFolders(accountId);
  }

  @Override
  public List<CEViewFolder> getFolders(String accountId, List<String> folderIds) {
    return ceViewFolderDao.getFolders(accountId, folderIds);
  }

  @Override
  public void createDefaultFolders(String accountId) {
    if (ceViewFolderDao.getDefaultFolder(accountId) == null) {
      ceViewFolderDao.createDefaultOrSampleFolder(accountId, ViewType.DEFAULT);
    }
    if (ceViewFolderDao.getSampleFolder(accountId) == null) {
      ceViewFolderDao.createDefaultOrSampleFolder(accountId, ViewType.SAMPLE);
    }
  }

  @Override
  public CEViewFolder updateFolder(String accountId, CEViewFolder ceViewFolder) {
    return ceViewFolderDao.updateFolder(accountId, ceViewFolder);
  }

  @Override
  public List<CEView> moveMultipleCEViews(String accountId, List<String> ceViewIds, String toFolderId) {
    if (StringUtils.isEmpty(toFolderId)) {
      ceViewDao.moveMultiplePerspectiveFolder(
          accountId, ceViewIds, ceViewFolderDao.getDefaultFolder(accountId).getUuid());
    }
    return ceViewDao.moveMultiplePerspectiveFolder(accountId, ceViewIds, toFolderId);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    List<CEView> perspectives = ceViewDao.findByAccountIdAndFolderId(accountId, uuid, null);
    List<String> perspectiveIds = perspectives.stream().map(CEView::getUuid).collect(Collectors.toList());
    CEViewFolder defaultFolder = ceViewFolderDao.getDefaultFolder(accountId);
    String defaultFolderId;
    if (defaultFolder == null) {
      defaultFolderId = ceViewFolderDao.createDefaultOrSampleFolder(accountId, ViewType.DEFAULT);
    } else {
      defaultFolderId = defaultFolder.getUuid();
    }
    if (defaultFolderId.equals(uuid)) {
      throw new InvalidRequestException("Default Folder can't be deleted");
    }
    ceViewDao.moveMultiplePerspectiveFolder(accountId, perspectiveIds, defaultFolderId);
    return ceViewFolderDao.delete(accountId, uuid);
  }
}
