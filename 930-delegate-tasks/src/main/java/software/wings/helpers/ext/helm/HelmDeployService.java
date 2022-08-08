/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.helm.HelmCommandResponse;

import software.wings.helpers.ext.helm.request.HelmCommandRequest;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.helpers.ext.helm.request.HelmReleaseHistoryCommandRequest;
import software.wings.helpers.ext.helm.request.HelmRollbackCommandRequest;
import software.wings.helpers.ext.helm.response.HelmListReleasesCommandResponse;
import software.wings.helpers.ext.helm.response.HelmReleaseHistoryCommandResponse;

import java.io.IOException;
import java.util.List;

/**
 * Created by anubhaw on 4/1/18.
 */
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public interface HelmDeployService {
  /**
   * Deploy helm command response.
   *
   * @param commandRequest       the command request
   * @return the helm command response
   */
  HelmCommandResponse deploy(HelmInstallCommandRequest commandRequest) throws IOException;

  /**
   * Rollback helm command response.
   *
   * @param commandRequest       the command request
   * @return the helm command response
   */
  HelmCommandResponse rollback(HelmRollbackCommandRequest commandRequest);

  /**
   * Ensure helm cli and tiller installed helm command response.
   *
   * @param helmCommandRequest   the helm command request
   * @return the helm command response
   */
  HelmCommandResponse ensureHelmCliAndTillerInstalled(HelmCommandRequest helmCommandRequest);

  /**
   * Last successful release version string.
   *
   * @param helmCommandRequest the helm command request
   * @return the string
   */
  HelmListReleasesCommandResponse listReleases(HelmInstallCommandRequest helmCommandRequest);

  /**
   * Release history helm release history command response.
   *
   * @param helmCommandRequest the helm command request
   * @return the helm release history command response
   */
  HelmReleaseHistoryCommandResponse releaseHistory(HelmReleaseHistoryCommandRequest helmCommandRequest);

  HelmCommandResponse addPublicRepo(HelmCommandRequest commandRequest) throws Exception;

  /**
   * Render chart templates and return the output.
   *
   * @param helmCommandRequest the helm command request
   * @param namespace the namespace
   * @param chartLocation the chart location
   * @param valueOverrides the value overrides
   * @return the helm release history command response
   */
  HelmCommandResponse renderHelmChart(HelmCommandRequest helmCommandRequest, String namespace, String chartLocation,
      List<String> valueOverrides) throws Exception;

  HelmCommandResponse ensureHelm3Installed(HelmCommandRequest commandRequest);

  HelmCommandResponse ensureHelmInstalled(HelmCommandRequest commandRequest);
}
