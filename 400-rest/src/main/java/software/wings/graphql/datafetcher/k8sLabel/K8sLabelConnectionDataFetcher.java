/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.k8sLabel;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.cluster.dao.K8sWorkloadDao;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;

import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLK8sLabel;
import software.wings.graphql.schema.type.QLK8sLabelConnection;
import software.wings.graphql.schema.type.QLK8sLabelConnection.QLK8sLabelConnectionBuilder;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.k8sLabel.QLK8sLabelFilter;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class K8sLabelConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLK8sLabelFilter, QLNoOpSortCriteria, QLK8sLabelConnection> {
  @Inject K8sLabelQueryHelper k8sLabelQueryHelper;
  @Inject K8sWorkloadDao k8sWorkloadDao;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  public QLK8sLabelConnection fetchConnection(List<QLK8sLabelFilter> filters, QLPageQueryParameters pageQueryParameters,
      List<QLNoOpSortCriteria> sortCriteria) {
    Query<K8sWorkload> query = populateFilters(wingsPersistence, filters, K8sWorkload.class, true);

    Map<String, Set<String>> labels = new HashMap<>();
    QLK8sLabelConnectionBuilder connectionBuilder = QLK8sLabelConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, k8sWorkload -> {
      if (k8sWorkload.getLabels() != null) {
        k8sWorkload.getLabels().keySet().forEach(key -> {
          if (!labels.containsKey(key)) {
            labels.put(key, new HashSet<>());
          }
          labels.get(key).add(k8sWorkload.getLabels().get(key));
        });
      }
    }));

    labels.keySet().forEach(key -> {
      QLK8sLabel node = QLK8sLabel.builder().name(key).values(labels.get(key).toArray(new String[0])).build();
      connectionBuilder.node(node);
    });

    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLK8sLabelFilter> filters, Query query) {
    k8sLabelQueryHelper.setQuery(filters, query);
  }

  @Override
  protected QLK8sLabelFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return null;
  }

  // To fetch all labels (so that pagination is avoided)
  public List<QLK8sLabel> fetchAllLabels(List<QLK8sLabelFilter> filters) {
    Query<K8sWorkload> query = populateFilters(wingsPersistence, filters, K8sWorkload.class, false);
    List<K8sWorkload> workloads = k8sWorkloadDao.list(query);
    List<QLK8sLabel> fetchedLabels = new ArrayList<>();
    Map<String, Set<String>> labels = new HashMap<>();
    workloads.forEach(k8sWorkload -> {
      if (k8sWorkload.getLabels() != null) {
        k8sWorkload.getLabels().keySet().forEach(key -> {
          if (!labels.containsKey(key)) {
            labels.put(key, new HashSet<>());
          }
          labels.get(key).add(k8sWorkload.getLabels().get(key));
        });
      }
    });

    labels.keySet().forEach(key
        -> fetchedLabels.add(QLK8sLabel.builder().name(key).values(labels.get(key).toArray(new String[0])).build()));
    return fetchedLabels;
  }
}
