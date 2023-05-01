/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.ngtriggers.Constants.AZURE_ARTIFACTS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDC)
public class AzureArtifactsRegistrySpec implements ArtifactTypeSpec {
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

  /**
   * Event Conditions
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
    return AZURE_ARTIFACTS;
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
