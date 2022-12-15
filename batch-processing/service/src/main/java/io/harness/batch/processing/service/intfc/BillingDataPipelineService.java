/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.intfc;

import software.wings.beans.Account;

import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceClient;
import com.google.cloud.bigquery.datatransfer.v1.TransferRun;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public interface BillingDataPipelineService {
  String createDataTransferJobFromGCS(String destinationDataSetId, String settingId, String accountId,
      String accountName, String curReportName, boolean isPrevMonthTransferJob) throws IOException;
  String createDataTransferJobFromBQ(String jobName, String srcProjectId, String srcDatasetId, String dstProjectId,
      String dstDatasetId, String impersonatedServiceAccount) throws IOException;
  String createScheduledQueriesForGCP(String scheduledQueryName, String dstDataSetId) throws IOException;
  HashMap<String, String> createScheduledQueriesForAWS(String dstDataSetId, String accountId, String accountName)
      throws IOException;
  void triggerTransferJobRun(String transferResourceName, String impersonatedServiceAccount) throws IOException;
  List<TransferRun> listTransferRuns(String transferResourceName, String impersonatedServiceAccount) throws IOException;
  TransferRun getTransferRuns(String transferRunResourceName, String impersonatedServiceAccount) throws IOException;
  String createDataSet(Account account);
  DataTransferServiceClient getDataTransferClient() throws IOException;
  String createTransferScheduledQueriesForGCP(String scheduledQueryName, String dstDataSetId,
      String impersonatedServiceAccount, String srcTablePrefix) throws IOException;
  String createRunOnceScheduledQueryGCP(String runOnceScheduledQueryName, String gcpBqProjectId, String gcpBqDatasetId,
      String dstDataSetId, String serviceAccountEmail) throws IOException;
}
