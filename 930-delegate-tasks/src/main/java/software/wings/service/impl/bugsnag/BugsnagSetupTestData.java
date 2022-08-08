/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.bugsnag;

import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.impl.analysis.SetupTestNodeData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Created by Pranjal on 04/09/2019
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class BugsnagSetupTestData extends SetupTestNodeData {
  private String orgId;
  private String projectId;
  private String releaseStage;
  private String query;
  private boolean browserApplication;

  @Builder
  public BugsnagSetupTestData(String appId, String settingId, String instanceName, boolean isServiceLevel,
      Instance instanceElement, String hostExpression, String workflowId, String guid, DelegateStateType stateType,
      long fromTime, long toTime, String orgId, String projectId, String releaseStage, String query,
      boolean browserApplication) {
    super(appId, settingId, instanceName, isServiceLevel, instanceElement, hostExpression, workflowId, guid, stateType,
        fromTime, toTime);
    this.orgId = orgId;
    this.projectId = projectId;
    this.releaseStage = releaseStage;
    this.query = query;
    this.browserApplication = browserApplication;
  }
}
