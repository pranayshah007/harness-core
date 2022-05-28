/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filter;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(DX)
public enum FilterType {
  @JsonProperty("Connector") CONNECTOR,
  @JsonProperty("DelegateProfile") DELEGATEPROFILE,
  @JsonProperty("Delegate") DELEGATE,
  @JsonProperty("PipelineSetup") PIPELINESETUP,
  @JsonProperty("PipelineExecution") PIPELINEEXECUTION,
  @JsonProperty("Deployment") DEPLOYMENT,
  @JsonProperty("Audit") AUDIT,
  @JsonProperty("Template") TEMPLATE,
  @JsonProperty("EnvironmentGroup") ENVIRONMENTGROUP,
  @JsonProperty("FileStore") FILESTORE,
  @JsonProperty("CCMRecommendation") CCMRECOMMENDATION,
  @JsonProperty("Anomaly") ANOMALY,
  @JsonProperty("Environment") ENVIRONMENT
}
