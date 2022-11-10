/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
public class AzureArtifactsInfo implements ArtifactInfo {
  /**
   * Connector Reference
   */
  String connectorRef;

  /**
   * Project
   */
  String project;

  /**
   * Feed
   */
  String feed;

  /**
   * Package Name
   */
  String packageName;

  /**
   * Package Type
   */
  String packageType;

  /**
   * Version Regex
   */
  String versionRegex;

  /**
   * Version
   */
  String version;

  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.AZURE_ARTIFACTS;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    return AzureArtifactsConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .project(ParameterField.<String>builder().value(project).build())
        .feed(ParameterField.<String>builder().value(feed).build())
        .packageName(ParameterField.<String>builder().value(packageName).build())
        .packageType(ParameterField.<String>builder().value(packageType).build())
        .versionRegex(ParameterField.<String>builder().value(versionRegex).build())
        .version(ParameterField.<String>builder().value(version).build())
        .build();
  }
}
