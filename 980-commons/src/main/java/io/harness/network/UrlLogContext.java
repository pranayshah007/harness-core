/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.network;

import io.harness.logging.logcontext.AutoLogContext;

public class UrlLogContext extends AutoLogContext {
  public static final String URL = "URL";

  public UrlLogContext(String url, OverrideBehavior behavior) {
    super(URL, url, behavior);
  }
}
