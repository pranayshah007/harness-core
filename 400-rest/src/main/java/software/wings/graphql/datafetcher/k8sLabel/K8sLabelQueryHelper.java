/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.k8sLabel;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.SettingAttribute;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.k8sLabel.QLK8sLabelFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.List;

@Singleton
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class K8sLabelQueryHelper {
  @Inject protected DataFetcherUtils utils;

  public void setQuery(List<QLK8sLabelFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<SettingAttribute>> field;

      if (filter.getAccountId() != null) {
        field = query.field("accountId");
        QLIdFilter accountFilter = filter.getAccountId();
        utils.setIdFilter(field, accountFilter);
      }

      if (filter.getCluster() != null) {
        field = query.field("clusterId");
        QLIdFilter clusterFilter = filter.getCluster();
        utils.setIdFilter(field, clusterFilter);
      }

      if (filter.getNamespace() != null) {
        field = query.field("namespace");
        QLIdFilter namespaceFilter = filter.getNamespace();
        utils.setIdFilter(field, namespaceFilter);
      }

      if (filter.getWorkloadName() != null) {
        field = query.field("name");
        QLIdFilter workloadName = filter.getWorkloadName();
        utils.setIdFilter(field, workloadName);
      }
    });
  }
}
