/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.splunkconnector;

import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SplunkCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public static String SERVER_INFO_URL = "services/server/info?output_mode=json";
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorConfigDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    SplunkConnectorDTO splunkConnectorDTO = (SplunkConnectorDTO) connectorConfigDTO;
    final String splunkUrl = splunkConnectorDTO.getSplunkUrl() + SERVER_INFO_URL;
    capabilityList.add(
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapabilityWithIgnoreResponseCode(
            splunkUrl, maskingEvaluator, true));
    populateDelegateSelectorCapability(capabilityList, splunkConnectorDTO.getDelegateSelectors());
    return capabilityList;
  }
}
