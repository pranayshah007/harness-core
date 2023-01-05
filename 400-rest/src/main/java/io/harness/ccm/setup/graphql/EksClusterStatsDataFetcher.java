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
import io.harness.ccm.commons.entities.billing.CECluster;

import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class EksClusterStatsDataFetcher
    extends AbstractConnectionV2DataFetcher<QLCESetupFilter, QLNoOpSortCriteria, QLEKSClusterData> {
  @Inject private CESetupQueryHelper CESetupQueryHelper;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  protected QLEKSClusterData fetchConnection(
      List<QLCESetupFilter> filters, QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<CECluster> query = populateFilters(wingsPersistence, filters, CECluster.class, true);
    final List<CECluster> eksClusterInfo = query.asList();

    int numberOfClusters = eksClusterInfo.size();
    List<QLEKSCluster> outputEKSClusterList = new ArrayList<>();
    for (CECluster ceCluster : eksClusterInfo) {
      outputEKSClusterList.add(populateEKSCluster(ceCluster));
    }

    return QLEKSClusterData.builder().count(numberOfClusters).clusters(outputEKSClusterList).build();
  }

  private QLEKSCluster populateEKSCluster(CECluster ceCluster) {
    return QLEKSCluster.builder()
        .id(ceCluster.getUuid())
        .name(ceCluster.getClusterName())
        .infraAccountId(ceCluster.getInfraAccountId())
        .infraMasterAccountId(ceCluster.getInfraMasterAccountId())
        .parentAccountSettingId(ceCluster.getParentAccountSettingId())
        .region(ceCluster.getRegion())
        .build();
  }

  @Override
  protected QLCESetupFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return null;
  }

  @Override
  protected void populateFilters(List<QLCESetupFilter> filters, Query query) {
    CESetupQueryHelper.setQuery(filters, query);
  }
}
