/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.handlermapping.context;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared runtime data across handlers
 */
@Singleton
public class Context {
  public static final String DELEGATE_ID = "delegateId";
  public static final String TASK_ID = "taskId";

  private Map<String, String> context;

  public Context() {
    this.context = new HashMap<>();
  }

  private Context(Map<String, String> contents) {
    this.context = contents;
  }

  public String get(String key) {
    return context.get(key);
  }

  public void set(String key, String val) {
    context.put(key, val);
  }

  public Context deepCopy() {
    Map<String, String> contents = new HashMap<>();
    contents.putAll(context);
    return new Context(contents);
  }
}
