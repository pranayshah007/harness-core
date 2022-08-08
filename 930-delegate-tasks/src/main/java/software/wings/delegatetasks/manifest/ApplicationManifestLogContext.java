/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.manifest;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.AutoLogContext;

import com.google.common.collect.ImmutableMap;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ApplicationManifestLogContext extends AutoLogContext {
  public static final String ID = "applicationManifestId";
  public static final String SERVICE_ID = "serviceId";

  public ApplicationManifestLogContext(String appManifestId, String serviceId, OverrideBehavior behavior) {
    super(ImmutableMap.of(ID, appManifestId, SERVICE_ID, serviceId), behavior);
  }

  public ApplicationManifestLogContext(String appManifestId, OverrideBehavior behavior) {
    super(ImmutableMap.of(ID, appManifestId), behavior);
  }
}
