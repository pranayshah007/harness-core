/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;

@Slf4j
public class PerspectiveAnomalyServiceImpl implements PerspectiveAnomalyService {
  @Inject CEViewService viewService;
  @Inject PerspectiveToAnomalyQueryHelper perspectiveToAnomalyQueryHelper;
  @Inject AnomalyQueryBuilder anomalyQueryBuilder;
  @Inject AnomalyDao anomalyDao;

  private static final Integer DEFAULT_LIMIT = 1000;
  private static final Integer DEFAULT_OFFSET = 0;

  @Override
  public List<AnomalyData> listPerspectiveAnomaliesForDate(
      @NonNull String accountIdentifier, @NonNull String perspectiveId, Instant date) {
    log.info("Perspective id: {}", perspectiveId);
    CEView perspective = viewService.get(perspectiveId);
    log.info("Perspective: {}", perspective);
    List<CCMFilter> filters = perspectiveToAnomalyQueryHelper.getConvertedRulesForPerspective(perspective);
    log.info("ConvertedRulesForPerspective size: {}", filters.size());
    log.info("ConvertedRulesForPerspective: {}", filters);
    Condition condition = anomalyQueryBuilder.applyPerspectiveRuleFilters(filters);
    log.info("Condition: {}", condition);
    List<Anomalies> anomalies = anomalyDao.fetchAnomaliesForNotification(accountIdentifier, condition,
        anomalyQueryBuilder.getOrderByFields(Collections.emptyList()), DEFAULT_OFFSET, DEFAULT_LIMIT,
        date.truncatedTo(ChronoUnit.DAYS));
    log.info("Anomalies: {}", anomalies);
    List<AnomalyData> anomalyData = new ArrayList<>();
    anomalies.forEach(anomaly -> anomalyData.add(AnomalyUtils.buildAnomalyData(anomaly)));
    return anomalyData;
  }

  @Override
  public void updateAnomalySentStatus(@NonNull String accountId, String anomalyId, boolean notificationSentStatus) {
    anomalyDao.updateAnomalyNotificationSentStatus(accountId, anomalyId, notificationSentStatus);
  }
}
