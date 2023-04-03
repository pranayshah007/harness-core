/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public class Constants {
  private Constants() {}

  public static final String IDP_PREFIX = "idp_";
  public static final String IDP_RESOURCE_TYPE = "IDP_SETTINGS";
  public static final String IDP_PERMISSION = "idp_idpsettings_manage";
  public static final List<String> pluginIds =
      List.of("github-pull-requests-board", "pager-duty", "snyk-security", "harness-ci-cd");
}
