/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.rbac;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class NGResourceType {
  public final String SERVICE = "SERVICE";
  public final String PIPELINE = "PIPELINE";
  public final String ENVIRONMENT = "ENVIRONMENT";
  public final String CONNECTOR = "CONNECTOR";
  public final String SECRETS = "SECRET";
  public final String ENVIRONMENT_GROUP = "ENVIRONMENT_GROUP";
  public final String FILE = "FILE";
  public final String VARIABLE = "VARIABLE";
  public final String SETTING = "SETTING";
}
