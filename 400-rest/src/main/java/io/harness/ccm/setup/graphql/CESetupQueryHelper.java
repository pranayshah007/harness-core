/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.setup.graphql;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.commons.entities.billing.CECloudAccount.CECloudAccountKeys;

import software.wings.beans.SettingAttribute;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.util.List;

@Singleton
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class CESetupQueryHelper {
  @Inject protected DataFetcherUtils utils;

  public void setQuery(List<QLCESetupFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<SettingAttribute>> field;

      if (filter.getCloudProviderId() != null) {
        field = query.field("cloudProviderId");
        QLIdFilter cloudProviderIdFilter = filter.getCloudProviderId();
        utils.setIdFilter(field, cloudProviderIdFilter);
      }

      if (filter.getInfraMasterAccountId() != null) {
        field = query.field("infraMasterAccountId");
        QLIdFilter infraMasterAccountIdFilter = filter.getInfraMasterAccountId();
        utils.setIdFilter(field, infraMasterAccountIdFilter);
      }

      if (filter.getMasterAccountSettingId() != null) {
        field = query.field("masterAccountSettingId");
        QLIdFilter masterAccountSettingIdFilter = filter.getMasterAccountSettingId();
        utils.setIdFilter(field, masterAccountSettingIdFilter);
      }

      if (filter.getSettingId() != null) {
        field = query.field("_id");
        QLIdFilter settingIdFilter = filter.getSettingId();
        utils.setIdFilter(field, settingIdFilter);
      }
    });
  }

  public Sort getSort(QLCESetupSortCriteria sort) {
    String sortField = null;
    if (sort.getSortType() == QLCESetupSortType.status) {
      sortField = CECloudAccountKeys.accountStatus;
    }
    switch (sort.getSortOrder()) {
      case DESCENDING:
        return Sort.descending(sortField);
      case ASCENDING:
      default:
        return Sort.ascending(sortField);
    }
  }
}
