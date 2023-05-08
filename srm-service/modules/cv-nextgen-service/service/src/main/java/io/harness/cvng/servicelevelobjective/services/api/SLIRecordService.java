/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.commons.math3.util.Pair;

public interface SLIRecordService {
  void create(List<SLIRecordParam> sliRecordList, String sliId, String verificationTaskId, int sliVersion);
  void delete(List<String> sliIds);

  Pair<Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecord>>,
      Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>>
  getSLODetailsSLIRecordsAndSLIMissingDataType(
      List<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail> serviceLevelObjectivesDetailList,
      Instant startTime, Instant endTime);
  List<SLIRecord> getSLIRecordsOfMinutes(String sliId, List<Instant> minutes);

  Map<String, SLIRecord> getLastCompositeSLOsSLIRecord(
      List<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail> serviceLevelObjectivesDetailList,
      Instant startTime);
  List<SLIRecord> getLatestCountSLIRecords(String sliId, int count);
  List<SLIRecord> getSLIRecordsForLookBackDuration(String sliId, long lookBackDuration);
  double getErrorBudgetBurnRate(
      String sliId, long lookBackDuration, int totalErrorBudgetMinutes, SLIMissingDataType sliMissingDataType);
  List<SLIRecord> getSLIRecords(String sliId, Instant startTime, Instant endTime);
  List<SLIRecord> getSLIRecordsWithSLIVersion(String sliId, Instant startTime, Instant endTime, int sliVersion);
  SLIRecord getFirstSLIRecord(String sliId, Instant timestampInclusive);
  SLIRecord getLatestSLIRecord(String sliId);
  SLIRecord getLastSLIRecord(String sliId, Instant startTimeStamp);
}
