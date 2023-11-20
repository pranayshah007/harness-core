/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLIValue;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordCount;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;

import java.time.Instant;
import java.util.List;

public interface SLIRecordBucketService {
  List<SLIRecordBucket> getSLIRecordBucketsForFilterRange(
      String uuid, Instant startTime, Instant endTime, TimeRangeParams filter, long numOfDataPointsInBetween);

  void create(List<SLIRecordParam> sliRecordList, String sliId, int sliVersion);

  SLIRecordBucket getFirstSLIRecord(String sliId, Instant timestampInclusive);

  SLIRecordBucket getLastSLIRecord(String sliId, Instant startTimeStamp);

  SLIRecordBucket getLatestSLIRecord(String sliId);

  List<SLIRecordBucket> getSLIRecords(String sliId, Instant startTime, Instant endTime);

  List<SLIRecordBucket> getSLIRecordsOfMinutes(String sliId, List<Instant> minutes);

  List<SLIRecordBucket> getSLIRecordsWithSLIVersion(String sliId, Instant startTime, Instant endTime, int sliVersion);

  long getBadCountTillRangeStartTime(ServiceLevelIndicator serviceLevelIndicator, SLIMissingDataType sliMissingDataType,
      SLIValue sliValue, SLIRecordBucket sliRecordBucket, long previousRunningCount);

  SLIValue calculateSLIValue(SLIEvaluationType sliEvaluationType, SLIMissingDataType sliMissingDataType,
      SLIRecordBucket sliRecordBucket, SLIRecordCount baselineRunningCountPair, long beginningMinute,
      long disabledMinutesFromStart);

  SLIRecordCount getPreviousBucketRunningCount(
      SLIRecordBucket sliRecordBucket, ServiceLevelIndicator serviceLevelIndicator);
}
