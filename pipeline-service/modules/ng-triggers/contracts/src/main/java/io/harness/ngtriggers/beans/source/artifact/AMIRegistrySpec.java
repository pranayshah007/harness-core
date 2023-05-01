/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.ngtriggers.Constants.AMI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDC)
public class AMIRegistrySpec implements ArtifactTypeSpec {
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

  /**
   * EventConditions
   */
  List<TriggerEventDataCondition> eventConditions;

  List<TriggerEventDataCondition> metaDataConditions;

  String jexlCondition;

  @Override
  public String fetchConnectorRef() {
    return connectorRef;
  }

  @Override
  public String fetchBuildType() {
    return AMI;
  }

  @Override
  public List<TriggerEventDataCondition> fetchEventDataConditions() {
    return eventConditions;
  }

  @Override
  public List<TriggerEventDataCondition> fetchMetaDataConditions() {
    return metaDataConditions;
  }

  @Override
  public String fetchJexlArtifactConditions() {
    return jexlCondition;
  }
}
