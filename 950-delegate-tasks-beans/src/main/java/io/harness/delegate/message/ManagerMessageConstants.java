/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.message;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

@TargetModule(HarnessModule._920_DELEGATE_AGENT_BEANS)
public interface ManagerMessageConstants {
  // Messages sent from manager to delegate
  String SELF_DESTRUCT = "[SELF_DESTRUCT]";
  String MIGRATE = "[MIGRATE]";
  String USE_CDN = "[USE_CDN]";
  String USE_STORAGE_PROXY = "[USE_STORAGE_PROXY]";
  String JRE_VERSION = "[JRE_VERSION]";
  String UPDATE_PERPETUAL_TASK = "[UPDATE_PERPETUAL_TASK]";
  String MONGO_TIMEOUT = "[MONGO_TIMEOUT]";
}
