/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.anomaly;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "AnomalyData", description = "This object contains details of a cost anomaly")
public class AnomalyData {
  String id;
  Long time;
  String anomalyRelativeTime;
  Double actualAmount;
  Double expectedAmount;
  Double anomalousSpend;
  Double anomalousSpendPercentage;
  String resourceName;
  String resourceInfo;
  EntityInfo entity;
  String details;
  String status;
  String statusRelativeTime;
  String comment;
  String cloudProvider;
  Double anomalyScore;
  AnomalyFeedback userFeedback;
  String perspectiveId;
  String perspectiveName;
}
