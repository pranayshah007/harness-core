/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.rancher;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class RancherTaskCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public static List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      RancherConnectorDTO clusterConfig, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    RancherConnectorConfigAuthDTO clusterConfigDetails = clusterConfig.getConfig().getConfig();
    if (clusterConfigDetails.getCredentials().getAuthType() == RancherAuthType.BEARER_TOKEN) {
      capabilityList.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
          clusterConfigDetails.getRancherUrl(), maskingEvaluator));
    } else {
      throw new UnknownEnumTypeException(
          "Rancher credential type", clusterConfigDetails.getCredentials().getAuthType().toString());
    }
    populateDelegateSelectorCapability(capabilityList, clusterConfig.getDelegateSelectors());
    return capabilityList;
  }
}
