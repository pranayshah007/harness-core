/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.setup.graphql;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.commons.entities.billing.CECloudAccount;

import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class LinkedAccountStatsDataFetcher
    extends AbstractConnectionV2DataFetcher<QLCESetupFilter, QLCESetupSortCriteria, QLLinkedAccountData> {
  @Inject CESetupQueryHelper ceSetupQueryHelper;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  protected QLLinkedAccountData fetchConnection(List<QLCESetupFilter> filters,
      QLPageQueryParameters pageQueryParameters, List<QLCESetupSortCriteria> sortCriteria) {
    Query<CECloudAccount> query = populateFilters(wingsPersistence, filters, CECloudAccount.class, true);
    if (!sortCriteria.isEmpty()) {
      Sort sort = ceSetupQueryHelper.getSort(sortCriteria.get(0));
      query.order(sort);
    }

    final List<CECloudAccount> linkedAccountsInfo = query.asList();
    int countOfConnected = 0;
    int countOfNotConnected = 0;
    int countOfNotVerified = 0;

    List<QLLinkedAccount> linkedAccountsList = new ArrayList<>();
    for (CECloudAccount ceCloudAccount : linkedAccountsInfo) {
      switch (ceCloudAccount.getAccountStatus()) {
        case CONNECTED:
          countOfConnected++;
          break;
        case NOT_CONNECTED:
          countOfNotConnected++;
          break;
        case NOT_VERIFIED:
          countOfNotVerified++;
          break;
        default:
          break;
      }
      linkedAccountsList.add(populateLinkedAccounts(ceCloudAccount));
    }

    return QLLinkedAccountData.builder()
        .count(QLAccountCountStats.builder()
                   .countOfConnected(countOfConnected)
                   .countOfNotConnected(countOfNotConnected)
                   .countOfNotVerified(countOfNotVerified)
                   .build())
        .linkedAccounts(linkedAccountsList)
        .build();
  }

  private QLLinkedAccount populateLinkedAccounts(CECloudAccount ceCloudAccount) {
    return QLLinkedAccount.builder()
        .id(ceCloudAccount.getInfraAccountId())
        .arn(ceCloudAccount.getAccountArn())
        .name(ceCloudAccount.getAccountName())
        .accountStatus(ceCloudAccount.getAccountStatus())
        .masterAccountId(ceCloudAccount.getInfraMasterAccountId())
        .build();
  }

  @Override
  protected void populateFilters(List<QLCESetupFilter> filters, Query query) {
    ceSetupQueryHelper.setQuery(filters, query);
  }

  @Override
  protected QLCESetupFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return null;
  }
}
