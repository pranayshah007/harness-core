/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class K8sSteadyStateConstants {
  public static final Integer WATCH_CALL_TIMEOUT_SECONDS = 300;
  public static final Pattern RESOURCE_VERSION_PATTERN =
      Pattern.compile("Timeout: Too large resource version: (\\d+), current: (\\d+)");
}
