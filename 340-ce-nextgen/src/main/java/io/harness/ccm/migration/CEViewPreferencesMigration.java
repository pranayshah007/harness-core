/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import io.harness.ccm.views.entities.ViewType;
import io.harness.ccm.views.utils.CEViewPreferenceUtils;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class CEViewPreferencesMigration implements NGMigration {
  @Inject private CEViewDao ceViewDao;
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
    modifyCEView(ceView);
    ceViewDao.update(ceView);
  }

  private void modifyCEView(final CEView ceView) {
    ceView.setViewPreferences(CEViewPreferenceUtils.getCEViewPreferencesForMigration(ceView));
    if (Objects.isNull(ceView.getViewRules())) {
      ceView.setViewRules(Collections.emptyList());
    }
    if (Objects.isNull(ceView.getDataSources())) {
      ceView.setDataSources(Collections.emptyList());
    }
    if (Objects.isNull(ceView.getViewType())) {
      ceView.setViewType(ViewType.CUSTOMER);
    }
  }
}
