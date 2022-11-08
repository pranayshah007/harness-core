/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCapabilityHelper;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandTypeNG;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.CDP)
public interface ElastigroupCommandRequest extends TaskParameters, ExecutionCapabilityDemander {
  String getAccountId();
  @NotEmpty ElastigroupCommandTypeNG getElastigroupCommandType();
  String getCommandName();
  CommandUnitsProgress getCommandUnitsProgress();
  Integer getTimeoutIntervalInMin();
  SpotInstConfig getSpotInstConfig();

  @Override
  default List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    SpotInstConfig spotInstConfig = getSpotInstConfig();
    List<EncryptedDataDetail> spotInstConfigEncryptionDataDetails = spotInstConfig.getEncryptionDataDetails();

    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                spotInstConfigEncryptionDataDetails, maskingEvaluator));

    SpotConnectorDTO spotConnectorDTO = spotInstConfig.getSpotConnectorDTO();
    capabilities.addAll(SpotCapabilityHelper.fetchRequiredExecutionCapabilities(spotConnectorDTO, maskingEvaluator));
    return capabilities;
  }
}
