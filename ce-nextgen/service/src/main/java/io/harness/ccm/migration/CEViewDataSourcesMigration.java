/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.migration;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.entities.ViewType;
import io.harness.ccm.views.entities.ViewVisualization;
import io.harness.ccm.views.service.CEViewService;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CE)
public class CEViewDataSourcesMigration implements NGMigration {
  @Inject private CEViewService ceViewService;
  @Inject private CEViewDao ceViewDao;
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    try {
      log.info("Starting migration (updates) of all CE Views DataSources");
      final List<CEView> ceViewList = hPersistence.createQuery(CEView.class, excludeAuthority).asList();
      for (final CEView ceView : ceViewList) {
        try {
          migrateCEViewDataSources(ceView);
        } catch (final Exception e) {
          log.error("Migration Failed for Account {}, ViewId {}", ceView.getAccountId(), ceView.getUuid(), e);
        }
      }
      log.info("CEViewDataSourcesMigration has been completed");
    } catch (final Exception e) {
      log.error("Failure occurred in CEViewDataSourcesMigration", e);
    }
  }

  private void migrateCEViewDataSources(final CEView ceView) {
    modifyCEView(ceView);
    ceViewDao.update(ceView);
  }

  private void modifyCEView(final CEView ceView) {
    if (Objects.isNull(ceView.getViewType())) {
      ceView.setViewType(ViewType.CUSTOMER);
    }
    if (Objects.isNull(ceView.getViewRules())) {
      ceView.setViewRules(new ArrayList<>());
    }
    ceView.setDataSources(
        getCEViewDataSources(ceView.getViewRules(), ceView.getViewVisualization(), ceView.getAccountId()));
  }

  private List<ViewFieldIdentifier> getCEViewDataSources(
      final List<ViewRule> viewRules, final ViewVisualization viewVisualization, final String accountId) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    viewFieldIdentifiers.addAll(getViewFieldIdentifiersFromRules(viewRules, accountId));
    viewFieldIdentifiers.addAll(getViewFieldIdentifiersFromGroupBy(viewVisualization));
    return new ArrayList<>(viewFieldIdentifiers);
  }

  private Set<ViewFieldIdentifier> getViewFieldIdentifiersFromRules(List<ViewRule> viewRules, String accountId) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    if (Objects.nonNull(viewRules)) {
      viewRules.forEach(viewRule -> {
        if (Objects.nonNull(viewRule) && Objects.nonNull(viewRule.getViewConditions())) {
          viewRule.getViewConditions().forEach(viewCondition -> {
            final ViewIdCondition viewIdCondition = (ViewIdCondition) viewCondition;
            final ViewFieldIdentifier viewFieldIdentifier = viewIdCondition.getViewField().getIdentifier();
            if (viewFieldIdentifier != ViewFieldIdentifier.LABEL) {
              if (viewFieldIdentifier == ViewFieldIdentifier.COMMON) {
                viewFieldIdentifiers.addAll(
                    ceViewService.getDataSourcesFromCloudProviderField(viewIdCondition, accountId));
              } else {
                viewFieldIdentifiers.add(viewIdCondition.getViewField().getIdentifier());
              }
            }
          });
        }
      });
    }
    return viewFieldIdentifiers;
  }

  private Set<ViewFieldIdentifier> getViewFieldIdentifiersFromGroupBy(final ViewVisualization viewVisualization) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    if (Objects.nonNull(viewVisualization) && Objects.nonNull(viewVisualization.getGroupBy())
        && Objects.nonNull(viewVisualization.getGroupBy().getIdentifier())
        && viewVisualization.getGroupBy().getIdentifier() != ViewFieldIdentifier.LABEL
        && viewVisualization.getGroupBy().getIdentifier() != ViewFieldIdentifier.COMMON) {
      viewFieldIdentifiers.add(viewVisualization.getGroupBy().getIdentifier());
    }
    return viewFieldIdentifiers;
  }
}
