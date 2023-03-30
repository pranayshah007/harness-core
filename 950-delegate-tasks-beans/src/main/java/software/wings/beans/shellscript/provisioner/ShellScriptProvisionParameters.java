/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.shellscript.provisioner;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class ShellScriptProvisionParameters implements TaskParameters, ActivityAccess, ExecutionCapabilityDemander {
  @Expression(ALLOW_SECRETS) private String scriptBody;
  private long timeoutInMillis;
  private Map<String, String> textVariables;
  private Map<String, EncryptedDataDetail> encryptedVariables;
  private String entityId;
  private String workflowExecutionId;

  private String accountId;
  private String appId;
  private String activityId;
  private String commandUnit;
  private List<String> delegateSelectors;

  /*
  Name of the variable which contains the file path
   */
  private String outputPathKey;
  private boolean useSshAgent;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();

    if (isNotEmpty(encryptedVariables)) {
      for (EncryptedDataDetail encryptedDataDetail : encryptedVariables.values()) {
        executionCapabilities.addAll(
            EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                Arrays.asList(encryptedDataDetail), maskingEvaluator));
      }
    }
    if (isNotEmpty(delegateSelectors)) {
      executionCapabilities.add(SelectorCapability.builder().selectors(new HashSet<>(delegateSelectors)).build());
    }

    return executionCapabilities;
  }
}
