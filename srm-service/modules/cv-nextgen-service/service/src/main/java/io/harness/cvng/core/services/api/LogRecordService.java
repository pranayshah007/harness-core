/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.LogRecordDTO;
import io.harness.cvng.core.beans.demo.DemoTemplate;
import io.harness.cvng.core.entities.LogRecord;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public interface LogRecordService {
  void save(List<LogRecordDTO> logRecords);

  /**
   * Start time inclusive and endTime exclusive.
   */
  List<LogRecord> getLogRecords(String verificationTaskId, Instant startTime, Instant endTime);

  void createDemoAnalysisData(String accountId, String verificationTaskId, String dataCollectionWorkerId,
      DemoTemplate demoTemplate, Instant startTime, Instant endTime) throws IOException;
}
