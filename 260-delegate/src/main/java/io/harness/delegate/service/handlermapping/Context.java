/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.handlermapping;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;

// TODO: We can create a global context object which contains all the in-mmeory sharable configurations defined in
// DelegateAgentImpl.class Shared data across handlers
@Singleton
public class Context {
  public static final String DELEGATE_ID = "delegateId";
  public static final String DELEGATE_NAME = "delegateName";
  public static final String DELEGATE_INSTANCE_ID = "delegateInstanceId";
  public static final String ACCOUNT_ID = "accountId";

  private Map<String, String> context = new HashMap<>();

  public String get(String key) {
    return context.get(key);
  }

  public void set(String key, String val) {
    context.put(key, val);
  }
}
