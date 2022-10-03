/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dao.CEViewFolderDao;
import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.ccm.views.entities.ViewType;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class CEViewsFolderRenameMigration implements NGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private CEViewFolderDao ceViewFolderDao;

  @Override
  public void migrate() {
    try {
      log.info("Starting migration of CCM Perspective Folders");

      List<String> accountIds = hPersistence.createQuery(CEViewFolder.class).getCollection().distinct("accountId");
      for (String accountId : accountIds) {
        if (StringUtils.isEmpty(accountId)) {
          continue;
        }
        CEViewFolder sampleFolder = ceViewFolderDao.getSampleFolder(accountId);
        if (sampleFolder == null) {
          ceViewFolderDao.createDefaultOrSampleFolder(accountId, ViewType.SAMPLE);
        } else {
          sampleFolder.setName("By Harness");
          ceViewFolderDao.updateFolder(accountId, sampleFolder);
        }
      }
    } catch (Exception e) {
      log.error("Failure occurred in CEViewsFolderRenameMigration", e);
    }
    log.info("CEViewsFolderRenameMigration has completed");
  }
}
