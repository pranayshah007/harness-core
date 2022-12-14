/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terragrunt.v2;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cli.CliResponse;
import io.harness.logging.LogCallback;
import io.harness.terragrunt.v2.request.TerragruntCliRequest;
import io.harness.terragrunt.v2.request.TerragruntPlanCliRequest;
import io.harness.terragrunt.v2.request.TerragruntShowCliRequest;
import io.harness.terragrunt.v2.request.TerragruntWorkspaceCliRequest;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

@OwnedBy(CDP)
public interface TerragruntClient {
  @Nonnull
  CliResponse init(@Nonnull TerragruntCliRequest request, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse workspace(@Nonnull TerragruntWorkspaceCliRequest request, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse refresh(@Nonnull TerragruntCliRequest request, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse plan(@Nonnull TerragruntPlanCliRequest request, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse show(@Nonnull TerragruntShowCliRequest request, @Nonnull LogCallback logCallback)
      throws IOException, InterruptedException, TimeoutException;

  @Nonnull String terragruntWorkingDirectory();
}
