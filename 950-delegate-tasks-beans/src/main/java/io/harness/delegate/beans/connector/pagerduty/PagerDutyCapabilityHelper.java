/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.pagerduty;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.cvng.pagerduty.PagerDutyUtils;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CV)
public class PagerDutyCapabilityHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ExpressionEvaluator maskingEvaluator, ConnectorConfigDTO connectorConfigDTO) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    PagerDutyConnectorDTO connectorDTO = (PagerDutyConnectorDTO) connectorConfigDTO;
    capabilityList.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        PagerDutyUtils.getBaseUrl(), maskingEvaluator));
    populateDelegateSelectorCapability(capabilityList, connectorDTO.getDelegateSelectors(),connectorDTO.getConnectorType() );
    return capabilityList;
  }
}
