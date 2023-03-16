/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.kustomize;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public final class KustomizeConstants {
  static final String COMMAND_FLAGS = "${COMMAND_FLAGS}";
  static final String KUSTOMIZE_BINARY_PATH = "${KUSTOMIZE_BINARY_PATH}";
  static final String KUSTOMIZE_DIR_PATH = "${DIR_PATH}";
  static final String KUSTOMIZE_PLUGIN_FLAG = "${KUSTOMIZE_PLUGIN_FLAG}";
  static final String KUSTOMIZE_BUILD_COMMAND =
      KUSTOMIZE_BINARY_PATH + " build " + KUSTOMIZE_DIR_PATH + " " + COMMAND_FLAGS;
  static final String XDG_CONFIG_HOME = "${XDG_CONFIG_HOME}";
  static final String KUSTOMIZE_BUILD_COMMAND_WITH_PLUGINS = "XDG_CONFIG_HOME=" + XDG_CONFIG_HOME + " "
      + KUSTOMIZE_BINARY_PATH + " build " + KUSTOMIZE_DIR_PATH + " " + KUSTOMIZE_PLUGIN_FLAG + " " + COMMAND_FLAGS;
  static final long KUSTOMIZE_COMMAND_TIMEOUT = TimeUnit.MINUTES.toMillis(1);
  static final String KUSTOMIZE_PLUGIN_FLAG_VERSION_LT_4_0_1 = "--enable_alpha_plugins";
  static final String KUSTOMIZE_PLUGIN_FLAG_LATEST = "--enable-alpha-plugins";
  static final String KUSTOMIZE_VERSION_V4_0_0 = "4.0.0";
}
