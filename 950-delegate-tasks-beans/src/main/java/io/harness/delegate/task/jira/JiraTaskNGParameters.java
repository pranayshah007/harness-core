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
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.mixin.JiraCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.jira.JiraActionNG;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JiraTaskNGParameters implements TaskParameters, ExecutionCapabilityDemander {
  JiraConnectorDTO jiraConnectorDTO;
  List<EncryptedDataDetail> encryptionDetails;
  JiraSearchUserParams jiraSearchUserParams;

  JiraActionNG action;
  String projectKey;
  String issueType;
  String issueKey;
  // Jira apis have an expand query param to fetch more information. Look at JiraClient apis to know more.
  String expand;
  // Fetch status along with create metadata.
  boolean fetchStatus;
  // Do not fetch comment along with create metadata.
  boolean ignoreComment;

  // Transition to do while updating (optional).
  String transitionToStatus; // required in a transition
  String transitionName; // optional - find a particular transition that goes to desired status

  // Fields sent while creating/updating issue.
  Map<String, String> fields;
  // For new jira server versions old metadata endpoint is deprecated
  boolean newMetadata;
  List<String> delegateSelectors;

  public Set<String> getDelegateSelectors() {
    Set<String> combinedDelegateSelectors = new HashSet<>();
    if (jiraConnectorDTO != null && jiraConnectorDTO.getDelegateSelectors() != null) {
      combinedDelegateSelectors.addAll(jiraConnectorDTO.getDelegateSelectors());
    }
    if (delegateSelectors != null) {
      combinedDelegateSelectors.addAll(delegateSelectors);
    }
    return combinedDelegateSelectors;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return JiraCapabilityGenerator.generateDelegateCapabilities(jiraConnectorDTO, encryptionDetails, maskingEvaluator);
  }
}
