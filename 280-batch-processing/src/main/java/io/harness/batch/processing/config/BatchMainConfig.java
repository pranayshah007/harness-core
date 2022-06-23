/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import io.harness.ccm.commons.beans.config.GcpConfig;
import io.harness.cf.CfClientConfig;
import io.harness.event.handler.segment.SegmentConfig;
import io.harness.ff.FeatureFlagConfig;
import io.harness.mongo.MongoConfig;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.timescaledb.TimeScaleDBConfig;

import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.security.authentication.AwsS3SyncConfig;
import software.wings.security.authentication.BatchQueryConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchMainConfig {
  @JsonProperty("timescaledb") private TimeScaleDBConfig timeScaleDBConfig;
  @JsonProperty("harness-mongo") private MongoConfig harnessMongo;
  @JsonProperty("events-mongo") private MongoConfig eventsMongo;
  @JsonProperty("batchQueryConfig") private BatchQueryConfig batchQueryConfig;
  @JsonProperty("awsRegionIdToName") private Map<String, String> awsRegionIdToName;
  @JsonProperty("awsS3SyncConfig") private AwsS3SyncConfig awsS3SyncConfig;
  @JsonProperty("azureStorageSyncConfig") private AzureStorageSyncConfig azureStorageSyncConfig;
  @JsonProperty("podInfo") private PodInfoConfig podInfoConfig;
  @JsonProperty("billingDataPipelineConfig") private BillingDataPipelineConfig billingDataPipelineConfig;
  @JsonProperty("smtp") private SmtpConfig smtpConfig;
  @JsonProperty("segmentConfig") private SegmentConfig segmentConfig;
  @JsonProperty("reportScheduleConfig") private ReportScheduleConfig reportScheduleConfig;
  @JsonProperty("baseUrl") private String baseUrl;
  @JsonProperty("awsCurBilling") private boolean awsCurBilling;
  @JsonProperty("scheduler-jobs-config") private SchedulerJobsConfig schedulerJobsConfig;
  @JsonProperty("cfClientConfig") private CfClientConfig cfClientConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;
  @JsonProperty("cePythonService") private CEPythonServiceConfig cePythonServiceConfig;
  @JsonProperty("banzaiConfig") private BanzaiConfig banzaiConfig;
  @JsonProperty("ngManagerServiceHttpClientConfig") private ServiceHttpClientConfig ngManagerServiceHttpClientConfig;
  @JsonProperty("ngManagerServiceSecret") private String ngManagerServiceSecret;
  @JsonProperty("ceNgServiceHttpClientConfig") private ServiceHttpClientConfig ceNgServiceHttpClientConfig;
  @JsonProperty("ceNgServiceSecret") private String ceNgServiceSecret;
  @JsonProperty("banzaiRecommenderConfig") private ServiceHttpClientConfig banzaiRecommenderConfig;
  @JsonProperty("connectorHealthUpdateJobConfig") private ConnectorHealthUpdateJobConfig connectorHealthUpdateJobConfig;
  @JsonProperty("awsAccountTagsCollectionJobConfig")
  private AwsAccountTagsCollectionJobConfig awsAccountTagsCollectionJobConfig;
  @JsonProperty("gcpConfig") private GcpConfig gcpConfig;
  @JsonProperty("notificationClient") private NotificationClientConfiguration notificationClientConfiguration;
}
