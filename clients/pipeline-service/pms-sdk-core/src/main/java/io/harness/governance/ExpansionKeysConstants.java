/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class ExpansionKeysConstants {
  public String CONNECTOR_EXPANSION_KEY = "connector";
  public String SERVICE_EXPANSION_KEY = "service";
  public String ENV_EXPANSION_KEY = "environment";
  public String MULTI_ENV_EXPANSION_KEY = "environments";
  public String INFRA_EXPANSION_KEY = "infrastructure";
}
