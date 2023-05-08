/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;

import java.time.Instant;
import java.util.List;

public interface CompositeSLORecordService {
  void create(CompositeServiceLevelObjective compositeServiceLevelObjective, Instant startTime, Instant endTime,
      String verificationTaskId);

  CompositeSLORecord getLatestCompositeSLORecord(String sloId);
  CompositeSLORecord getLatestCompositeSLORecordWithVersion(
      String sloId, Instant startTimeForCurrentRange, int sloVersion);
  CompositeSLORecord getFirstCompositeSLORecord(String sloId, Instant startTimeStamp);
  CompositeSLORecord getLastCompositeSLORecord(String sloId, Instant startTimeStamp);
  List<CompositeSLORecord> getSLORecords(String sloId, Instant startTimeStamp, Instant endTimeStamp);
  List<CompositeSLORecord> getLatestCountSLORecords(String sloId, int count);

  List<CompositeSLORecord> getSLORecordsOfMinutes(String sloId, List<Instant> minutes);

  double getErrorBudgetBurnRate(String sloId, long lookBackDuration, int totalErrorBudgetMinutes);
}
