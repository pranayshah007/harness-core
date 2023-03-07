/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import static io.harness.pcf.PcfUtils.encodeColor;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.pcf.artifact.TasArtifactConfig;
import io.harness.delegate.task.pcf.artifact.TasArtifactType;
import io.harness.delegate.task.pcf.request.CfCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.logging.LogCallback;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public abstract class CfCommandTaskNGHandler {
  protected static Yaml yaml;
  protected static final int MAX_RELEASE_VERSIONS_TO_KEEP = 3;
  protected final String CLOUD_FOUNDRY_LOG_PREFIX = "CLOUD_FOUNDRY_LOG_PREFIX: ";

  static {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setExplicitStart(true);
    yaml = new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(new DumperOptions()), options);
  }
  public CfCommandResponseNG executeTask(CfCommandRequestNG cfCommandRequestNG,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    return executeTaskInternal(cfCommandRequestNG, iLogStreamingTaskClient, commandUnitsProgress);
  }

  protected abstract CfCommandResponseNG executeTaskInternal(CfCommandRequestNG cfCommandRequestNG,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception;

  protected void printExistingApplicationsDetails(
      LogCallback executionLogCallback, List<ApplicationSummary> previousReleases) {
    if (EmptyPredicate.isEmpty(previousReleases)) {
      executionLogCallback.saveExecutionLog("# No Existing applications found");
    } else {
      StringBuilder appNames = new StringBuilder(color("# Existing applications: ", White, Bold));
      previousReleases.forEach(
          applicationSummary -> appNames.append("\n").append(encodeColor(applicationSummary.getName())));
      executionLogCallback.saveExecutionLog(appNames.toString());
    }
  }

  protected boolean isDockerArtifact(TasArtifactConfig tasArtifactConfig) {
    return TasArtifactType.CONTAINER == tasArtifactConfig.getArtifactType();
  }

  protected boolean isPackageArtifact(TasArtifactConfig tasArtifactConfig) {
    return TasArtifactType.PACKAGE == tasArtifactConfig.getArtifactType();
  }
}
