/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.beans.entities;

import io.harness.ng.core.dashboard.AuthorInfo;
import io.harness.ng.core.dashboard.GitInfo;
import io.harness.ng.core.dashboard.ServiceDeploymentInfo;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BuildActiveInfo {
  private String piplineName;
  private String pipelineIdentifier;
  private String branch;
  private String commit;
  private String commitID;
  private String triggerType;
  private AuthorInfo author;
  private Long startTs;
  private String status;
  private String planExecutionId;
  private Long endTs;
  private GitInfo gitInfo;
  private List<ServiceDeploymentInfo> serviceInfoList;
}
