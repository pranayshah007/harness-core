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

import software.wings.beans.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "DelegateTaskPackageKeys")
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public class DelegateTaskPackageV2 {
  private String id;
  @JsonProperty("type") private TaskType taskType;
  private Object data;
  private boolean async;
  private long timeout;
  // Do we need accountId, expressionFunctorToken, expressions?
  @JsonProperty("logging") private DelegateTaskLoggingV2 delegateTaskLogging;
  @JsonProperty("delegate") private DelegateInfoV2 delegate;

  // TODO: Remove encryptionConfigs and secretDetails in favour of List<EncryptedDataDetail>
  @JsonProperty("encryption_configs")
  @Default
  private Map<String, EncryptionConfig> encryptionConfigs = new HashMap<>();
  @JsonProperty("secret_details") @Default private Map<String, SecretDetail> secretDetails = new HashMap<>();
  @Default private Set<String> secrets = new HashSet<>();

  @JsonProperty("capabilities") private List<ExecutionCapability> executionCapabilities;
}
