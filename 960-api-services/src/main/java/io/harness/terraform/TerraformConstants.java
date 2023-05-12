/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terraform;

import java.util.concurrent.TimeUnit;

public class TerraformConstants {
  static final long DEFAULT_TERRAFORM_COMMAND_TIMEOUT = TimeUnit.MINUTES.toMillis(30);
  static final String INIT = "INIT";
  static final String WORKSPACE = "WORKSPACE";
  static final String REFRESH = "REFRESH";
  static final String PLAN = "PLAN";
  static final String APPLY = "APPLY";
  static final String DESTROY = "DESTROY";
}
