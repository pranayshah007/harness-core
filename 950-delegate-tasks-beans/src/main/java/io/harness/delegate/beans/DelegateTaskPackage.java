/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.security.encryption.EncryptionConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "DelegateTaskPackageKeys")
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
@ToString(of = {"delegateId", "delegateInstanceId", "delegateTaskId"})
public class DelegateTaskPackage {
  private String accountId;
  private String delegateTaskId;
  private String delegateId;
  private String delegateInstanceId;
  private String logStreamingToken;
  private String delegateCallbackToken;

  private TaskData data;

  @Default private Map<String, EncryptionConfig> encryptionConfigs = new HashMap<>();
  @Default private Map<String, SecretDetail> secretDetails = new HashMap<>();
  @Default private Set<String> secrets = new HashSet<>();

  private List<ExecutionCapability> executionCapabilities;
  private LinkedHashMap<String, String> logStreamingAbstractions;
}
