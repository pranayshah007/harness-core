/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._957_CG_BEANS)
public class AzureContainerRegistry extends AzureResourceReference {
  @Builder
  private AzureContainerRegistry(
      String name, String resourceGroup, String subscriptionId, String type, String id, String loginServer) {
    super(name, resourceGroup, subscriptionId, type, id);
    this.loginServer = loginServer;
  };

  private String loginServer;
}
