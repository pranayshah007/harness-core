/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.intfc;

import software.wings.beans.Account;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.verification.CVConfiguration;

/**
 * Created by rsingh on 10/9/18.
 */
public interface ContinuousVerificationService {
  boolean shouldPerformServiceGuardTasks(Account account);

  boolean triggerAPMDataCollection(String accountId);
  void triggerAPMDataCollection(CVConfiguration cvConfiguration);
  void triggerDataCollection(CVConfiguration cvConfiguration);
  /**
   * Creates tasks for Learning Engine
   * @param accountId
   */
  void triggerServiceGuardTimeSeriesAnalysis(String accountId);

  boolean triggerLogDataCollection(String accountId);

  void triggerServiceGuardAnalysis(CVConfiguration cvConfiguration);

  void triggerLogsL1Clustering(String accountId);

  void triggerLogsL2Clustering(String accountId);

  void triggerLogDataAnalysis(String accountId);

  void triggerFeedbackAnalysis(String accountId);

  void cleanupStuckLocks();

  boolean triggerWorkflowDataCollection(AnalysisContext context);

  void markWorkflowDataCollectionDone(AnalysisContext context);

  void triggerTimeSeriesAlertIfNecessary(String cvConfigId, double riskScore, long analysisMinute);

  void triggerLogAnalysisAlertIfNecessary(
      String cvConfigId, LogMLAnalysisRecord mlAnalysisResponse, int analysisMinute);

  void processNextCVTasks(String accountId);

  void expireLongRunningCVTasks(String accountId);

  void retryCVTasks(String accountId);
}
