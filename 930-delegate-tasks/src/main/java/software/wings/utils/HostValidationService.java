/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.ExecutionCredential;
import software.wings.beans.HostReachabilityInfo;
import software.wings.beans.HostValidationResponse;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.TaskType;
import software.wings.beans.dto.SettingAttribute;
import software.wings.delegatetasks.DelegateTaskType;

import java.util.List;

public interface HostValidationService {
  @DelegateTaskType(TaskType.HOST_VALIDATION)
  List<HostValidationResponse> validateHost(List<String> hostNames, SettingAttribute connectionSetting,
      List<EncryptedDataDetail> encryptionDetails, ExecutionCredential executionCredential,
      SSHVaultConfig sshVaultConfig);

  List<HostReachabilityInfo> validateReachability(List<String> hostNames, SettingAttribute connectionSetting);
  List<HostReachabilityInfo> validateReachability(List<String> hostNames, int port);
}
