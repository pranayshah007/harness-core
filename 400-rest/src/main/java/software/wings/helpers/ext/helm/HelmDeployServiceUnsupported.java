/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.helm.HelmCommandResponse;

import software.wings.delegatetasks.validation.capabilities.HelmCommandRequest;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.helpers.ext.helm.request.HelmReleaseHistoryCommandRequest;
import software.wings.helpers.ext.helm.request.HelmRollbackCommandRequest;
import software.wings.helpers.ext.helm.response.HelmListReleasesCommandResponse;
import software.wings.helpers.ext.helm.response.HelmReleaseHistoryCommandResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@OwnedBy(CDP)
public class HelmDeployServiceUnsupported implements HelmDeployService {
  @Override
  public HelmCommandResponse deploy(HelmInstallCommandRequest commandRequest) {
    throw new UnsupportedOperationException("Helm deploy service not supported on manager");
  }

  @Override
  public HelmCommandResponse rollback(HelmRollbackCommandRequest commandRequest) {
    throw new UnsupportedOperationException("Helm deploy service not supported on manager");
  }

  @Override
  public HelmCommandResponse ensureHelmCliAndTillerInstalled(HelmCommandRequest helmCommandRequest) {
    throw new UnsupportedOperationException("Helm deploy service not supported on manager");
  }

  @Override
  public HelmListReleasesCommandResponse listReleases(HelmInstallCommandRequest helmCommandRequest) {
    throw new UnsupportedOperationException("Helm deploy service not supported on manager");
  }

  @Override
  public HelmReleaseHistoryCommandResponse releaseHistory(HelmReleaseHistoryCommandRequest helmCommandRequest) {
    throw new UnsupportedOperationException("Helm deploy service not supported on manager");
  }

  @Override
  public HelmCommandResponse addPublicRepo(HelmCommandRequest commandRequest)
      throws InterruptedException, IOException, TimeoutException {
    throw new UnsupportedOperationException("Helm deploy service not supported on manager");
  }

  @Override
  public HelmCommandResponse renderHelmChart(HelmCommandRequest commandRequest, String namespace, String chartLocation,
      List<String> valueOverrides) throws InterruptedException, TimeoutException, IOException, ExecutionException {
    throw new UnsupportedOperationException("Helm deploy service not supported on manager");
  }

  @Override
  public HelmCommandResponse ensureHelm3Installed(HelmCommandRequest commandRequest) {
    throw new UnsupportedOperationException("Helm deploy service not supported on manager");
  }

  @Override
  public HelmCommandResponse ensureHelmInstalled(HelmCommandRequest commandRequest) {
    throw new UnsupportedOperationException("Helm deploy service not supported on manager");
  }
}
