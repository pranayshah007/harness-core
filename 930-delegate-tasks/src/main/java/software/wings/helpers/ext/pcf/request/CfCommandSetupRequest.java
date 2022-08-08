/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.pcf.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.pcf.model.CfCliVersion;

import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactStreamAttributes;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * This class contains all required data for PCFCommandTask.SETUP to perform setup task
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class CfCommandSetupRequest extends CfCommandRequest {
  /**
   * releasePrefixName is (appId_serviceId_envId), while creating new version of app,
   * we will add 1 to most recent version deployed,
   * so actual app name will be appId_serviceId_envId__version
   */
  private String releaseNamePrefix;
  private String manifestYaml;
  private List<ArtifactFile> artifactFiles;
  private ArtifactStreamAttributes artifactStreamAttributes;
  private List<String> tempRouteMap;
  private List<String> routeMaps;
  private Map<String, String> serviceVariables;
  private Map<String, String> safeDisplayServiceVariables;
  private Integer maxCount;
  private Integer currentRunningCount;
  private boolean useCurrentCount;
  private boolean blueGreen;
  private Integer olderActiveVersionCountToKeep;
  private PcfManifestsPackage pcfManifestsPackage;
  private String artifactProcessingScript;
  private boolean isNonVersioning;
  private boolean nonVersioningInactiveRollbackEnabled;

  @Builder
  public CfCommandSetupRequest(String accountId, String appId, String commandName, String activityId,
      PcfCommandType pcfCommandType, String organization, String space, CfInternalConfig pcfConfig,
      String workflowExecutionId, String releaseNamePrefix, String manifestYaml, List<ArtifactFile> artifactFiles,
      ArtifactStreamAttributes artifactStreamAttributes, List<String> tempRouteMap, List<String> routeMaps,
      Map<String, String> serviceVariables, Map<String, String> safeDisplayServiceVariables,
      Integer timeoutIntervalInMin, Integer maxCount, Integer currentRunningCount, boolean useCurrentCount,
      boolean blueGreen, Integer olderActiveVersionCountToKeep, boolean useCLIForPcfAppCreation,
      PcfManifestsPackage pcfManifestsPackage, boolean useAppAutoscalar, boolean enforceSslValidation,
      boolean limitPcfThreads, boolean ignorePcfConnectionContextCache, String artifactProcessingScript,
      CfCliVersion cfCliVersion, boolean isNonVersioning, boolean nonVersioningInactiveRollbackEnabled) {
    super(accountId, appId, commandName, activityId, pcfCommandType, organization, space, pcfConfig,
        workflowExecutionId, timeoutIntervalInMin, useCLIForPcfAppCreation, enforceSslValidation, useAppAutoscalar,
        limitPcfThreads, ignorePcfConnectionContextCache, cfCliVersion);
    this.releaseNamePrefix = releaseNamePrefix;
    this.manifestYaml = manifestYaml;
    this.artifactFiles = artifactFiles;
    this.artifactStreamAttributes = artifactStreamAttributes;
    this.tempRouteMap = tempRouteMap;
    this.routeMaps = routeMaps;
    this.serviceVariables = serviceVariables;
    this.safeDisplayServiceVariables = safeDisplayServiceVariables;
    this.maxCount = maxCount;
    this.blueGreen = blueGreen;
    this.olderActiveVersionCountToKeep = olderActiveVersionCountToKeep;
    this.currentRunningCount = currentRunningCount;
    this.useCurrentCount = useCurrentCount;
    this.pcfManifestsPackage = pcfManifestsPackage;
    this.artifactProcessingScript = artifactProcessingScript;
    this.isNonVersioning = isNonVersioning;
    this.nonVersioningInactiveRollbackEnabled = nonVersioningInactiveRollbackEnabled;
  }
}
