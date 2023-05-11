/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.api.ExecutionDataValue.executionDataValue;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;

import software.wings.beans.command.GcbTaskParams;
import software.wings.delegatetasks.GcbDelegateResponse;
import software.wings.helpers.ext.gcb.models.GcbArtifactObjects;
import software.wings.helpers.ext.gcb.models.GcbArtifacts;
import software.wings.helpers.ext.gcb.models.GcbBuildDetails;
import software.wings.helpers.ext.gcb.models.GcbBuildStatus;
import software.wings.sm.StateExecutionData;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by vglijin on 5/29/20.
 */
@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class GcbExecutionData extends StateExecutionData implements DelegateTaskNotifyResponseData {
  public static final String GCB_URL = "https://console.cloud.google.com/cloud-build/builds/";
  @NotNull private String activityId;
  @Nullable private String buildUrl;
  @Nullable private String buildNo;
  @Nullable private List<String> tags;
  @Nullable private GcbBuildStatus buildStatus;
  @Nullable private String name;
  @Nullable private String createTime;
  @Nullable private Map<String, String> substitutions;
  @Nullable private String logUrl;
  @Nullable private List<String> images;
  @Nullable private String artifactLocation;
  @Nullable private List<String> artifacts;
  @Nullable private String gcpConfigId;
  @Nullable private List<Map<String, Object>> secrets;

  @NotNull
  public GcbExecutionData withDelegateResponse(@NotNull final GcbDelegateResponse delegateResponse) {
    GcbTaskParams params = delegateResponse.getParams();
    activityId = delegateResponse.getParams().getActivityId();
    name = params.getBuildName();
    buildNo = params.getBuildId();
    buildUrl = GCB_URL + buildNo;
    if (delegateResponse.getParams().getGcbOptions() != null) {
      gcpConfigId = delegateResponse.getParams().getGcbOptions().getGcpConfigId();
    }
    GcbBuildDetails buildDetails = delegateResponse.getBuild();
    if (buildDetails != null) {
      tags = buildDetails.getTags();
      buildStatus = buildDetails.getStatus();
      createTime = buildDetails.getCreateTime();
      substitutions = buildDetails.getSubstitutions();
      secrets = buildDetails.getSecrets();
      logUrl = buildDetails.getLogUrl();
      final GcbArtifacts gcbArtifacts = buildDetails.getArtifacts();
      if (gcbArtifacts != null) {
        images = gcbArtifacts.getImages();
        final GcbArtifactObjects artifactObjects = gcbArtifacts.getObjects();
        if (artifactObjects != null) {
          artifactLocation = artifactObjects.getLocation();
          this.artifacts = artifactObjects.getPaths();
        }
      }
    }
    return this;
  }

  @Override
  public Map<String, ExecutionDataValue> getExecutionSummary() {
    return setExecutionData(super.getExecutionSummary());
  }

  @Override
  public Map<String, ExecutionDataValue> getExecutionDetails() {
    return setExecutionData(super.getExecutionDetails());
  }

  private Map<String, ExecutionDataValue> setExecutionData(Map<String, ExecutionDataValue> executionDetails) {
    if (isNotEmpty(tags)) {
      executionDetails.put("tags", executionDataValue("Tags", tags));
    }

    if (buildStatus != null) {
      executionDetails.put("status", executionDataValue("Status", buildStatus));
    }

    if (isNotEmpty(name)) {
      executionDetails.put("name", executionDataValue("Name", name));
    }

    if (isNotEmpty(createTime)) {
      executionDetails.put("createTime", executionDataValue("Created Time", createTime));
    }

    if (isNotEmpty(logUrl)) {
      executionDetails.put("logUrl", executionDataValue("Logs Url", logUrl));
    }

    if (isNotEmpty(substitutions)) {
      executionDetails.put("substitutions", executionDataValue("Substitutions", removeNullValues(substitutions)));
    }

    if (isNotEmpty(secrets)) {
      executionDetails.put("secrets", executionDataValue("Secrets", secrets));
    }

    if (isNotEmpty(buildNo)) {
      executionDetails.put("buildNo", executionDataValue("BuildNo", buildNo));
    }

    if (isNotEmpty(buildUrl)) {
      executionDetails.put("buildUrl", executionDataValue("Build Url", buildUrl));
    }

    if (isNotEmpty(activityId)) {
      putNotNull(executionDetails, "activityId", executionDataValue("Activity Id", activityId));
    }

    if (isNotEmpty(images)) {
      putNotNull(executionDetails, "images", executionDataValue("Images", images));
    }

    if (isNotEmpty(artifactLocation)) {
      putNotNull(executionDetails, "artifactLocation", executionDataValue("Artifact Location", artifactLocation));
    }

    if (isNotEmpty(artifacts)) {
      putNotNull(executionDetails, "artifacts", executionDataValue("Artifacts", artifacts));
    }

    return executionDetails;
  }
}
