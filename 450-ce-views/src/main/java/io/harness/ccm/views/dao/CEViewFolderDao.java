/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.ccm.views.entities.CEViewFolder.CEViewFolderKeys;
import io.harness.ccm.views.entities.ViewType;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
public class CEViewFolderDao {
  @Inject private HPersistence hPersistence;

  public CEViewFolder save(CEViewFolder ceViewFolder) {
    String id = hPersistence.save(ceViewFolder);
    return hPersistence.createQuery(CEViewFolder.class).field(CEViewFolderKeys.uuid).equal(id).get();
  }

  public CEViewFolder findByAccountIdAndName(String accountId, String folderName) {
    return hPersistence.createQuery(CEViewFolder.class)
        .field(CEViewFolderKeys.accountId)
        .equal(accountId)
        .field(CEViewFolderKeys.name)
        .equal(folderName)
        .get();
  }

  public long getNumberOfFolders(String accountId) {
    return hPersistence.createQuery(CEViewFolder.class).field(CEViewFolderKeys.accountId).equal(accountId).count();
  }

  public long getNumberOfFolders(String accountId, List<String> folderIds) {
    return hPersistence.createQuery(CEViewFolder.class)
        .field(CEViewFolderKeys.accountId)
        .equal(accountId)
        .field(CEViewFolderKeys.uuid)
        .in(folderIds)
        .count();
  }

  public List<CEViewFolder> getFolders(String accountId) {
    return hPersistence.createQuery(CEViewFolder.class)
        .field(CEViewFolderKeys.accountId)
        .equal(accountId)
        .order(Sort.descending(CEViewFolderKeys.pinned), Sort.ascending(CEViewFolderKeys.name))
        .asList();
  }

  public List<CEViewFolder> getFolders(String accountId, List<String> folderIds) {
    return hPersistence.createQuery(CEViewFolder.class)
        .field(CEViewFolderKeys.accountId)
        .equal(accountId)
        .field(CEViewFolderKeys.uuid)
        .in(folderIds)
        .order(Sort.descending(CEViewFolderKeys.pinned), Sort.ascending(CEViewFolderKeys.name))
        .asList();
  }

  public CEViewFolder getDefaultFolder(String accountId) {
    return hPersistence.createQuery(CEViewFolder.class)
        .field(CEViewFolderKeys.accountId)
        .equal(accountId)
        .field(CEViewFolderKeys.viewType)
        .equal(ViewType.DEFAULT)
        .get();
  }

  public CEViewFolder getSampleFolder(String accountId) {
    return hPersistence.createQuery(CEViewFolder.class)
        .field(CEViewFolderKeys.accountId)
        .equal(accountId)
        .field(CEViewFolderKeys.viewType)
        .equal(ViewType.SAMPLE)
        .get();
  }

  public String createDefaultOrSampleFolder(String accountId, ViewType viewType) {
    CEViewFolder ceViewFolder = CEViewFolder.builder()
                                    .accountId(accountId)
                                    .name((viewType.equals(ViewType.DEFAULT)) ? "Default" : "By Harness")
                                    .pinned(true)
                                    .viewType(viewType)
                                    .description((viewType.equals(ViewType.DEFAULT))
                                            ? "Contains all the custom perspectives not belonging to any folder"
                                            : "Contains all the perspectives created by Harness")
                                    .build();
    return hPersistence.save(ceViewFolder);
  }

  public CEViewFolder updateFolder(String accountId, CEViewFolder ceViewFolder) {
    Query<CEViewFolder> query = hPersistence.createQuery(CEViewFolder.class)
                                    .field(CEViewFolderKeys.accountId)
                                    .equal(accountId)
                                    .field(CEViewFolderKeys.uuid)
                                    .equal(ceViewFolder.getUuid());

    UpdateOperations<CEViewFolder> updateOperations = hPersistence.createUpdateOperations(CEViewFolder.class);
    if (ceViewFolder.getName() != null) {
      updateOperations = updateOperations.set(CEViewFolderKeys.name, ceViewFolder.getName());
    }
    if (ceViewFolder.getTags() != null) {
      updateOperations = updateOperations.set(CEViewFolderKeys.tags, ceViewFolder.getTags());
    }
    if (ceViewFolder.getDescription() != null) {
      updateOperations = updateOperations.set(CEViewFolderKeys.description, ceViewFolder.getDescription());
    }
    if (ceViewFolder.getLastUpdatedBy() != null) {
      updateOperations = updateOperations.set(CEViewFolderKeys.lastUpdatedBy, ceViewFolder.getLastUpdatedBy());
    }
    updateOperations = updateOperations.set(CEViewFolderKeys.pinned, ceViewFolder.isPinned());

    hPersistence.update(query, updateOperations);
    return query.asList().get(0);
  }

  public boolean delete(String accountId, String uuid) {
    Query<CEViewFolder> query = hPersistence.createQuery(CEViewFolder.class)
                                    .field(CEViewFolderKeys.accountId)
                                    .equal(accountId)
                                    .field(CEViewFolderKeys.uuid)
                                    .equal(uuid);
    return hPersistence.delete(query);
  }
}
