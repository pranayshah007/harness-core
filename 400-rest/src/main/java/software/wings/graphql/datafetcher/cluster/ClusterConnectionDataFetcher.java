/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.cluster;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.cluster.entities.ClusterRecord.ClusterRecordKeys;

import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLCluster;
import software.wings.graphql.schema.type.QLCluster.QLClusterBuilder;
import software.wings.graphql.schema.type.QLClusterConnection;
import software.wings.graphql.schema.type.QLClusterConnection.QLClusterConnectionBuilder;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.cluster.QLClusterFilter;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class ClusterConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLClusterFilter, QLNoOpSortCriteria, QLClusterConnection> {
  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  public QLClusterConnection fetchConnection(List<QLClusterFilter> clusterFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<ClusterRecord> query = populateFilters(wingsPersistence, null, ClusterRecord.class, true)
                                     .order(Sort.descending(ClusterRecordKeys.createdAt));

    QLClusterConnectionBuilder connectionBuilder = QLClusterConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, clusterRecord -> {
      QLClusterBuilder builder = QLCluster.builder();
      ClusterController.populateCluster(clusterRecord.getCluster(), clusterRecord.getUuid(), builder);
      connectionBuilder.node(builder.build());
    }));
    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLClusterFilter> filters, Query query) {
    // do nothing
  }

  @Override
  protected QLClusterFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return null;
  }
}
