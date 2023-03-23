/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.hooks;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ServiceHookConstants {
  public static final String FETCH_FILES = "FetchFiles";
  public static final String TEMPLATE_MANIFEST = "TemplateManifest";
  public static final String STEADY_STATE_CHECK = "SteadyStateCheck";
  public static final String PRE_HOOK = "PreHook";
  public static final String POST_HOOK = "PostHook";
}
