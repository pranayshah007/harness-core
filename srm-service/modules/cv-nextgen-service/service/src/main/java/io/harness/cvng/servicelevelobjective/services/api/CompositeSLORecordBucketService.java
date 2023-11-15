/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecordBucket;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;

import java.time.Instant;
import java.util.List;

public interface CompositeSLORecordBucketService {
  void create(CompositeServiceLevelObjective compositeServiceLevelObjective, Instant startTime, Instant endTime,
      String verificationTaskId);
  CompositeSLORecordBucket getLatestCompositeSLORecordBucket(String sloId);
  CompositeSLORecordBucket getLatestCompositeSLORecordBucketWithVersion(
      String sloId, Instant startTimeForCurrentRange, int sloVersion);
  CompositeSLORecordBucket getFirstCompositeSLORecordBucket(String sloId, Instant startTimeStamp);
  CompositeSLORecordBucket getLastCompositeSLORecordBucket(String sloId, Instant startTimeStamp);
  List<CompositeSLORecordBucket> getSLORecordBuckets(String sloId, Instant startTimeStamp, Instant endTimeStamp);
  List<CompositeSLORecordBucket> getLatestCountSLORecords(String sloId, int count);
  List<CompositeSLORecordBucket> getSLORecordsOfMinutes(String sloId, List<Instant> minutes);
}
