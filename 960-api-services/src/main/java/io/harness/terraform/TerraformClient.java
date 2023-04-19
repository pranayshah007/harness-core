/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cli.CliResponse;
import io.harness.logging.LogCallback;
import io.harness.logging.PlanHumanReadableOutputStream;
import io.harness.logging.PlanJsonLogOutputStream;
import io.harness.logging.PlanLogOutputStream;
import io.harness.terraform.beans.TerraformVersion;
import io.harness.terraform.request.TerraformApplyCommandRequest;
import io.harness.terraform.request.TerraformDestroyCommandRequest;
import io.harness.terraform.request.TerraformInitCommandRequest;
import io.harness.terraform.request.TerraformPlanCommandRequest;
import io.harness.terraform.request.TerraformRefreshCommandRequest;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

@OwnedBy(CDP)
public interface TerraformClient {
  @Nonnull
  CliResponse init(TerraformInitCommandRequest terraformInitCommandRequest, long timeoutInMillis,
      Map<String, String> envVariables, String scriptDirectory, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse destroy(TerraformDestroyCommandRequest terraformDestroyCommandRequest, long timeoutInMillis,
      Map<String, String> envVariables, String scriptDirectory, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse refresh(TerraformRefreshCommandRequest terraformRefreshCommandRequest, long timeoutInMillis,
      Map<String, String> envVariables, String scriptDirectory, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse plan(TerraformPlanCommandRequest terraformPlanCommandRequest, long timeoutInMillis,
      Map<String, String> envVariables, String scriptDirectory, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse apply(TerraformApplyCommandRequest terraformApplyCommandRequest, long timeoutInMillis,
      Map<String, String> envVariables, String scriptDirectory, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse workspace(String workspace, boolean isNew, long timeoutInMillis, Map<String, String> envVariables,
      String scriptDirectory, @Nonnull LogCallback executionLogCallback, Map<String, String> additionalCliFlags)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse getWorkspaceList(long timeoutInMillis, Map<String, String> envVariables, String scriptDirectory,
      @Nonnull LogCallback executionLogCallback) throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse show(String planName, long timeoutInMillis, Map<String, String> envVariables, String scriptDirectory,
      @Nonnull LogCallback executionLogCallback, @Nonnull PlanJsonLogOutputStream planJsonLogOutputStream)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse show(String planName, long timeoutInMillis, Map<String, String> envVariables, String scriptDirectory,
      @Nonnull LogCallback executionLogCallback, @Nonnull PlanLogOutputStream planLogOutputStream)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse prepareHumanReadablePlan(String planName, long timeoutInMillis, Map<String, String> envVariables,
      String scriptDirectory, @Nonnull LogCallback executionLogCallback,
      @Nonnull PlanHumanReadableOutputStream planHumanReadableOutputStream)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse output(String tfOutputsFile, long timeoutInMillis, Map<String, String> envVariables,
      String scriptDirectory, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  TerraformVersion version(long timeoutInMillis, String scriptDirectory)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  TerraformVersion version(String tfBinaryPath, long timeoutInMillis, String scriptDirectory)
      throws InterruptedException, TimeoutException, IOException;
}
