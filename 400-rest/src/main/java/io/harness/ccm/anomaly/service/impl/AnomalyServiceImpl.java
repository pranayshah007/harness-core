/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.anomaly.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.anomaly.service.AnomalyDataQueryBuilder.addValidAnomaliesFilter;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.anomaly.dao.AnomalyEntityDao;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.service.AnomalyDataQueryBuilder;
import io.harness.ccm.anomaly.service.itfc.AnomalyService;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;

import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;

import com.google.inject.Inject;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class AnomalyServiceImpl implements AnomalyService {
  @Autowired @Inject private AnomalyEntityDao anomalyEntityDao;

  @Override
  public List<AnomalyEntity> list(String account, Instant date) {
    SelectQuery query = new SelectQuery();
    query.addAllTableColumns(AnomalyEntity.AnomaliesDataTableSchema.table);
    addAccountIdFilter(account, query);
    query.addCondition(
        BinaryCondition.equalTo(AnomalyEntity.AnomaliesDataTableSchema.anomalyTime, date.truncatedTo(ChronoUnit.DAYS)));
    addValidAnomaliesFilter(query);
    return anomalyEntityDao.list(query.validate().toString());
  }

  @Override
  public List<AnomalyEntity> list(String account, Instant from, Instant to) {
    SelectQuery query = new SelectQuery();
    query.addAllTableColumns(AnomalyEntity.AnomaliesDataTableSchema.table);
    addAccountIdFilter(account, query);
    query.addCondition(BinaryCondition.lessThanOrEq(
        AnomalyEntity.AnomaliesDataTableSchema.anomalyTime, to.truncatedTo(ChronoUnit.DAYS)));
    query.addCondition(BinaryCondition.greaterThanOrEq(
        AnomalyEntity.AnomaliesDataTableSchema.anomalyTime, from.truncatedTo(ChronoUnit.DAYS)));
    addValidAnomaliesFilter(query);
    return anomalyEntityDao.list(query.validate().toString());
  }

  public List<AnomalyEntity> listK8s(String accountId, List<QLBillingDataFilter> filters, List<QLCCMGroupBy> groupBy) {
    try {
      String queryStatement = AnomalyDataQueryBuilder.formK8SQuery(accountId, filters, groupBy);
      return anomalyEntityDao.list(queryStatement);
    } catch (Exception e) {
      log.error("Exception occurred in listK8s: [{}]", e.toString());
      return Collections.emptyList();
    }
  }

  public List<AnomalyEntity> listCloud(
      String accountId, List<CloudBillingFilter> filters, List<CloudBillingGroupBy> groupBy) {
    try {
      String queryStatement = AnomalyDataQueryBuilder.formCloudQuery(accountId, filters, groupBy);
      return anomalyEntityDao.list(queryStatement);
    } catch (Exception e) {
      log.error("Exception occurred in listCloud: [{}]", e.toString());
      return Collections.emptyList();
    }
  }
  public List<AnomalyEntity> listOverview(String accountId, List<QLBillingDataFilter> filters) {
    try {
      String queryStatement = AnomalyDataQueryBuilder.overviewQuery(accountId, filters);
      return anomalyEntityDao.list(queryStatement);
    } catch (Exception e) {
      log.error("Exception occurred in listOverview: [{}]", e.toString());
      return Collections.emptyList();
    }
  }

  @Override
  public void delete(List<String> ids, Instant date) {
    anomalyEntityDao.delete(ids, date);
  }

  @Override
  public void insert(List<? extends AnomalyEntity> anomalies) {
    anomalyEntityDao.insert(anomalies);
  }

  private void addAccountIdFilter(String accountId, SelectQuery query) {
    query.addCondition(BinaryCondition.equalTo(AnomalyEntity.AnomaliesDataTableSchema.accountId, accountId));
  }

  public AnomalyEntity update(AnomalyEntity anomaly) {
    return anomalyEntityDao.update(anomaly);
  }
}
