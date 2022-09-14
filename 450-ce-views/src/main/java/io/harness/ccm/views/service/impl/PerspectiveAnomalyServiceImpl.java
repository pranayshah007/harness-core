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
import io.harness.ccm.commons.entities.anomaly.AnomalyQueryDTO;
import io.harness.ccm.commons.utils.AnomalyQueryBuilder;
import io.harness.ccm.commons.utils.AnomalyUtils;
import io.harness.ccm.views.dto.PerspectiveQueryDTO;
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
import org.jooq.Condition;
import org.jooq.impl.DSL;

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
    CEView perspective = viewService.get(perspectiveId);
    CCMFilter filters =
        perspectiveToAnomalyQueryHelper.getConvertedFiltersForPerspective(perspective, getDefaultPerspectiveQuery());
    return listAnomalies(accountIdentifier,
        AnomalyQueryDTO.builder()
            .filter(filters)
            .orderBy(Collections.emptyList())
            .limit(DEFAULT_LIMIT)
            .offset(DEFAULT_OFFSET)
            .build(),
        date);
  }

  @Override
  public void updateAnomalySentStatus(@NonNull String accountId, String anomalyId, boolean notificationSentStatus) {
    anomalyDao.updateAnomalyNotificationSentStatus(accountId, anomalyId, notificationSentStatus);
  }

  private List<AnomalyData> listAnomalies(
      @NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery, Instant date) {
    if (anomalyQuery == null) {
      anomalyQuery = AnomalyUtils.getDefaultAnomalyQuery();
    }
    Condition condition = anomalyQuery.getFilter() != null
        ? anomalyQueryBuilder.applyAllFilters(anomalyQuery.getFilter())
        : DSL.noCondition();

    List<Anomalies> anomalies = anomalyDao.fetchAnomaliesForNotification(accountIdentifier, condition,
        anomalyQueryBuilder.getOrderByFields(
            anomalyQuery.getOrderBy() != null ? anomalyQuery.getOrderBy() : Collections.emptyList()),
        anomalyQuery.getOffset() != null ? anomalyQuery.getOffset() : DEFAULT_OFFSET,
        anomalyQuery.getLimit() != null ? anomalyQuery.getLimit() : DEFAULT_LIMIT, date.truncatedTo(ChronoUnit.DAYS));

    List<AnomalyData> anomalyData = new ArrayList<>();
    anomalies.forEach(anomaly -> anomalyData.add(AnomalyUtils.buildAnomalyData(anomaly)));
    return anomalyData;
  }

  private PerspectiveQueryDTO getDefaultPerspectiveQuery() {
    return PerspectiveQueryDTO.builder().filters(null).groupBy(null).build();
  }
}
