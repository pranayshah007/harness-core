/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.intf;

import io.harness.ccm.commons.entities.CCMFilter;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.entities.anomaly.AnomalyFeedbackDTO;
import io.harness.ccm.commons.entities.anomaly.AnomalyQueryDTO;
import io.harness.ccm.commons.entities.anomaly.AnomalySummary;
import io.harness.ccm.commons.entities.anomaly.AnomalyWidgetData;
import io.harness.ccm.commons.entities.anomaly.PerspectiveAnomalyData;
import io.harness.ccm.graphql.dto.recommendation.FilterStatsDTO;
import io.harness.ccm.views.dto.PerspectiveQueryDTO;
import io.harness.ccm.views.entities.CEView;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import lombok.NonNull;

public interface AnomalyService {
  List<AnomalyData> listAnomalies(
      @NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery, Set<String> allowedAnomaliesIds);
  List<AnomalyData> listAnomalies(@NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery,
      @NonNull List<CCMFilter> ruleFilters, Set<String> allowedAnomaliesIds, boolean alwaysAllowed);
  List<FilterStatsDTO> getAnomalyFilterStats(@NonNull String accountIdentifier, List<String> anomalyColumnsList);
  List<PerspectiveAnomalyData> listPerspectiveAnomalies(
      @NonNull String accountIdentifier, @NonNull CEView perspective, PerspectiveQueryDTO perspectiveQuery);
  Boolean updateAnomalyFeedback(@NonNull String accountIdentifier, String anomalyId, AnomalyFeedbackDTO feedback);
  List<AnomalySummary> getAnomalySummary(
      @NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery, Set<String> allowedAnomaliesIds);
  List<AnomalyWidgetData> getAnomalyWidgetData(
      @NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery, Set<String> allowedAnomaliesIds);
  Set<String> listAllowedAnomaliesIds(
      @NonNull String accountIdentifier, Set<String> allowedFolderIds, List<CEView> perspectives);
  HashMap<String, CEView> listAllowedAnomaliesIdAndPerspectives(
      @NonNull String accountIdentifier, Set<String> allowedFolderIds, List<CEView> perspectives);
  List<AnomalyData> addPerspectiveInfo(
      List<AnomalyData> anomalyData, HashMap<String, CEView> allowedAnomaliesIdAndPerspectives);
}
