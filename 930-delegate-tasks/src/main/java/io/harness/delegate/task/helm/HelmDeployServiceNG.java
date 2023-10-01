/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;

import java.io.IOException;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public interface HelmDeployServiceNG {
  void setTaskId(String taskId);

  void setLogStreamingClient(ILogStreamingTaskClient iLogStreamingTaskClient);

  void setTaskProgressStreamingClient(ILogStreamingTaskClient iLogStreamingTaskClient);

  HelmCommandResponseNG deploy(HelmInstallCommandRequestNG commandRequest) throws IOException;

  /**
   * Rollback helm command response.
   *
   * @param commandRequest       the command request
   * @return the helm command response
   */
  HelmCommandResponseNG rollback(HelmRollbackCommandRequestNG commandRequest) throws Exception;

  /**
   * Ensure helm cli and tiller installed helm command response.
   *
   * @param helmCommandRequest   the helm command request
   * @return the helm command response
   */
  HelmCommandResponseNG ensureHelmCliAndTillerInstalled(HelmCommandRequestNG helmCommandRequest);

  /**
   * Last successful release version string.
   *
   * @param helmCommandRequest the helm command request
   * @return the string
   */
  HelmListReleaseResponseNG listReleases(HelmInstallCommandRequestNG helmCommandRequest);

  /**
   * Release history helm release history command response.
   *
   * @param helmCommandRequest the helm command request
   * @return the helm release history command response
   */
  HelmReleaseHistoryCmdResponseNG releaseHistory(HelmReleaseHistoryCommandRequestNG helmCommandRequest);

  /**
   * Render chart templates and return the output.
   *
   * @param helmCommandRequest the helm command request
   * @param namespace the namespace
   * @param chartLocation the chart location
   * @param valueOverrides the value overrides
   * @return the helm release history command response
   */
  HelmCommandResponseNG renderHelmChart(HelmCommandRequestNG helmCommandRequest, String namespace, String chartLocation,
      List<String> valueOverrides) throws Exception;

  HelmCommandResponseNG ensureHelm3Installed(HelmCommandRequestNG commandRequest);

  HelmCommandResponseNG ensureHelmInstalled(HelmCommandRequestNG commandRequest);
}
