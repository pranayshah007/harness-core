/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.hooks;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;

@OwnedBy(CDP)
public class ServiceHooks {
  List<ServiceHook> serviceHooks;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public ServiceHooks(List<ServiceHook> serviceHooks) {
    this.serviceHooks = serviceHooks;
  }
}
