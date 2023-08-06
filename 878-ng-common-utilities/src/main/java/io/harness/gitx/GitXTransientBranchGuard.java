/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitx;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitaware.helper.GitAwareContextHelper;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
public class GitXTransientBranchGuard implements AutoCloseable {
  public GitXTransientBranchGuard(String transientBranch) {
    GitAwareContextHelper.setTransientBranch(transientBranch);
  }

  @Override
  public void close() {
    GitAwareContextHelper.resetTransientBranch();
  }
}
