/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.newrelic;

import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.NewRelicConfig;
import software.wings.beans.NewRelicDeploymentMarkerPayload;
import software.wings.beans.TaskType;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.newrelic.NewRelicApplication;
import software.wings.service.impl.newrelic.NewRelicApplicationInstance;
import software.wings.service.impl.newrelic.NewRelicMetric;
import software.wings.service.impl.newrelic.NewRelicMetricData;
import software.wings.service.impl.newrelic.NewRelicSetupTestNodeData;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;

/**
 * Created by rsingh on 8/28/17.
 */
public interface NewRelicDelegateService {
  @DelegateTaskType(TaskType.NEWRELIC_VALIDATE_CONFIGURATION_TASK)
  boolean validateConfig(@NotNull NewRelicConfig newRelicConfig, List<EncryptedDataDetail> encryptedDataDetails);

  @DelegateTaskType(TaskType.NEWRELIC_GET_APP_TASK)
  List<NewRelicApplication> getAllApplications(@NotNull NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, ThirdPartyApiCallLog apiCallLog)
      throws IOException, CloneNotSupportedException;

  @DelegateTaskType(TaskType.NEWRELIC_GET_APP_INSTANCES_TASK)
  List<NewRelicApplicationInstance> getApplicationInstances(NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, long newRelicApplicationId, ThirdPartyApiCallLog apiCallLog);

  NewRelicMetricData getMetricDataApplicationInstance(NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, long newRelicApplicationId, long instanceId,
      Collection<String> metricNames, long fromTime, long toTime, ThirdPartyApiCallLog apiCallLog) throws IOException;

  NewRelicMetricData getMetricDataApplication(NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, long newRelicApplicationId, Collection<String> metricNames,
      long fromTime, long toTime, boolean summarize, ThirdPartyApiCallLog apiCallLog) throws IOException;

  @DelegateTaskType(TaskType.NEWRELIC_POST_DEPLOYMENT_MARKER)
  String postDeploymentMarker(NewRelicConfig config, List<EncryptedDataDetail> encryptedDataDetails,
      long newRelicApplicationId, NewRelicDeploymentMarkerPayload body, ThirdPartyApiCallLog apiCallLog);

  Set<NewRelicMetric> getTxnNameToCollect(NewRelicConfig newRelicConfig, List<EncryptedDataDetail> encryptedDataDetails,
      long newRelicAppId, ThirdPartyApiCallLog thirdPartyApiCallLog);

  Set<NewRelicMetric> getTxnsWithDataInLastHour(Collection<NewRelicMetric> metrics, NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, long applicationId, boolean checkNotAllowedStrings,
      ThirdPartyApiCallLog apiCallLog) throws IOException;

  @DelegateTaskType(TaskType.NEWRELIC_GET_TXNS_WITH_DATA)
  List<NewRelicMetric> getTxnsWithData(NewRelicConfig newRelicConfig, List<EncryptedDataDetail> encryptionDetails,
      long newRelicAppId, boolean checkNotAllowedStrings, ThirdPartyApiCallLog thirdPartyApiCallLog);

  @DelegateTaskType(TaskType.NEWRELIC_GET_TXNS_WITH_DATA_FOR_NODE)
  VerificationNodeDataSetupResponse getMetricsWithDataForNode(NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, NewRelicSetupTestNodeData setupTestNodeData, long instanceId,
      boolean checkNotAllowedStrings, ThirdPartyApiCallLog apiCallLog) throws IOException;

  @DelegateTaskType(TaskType.NEWRELIC_RESOLVE_APP_TASK)
  NewRelicApplication resolveNewRelicApplicationName(@NotNull NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, String newRelicApplicationName, ThirdPartyApiCallLog apiCallLog);

  @DelegateTaskType(TaskType.NEWRELIC_RESOLVE_APP_ID_TASK)
  NewRelicApplication resolveNewRelicApplicationId(@NotNull NewRelicConfig newRelicConfig,
      List<EncryptedDataDetail> encryptedDataDetails, String newRelicApplicationId, ThirdPartyApiCallLog apiCallLog);
}
