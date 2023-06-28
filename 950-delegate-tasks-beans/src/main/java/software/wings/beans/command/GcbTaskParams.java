/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GcpConfig;
import software.wings.beans.GitConfig;
import software.wings.sm.states.gcbconfigs.GcbOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@Data
@Builder
public class GcbTaskParams implements ExecutionCapabilityDemander {
  public enum GcbTaskType { START, POLL, CANCEL, FETCH_TRIGGERS }

  @Nullable private String appId;
  @Nullable private String unitName;
  @Nullable private GcbTaskType type;
  @Nullable private String activityId;
  @Nullable private GcpConfig gcpConfig;
  @Nullable private List<EncryptedDataDetail> encryptedDataDetails;
  @Nullable private GcbOptions gcbOptions;
  @Nullable private String gcpConfigId;
  @Nullable private String accountId;

  @Nullable private GitConfig gitConfig;
  @Nullable private String buildId;
  @Nullable private String buildName;
  @Nullable private Map<String, String> substitutions;
  private long timeout;
  private long startTs;
  private boolean injectEnvVars;
  @Builder.Default private int pollFrequency = 5;
  private boolean timeoutSupported;

  @NotNull
  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities =
        new ArrayList<>(gcpConfig.fetchRequiredExecutionCapabilities(maskingEvaluator));
    Set<String> selectors = new HashSet<>();
    if (gcpConfig.isUseDelegateSelectors()) {
      selectors.addAll(gcpConfig.getDelegateSelectors());
    }
    if (gitConfig != null && isNotEmpty(gitConfig.getDelegateSelectors())) {
      selectors.addAll(gitConfig.getDelegateSelectors());
    }

    if (!selectors.isEmpty()) {
      executionCapabilities.add(SelectorCapability.builder().selectors(selectors).build());
    }

    return executionCapabilities;
  }
}
