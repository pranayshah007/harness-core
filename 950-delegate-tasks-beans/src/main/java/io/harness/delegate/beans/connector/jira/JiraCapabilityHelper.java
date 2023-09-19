/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.jira;

import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JiraCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ExpressionEvaluator maskingEvaluator, JiraConnectorDTO jiraConnectorDTO) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    String jiraUrl = jiraConnectorDTO.getJiraUrl();
    HttpConnectionExecutionCapability httpConnectionExecutionCapability =
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
            jiraUrl.endsWith("/") ? jiraUrl : jiraUrl.concat("/"), maskingEvaluator);
    httpConnectionExecutionCapability.setIgnoreRedirect(true);
    capabilityList.add(httpConnectionExecutionCapability);
    populateDelegateSelectorCapability(capabilityList, jiraConnectorDTO.getDelegateSelectors(),jiraConnectorDTO.getConnectorType());
    return capabilityList;
  }
}
