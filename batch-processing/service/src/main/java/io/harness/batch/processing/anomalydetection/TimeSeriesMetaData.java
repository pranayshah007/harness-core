/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import io.harness.ccm.anomaly.entities.EntityType;
import io.harness.ccm.anomaly.entities.TimeGranularity;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class TimeSeriesMetaData {
  String accountId;
  Instant trainStart;
  Instant trainEnd;
  Instant testStart;
  Instant testEnd;
  TimeGranularity timeGranularity;
  EntityType entityType;
  String entityIdentifier;

  K8sQueryMetaData k8sQueryMetaData;
  CloudQueryMetaData cloudQueryMetaData;
}
