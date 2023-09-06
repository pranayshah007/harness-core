/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.jira.JiraIssueCreateMetadataNG;
import io.harness.jira.JiraIssueNG;
import io.harness.jira.JiraIssueTransitionNG;
import io.harness.jira.JiraIssueUpdateMetadataNG;
import io.harness.jira.JiraProjectBasicNG;
import io.harness.jira.JiraStatusNG;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldDefaults(level = AccessLevel.PRIVATE)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
public class JiraTaskNGResponse implements DelegateTaskNotifyResponseData {
  List<JiraProjectBasicNG> projects;
  List<JiraStatusNG> statuses;
  List<JiraIssueTransitionNG> jiraIssueTransitionsNG;
  JiraIssueNG issue;
  JiraIssueCreateMetadataNG issueCreateMetadata;
  JiraIssueUpdateMetadataNG issueUpdateMetadata;
  JiraSearchUserData jiraSearchUserData;

  DelegateMetaInfo delegateMetaInfo;
}
