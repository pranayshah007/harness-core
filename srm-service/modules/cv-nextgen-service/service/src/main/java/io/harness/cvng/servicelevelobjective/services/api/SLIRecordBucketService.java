/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;

import java.time.Instant;
import java.util.List;

public interface SLIRecordBucketService {
  void create(List<SLIRecordParam> sliRecordList, String sliId, int sliVersion);

  SLIRecordBucket getLastSLIRecord(String sliId, Instant startTimeStamp);

  SLIRecordBucket getLatestSLIRecord(String sliId);

  List<SLIRecordBucket> getSLIRecords(String sliId, Instant startTime, Instant endTime);
}
