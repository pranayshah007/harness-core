/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import io.harness.annotations.retry.RetryOnException;
import io.harness.ccm.commons.dao.anomaly.AnomalyDao;
import io.harness.ccm.commons.entities.CCMFilter;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.utils.AnomalyQueryBuilder;
import io.harness.ccm.commons.utils.AnomalyUtils;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.helper.PerspectiveToAnomalyQueryHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.PerspectiveAnomalyService;
import io.harness.timescaledb.tables.pojos.Anomalies;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.harness.timescaledb.tables.records.AnomaliesRecord;
import lombok.NonNull;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.SelectFinalStep;
import org.jooq.impl.DSL;

import javax.annotation.Nullable;

import static com.google.common.base.MoreObjects.firstNonNull;
import static io.harness.ccm.commons.utils.TimeUtils.toOffsetDateTime;
import static io.harness.timescaledb.Tables.ANOMALIES;

public class PerspectiveAnomalyServiceImpl implements PerspectiveAnomalyService {
  @Inject CEViewService viewService;
  @Inject PerspectiveToAnomalyQueryHelper perspectiveToAnomalyQueryHelper;
  @Inject AnomalyQueryBuilder anomalyQueryBuilder;
  @Inject AnomalyDao anomalyDao;
  @Inject private DSLContext dslContext;

  private static final int RETRY_COUNT = 3;
  private static final int SLEEP_DURATION = 100;

  private static final Integer DEFAULT_LIMIT = 1000;
  private static final Integer DEFAULT_OFFSET = 0;

  @Override
  public List<AnomalyData> listPerspectiveAnomaliesForDate(
      @NonNull String accountIdentifier, @NonNull String perspectiveId, Instant date) {
    CEView perspective = viewService.get(perspectiveId);
    List<CCMFilter> filters = perspectiveToAnomalyQueryHelper.getConvertedRulesForPerspective(perspective);
    Condition condition = anomalyQueryBuilder.applyPerspectiveRuleFilters(filters);
    Condition newCondition = firstNonNull(condition, DSL.noCondition());
    List<Anomalies> anomalies = anomalyDao.fetchAnomaliesForNotification(accountIdentifier, condition,
        anomalyQueryBuilder.getOrderByFields(Collections.emptyList()), DEFAULT_OFFSET, DEFAULT_LIMIT,
        date.truncatedTo(ChronoUnit.DAYS));
    List<AnomalyData> anomalyData = new ArrayList<>();
    anomalies.forEach(anomaly -> anomalyData.add(AnomalyUtils.buildAnomalyData(anomaly)));
    return anomalyData;
  }

  @Override
  public void updateAnomalySentStatus(@NonNull String accountId, String anomalyId, boolean notificationSentStatus) {
    anomalyDao.updateAnomalyNotificationSentStatus(accountId, anomalyId, notificationSentStatus);
  }
}
