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
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.entities.ViewType;
import io.harness.ccm.views.entities.ViewVisualization;
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

public class CEViewBusinessMappingDataSourcesMigration implements NGMigration {
  @Inject private CEViewDao ceViewDao;
  @Inject private HPersistence hPersistence;
  @Inject private BusinessMappingService businessMappingService;

  @Override
  public void migrate() {
    try {
      log.info("Starting migration (updates) of all CE Views Business Mapping Data Sources");
      final List<CEView> ceViewList = hPersistence.createQuery(CEView.class, excludeAuthority).asList();
      for (final CEView ceView : ceViewList) {
        try {
          migrateCEViewBusinessMappingDataSources(ceView);
        } catch (final Exception e) {
          log.error("Migration Failed for Account {}, ViewId {}", ceView.getAccountId(), ceView.getUuid(), e);
        }
      }
      log.info("CEViewDataSourcesMigration has been completed");
    } catch (final Exception e) {
      log.error("Failure occurred in CEViewBusinessMappingDataSourcesMigration", e);
    }
  }

  private void migrateCEViewBusinessMappingDataSources(final CEView ceView) {
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
    ceView.setBusinessMappingDataSources(getCEViewBusinessMappingDataSources(
        ceView.getViewRules(), ceView.getViewVisualization(), ceView.getAccountId()));
  }

  private List<ViewFieldIdentifier> getCEViewBusinessMappingDataSources(
      final List<ViewRule> viewRules, final ViewVisualization viewVisualization, final String accountId) {
    final Set<ViewFieldIdentifier> businessMappingDataSources = new HashSet<>();
    businessMappingDataSources.addAll(getBusinessMappingDataSourceFromRules(viewRules, accountId));
    businessMappingDataSources.addAll(getBusinessMappingDataSourcFromGroupBy(viewVisualization, accountId));
    return new ArrayList<>(businessMappingDataSources);
  }

  private Set<ViewFieldIdentifier> getBusinessMappingDataSourceFromRules(List<ViewRule> viewRules, String accountId) {
    final Set<ViewFieldIdentifier> businessMappingDataSourceFromViewRules = new HashSet<>();
    if (Objects.nonNull(viewRules)) {
      viewRules.forEach(viewRule -> {
        if (Objects.nonNull(viewRule) && Objects.nonNull(viewRule.getViewConditions())) {
          viewRule.getViewConditions().forEach(viewCondition -> {
            final ViewIdCondition viewIdCondition = (ViewIdCondition) viewCondition;
            final ViewFieldIdentifier viewFieldIdentifier = viewIdCondition.getViewField().getIdentifier();
            if (viewFieldIdentifier == ViewFieldIdentifier.BUSINESS_MAPPING) {
              List<ViewFieldIdentifier> businessMappingDataSource =
                  businessMappingService.getBusinessMappingDataSources(
                      accountId, viewIdCondition.getViewField().getFieldId());
              for (ViewFieldIdentifier field : businessMappingDataSource) {
                businessMappingDataSourceFromViewRules.add(field);
              }
            }
          });
        }
      });
    }
    return businessMappingDataSourceFromViewRules;
  }

  private Set<ViewFieldIdentifier> getBusinessMappingDataSourcFromGroupBy(
      final ViewVisualization viewVisualization, String accountId) {
    final Set<ViewFieldIdentifier> businessMappingDataSourceFromGroupBy = new HashSet<>();
    if (Objects.nonNull(viewVisualization) && Objects.nonNull(viewVisualization.getGroupBy())
        && Objects.nonNull(viewVisualization.getGroupBy().getIdentifier())
        && viewVisualization.getGroupBy().getIdentifier() != ViewFieldIdentifier.BUSINESS_MAPPING) {
      List<ViewFieldIdentifier> businessMappingDataSource =
          businessMappingService.getBusinessMappingDataSources(accountId, viewVisualization.getGroupBy().getFieldId());
      for (ViewFieldIdentifier field : businessMappingDataSource) {
        businessMappingDataSourceFromGroupBy.add(field);
      }
    }
    return businessMappingDataSourceFromGroupBy;
  }
}
