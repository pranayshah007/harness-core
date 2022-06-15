/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import com.google.inject.Singleton;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Singleton
public class BillingDataPipelineConfig {
  private String gcpProjectId;
  private String gcsBasePath;
  private String gcpPipelinePubSubTopic;
  private String gcpSyncPubSubTopic;
  private boolean gcpUseNewPipeline;
  private boolean isGcpSyncEnabled;
  private String clusterDataGcsBucketName;
  private String clusterDataGcsBackupBucketName;
  private boolean awsUseNewPipeline;
  private String awsRoleName;

  public String getGcpPipelinePubSubTopic() {
    return "projects/" + gcpProjectId + "/topics/" + gcpPipelinePubSubTopic;
  }
}
