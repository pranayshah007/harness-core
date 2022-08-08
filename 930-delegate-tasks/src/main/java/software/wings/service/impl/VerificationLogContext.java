/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.delegatetasks.DelegateStateType;

import java.util.HashMap;
import java.util.Map;

public class VerificationLogContext extends AutoLogContext {
  public static final String cvConfigIdKeyword = "cvConfigId";
  public static final String stateTypeKeyword = "stateType";

  private static Map<String, String> getContext(
      String accountId, String cvConfigId, String stateExecutionId, DelegateStateType stateType) {
    Map<String, String> contextMap = new HashMap<>();
    contextMap.put(AccountLogContext.ID, accountId);
    if (cvConfigId != null) {
      contextMap.put(cvConfigIdKeyword, cvConfigId);
    }
    if (stateExecutionId != null) {
      contextMap.put(StateExecutionInstanceLogContext.ID, stateExecutionId);
    }
    if (stateType != null) {
      contextMap.put(stateTypeKeyword, stateType.getName());
    }
    return contextMap;
  }

  public VerificationLogContext(String accountId, String cvConfigId, String stateExecutionId,
      DelegateStateType stateType, OverrideBehavior behavior) {
    super(getContext(accountId, cvConfigId, stateExecutionId, stateType), behavior);
  }
}
