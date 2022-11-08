/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ConnectivityCapabilityDemander;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SocketConnectivityBulkOrExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.dto.SettingAttribute;
import software.wings.settings.SettingValue;

import java.util.Collection;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class HostValidationTaskParameters implements ExecutionCapabilityDemander {
  List<String> hostNames;
  SettingAttribute connectionSetting;
  List<EncryptedDataDetail> encryptionDetails;
  ExecutionCredential executionCredential;
  boolean checkOnlyReachability;
  boolean checkOr;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    if (connectionSetting == null) {
      return EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          encryptionDetails, maskingEvaluator);
    }
    SettingValue settingValue = connectionSetting.getValue();
    int port = 22;
    if (settingValue instanceof WinRmConnectionAttributes) {
      port = ((WinRmConnectionAttributes) settingValue).getPort();
    } else if (settingValue instanceof HostConnectionAttributes) {
      port = ((HostConnectionAttributes) settingValue).getSshPort();
    }
    final int portf = port;
    List<ExecutionCapability> capabilities;
    if (checkOr) {
      capabilities =
          singletonList(SocketConnectivityBulkOrExecutionCapability.builder().hostNames(hostNames).port(portf).build());
    } else {
      List<List<ExecutionCapability>> capabilityDemanders =
          hostNames.stream()
              .map(host
                  -> new ConnectivityCapabilityDemander(host, portf)
                         .fetchRequiredExecutionCapabilities(maskingEvaluator))
              .collect(toList());
      capabilities = capabilityDemanders.stream().flatMap(Collection::stream).collect(toList());
    }
    return capabilities;
  }
}
