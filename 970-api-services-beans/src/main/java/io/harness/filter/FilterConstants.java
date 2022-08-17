/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filter;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(DX)
public class FilterConstants {
  public static final String CONNECTOR_FILTER = "Connector";
  public static final String PIPELINE_SETUP_FILTER = "PipelineSetup";
  public static final String TEMPLATE_FILTER = "Template";
  public static final String PIPELINE_EXECUTION_FILTER = "PipelineExecution";
  public static final String DEPLOYMENT_FILTER = "Deployment";
  public static final String AUDIT_FILTER = "Audit";
  public static final String DELEGATE_PROFILE_FILTER = "DelegateProfile";
  public static final String DELEGATE_FILTER = "Delegate";
  public static final String ENVIRONMENT_GROUP_FILTER = "EnvironmentGroup";
  public static final String FILE_STORE_FILTER = "FileStore";
  public static final String CCM_RECOMMENDATION_FILTER = "CCMRecommendation";
  public static final String ANOMALY_FILTER = "Anomaly";
  public static final String ENVIRONMENT = "Environment";
}
