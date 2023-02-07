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
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
public class AMIArtifactInfo implements ArtifactInfo {
  /**
   * Connector Reference
   */
  String connectorRef;

  /**
   * Region
   */
  String region;

  /**
   * Tags
   */
  List<AMITag> tags;

  /**
   * Filters
   */
  List<AMIFilter> filters;

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
    return ArtifactSourceType.AMI;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    return AMIArtifactConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .region(ParameterField.<String>builder().value(region).build())
        .tags(ParameterField.<List<AMITag>>builder().value(tags).build())
        .filters(ParameterField.<List<AMIFilter>>builder().value(filters).build())
        .versionRegex(ParameterField.<String>builder().value(versionRegex).build())
        .version(ParameterField.<String>builder().value(version).build())
        .build();
  }
}
