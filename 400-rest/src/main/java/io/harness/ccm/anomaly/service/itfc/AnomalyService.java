/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.anomaly.service.itfc;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;

import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;

import java.time.Instant;
import java.util.List;

@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public interface AnomalyService {
  /* anomalies on date */
  List<AnomalyEntity> list(String account, Instant date);
  /* anomalies in [date1,date2] */
  List<AnomalyEntity> list(String account, Instant from, Instant to);
  List<AnomalyEntity> listK8s(String accountId, List<QLBillingDataFilter> filters, List<QLCCMGroupBy> groupBy);
  List<AnomalyEntity> listCloud(String accountId, List<CloudBillingFilter> filters, List<CloudBillingGroupBy> groupBy);
  List<AnomalyEntity> listOverview(String accountId, List<QLBillingDataFilter> filters);
  void delete(List<String> ids, Instant date);
  Long count(String accountId);
  boolean deleteAllForAccount(String accountId);
  void insert(List<? extends AnomalyEntity> anomalies);
  AnomalyEntity update(AnomalyEntity anomaly);
}
