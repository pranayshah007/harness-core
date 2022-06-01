/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

public class DelegateAgentCommonVariables {
  private static volatile String delegateId;
  private static String delegateTokenName;
  private static final String UNREGISTERED = "Unregistered";

  public static void setDelegateId(String registeredDelegateId) {
    delegateId = registeredDelegateId;
  }

  public static String getDelegateId() {
    return isEmpty(delegateId) ? UNREGISTERED : delegateId;
  }

  public static void setDelegateTokenName(String tokenName) {
    delegateTokenName = tokenName;
  }

  public static String getDelegateTokenName() {
    return isEmpty(delegateTokenName) ? UNREGISTERED : delegateTokenName;
  }
}
