/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.migration;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewPreferences;
import io.harness.ccm.views.service.CEViewPreferenceService;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class CEViewPreferencesMigration implements NGMigration {
  @Inject private CEViewDao ceViewDao;
  @Inject private CEViewPreferenceService ceViewPreferenceService;
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    try {
      log.info("Starting migration (updates) of all CE Views Preferences");
      final List<CEView> ceViewList = hPersistence.createQuery(CEView.class, excludeValidate).asList();
      for (final CEView ceView : ceViewList) {
        try {
          migrateCEViewPreferences(ceView);
        } catch (final Exception e) {
          log.error("Migration Failed for Account {}, ViewId {}", ceView.getAccountId(), ceView.getUuid(), e);
        }
      }
    } catch (final Exception e) {
      log.error("Failure occurred in CEViewsPreferencesMigration", e);
    }
    log.info("CEViewsPreferencesMigration has completed");
  }

  private void migrateCEViewPreferences(final CEView ceView) {
    final ViewPreferences viewPreferences =
        ceViewPreferenceService.getCEViewPreferences(ceView, Collections.emptySet());
    ceViewDao.updateViewPreferences(ceView.getUuid(), ceView.getAccountId(), viewPreferences);
  }
}
