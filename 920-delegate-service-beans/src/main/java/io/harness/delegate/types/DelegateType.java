/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.types;

public final class DelegateType {
  public static final String SHELL_SCRIPT = "SHELL_SCRIPT";
  public static final String DOCKER = "DOCKER";
  public static final String KUBERNETES = "KUBERNETES";
  public static final String CE_KUBERNETES = "CE_KUBERNETES";
  public static final String HELM_DELEGATE = "HELM_DELEGATE";
  public static final String ECS = "ECS";

  private DelegateType() {}
}
