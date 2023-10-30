/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.ngtriggers.Constants.ARTIFACTORY_REGISTRY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDP)
public class ArtifactoryRegistrySpec implements ArtifactTypeSpec {
  String connectorRef;
  List<TriggerEventDataCondition> eventConditions;
  List<TriggerEventDataCondition> metaDataConditions;
  String jexlCondition;
  String artifactDirectory;
  String artifactPath;
  String repository;
  String repositoryFormat;
  String repositoryUrl;
  String artifactFilter;

  @Override
  public String fetchConnectorRef() {
    return connectorRef;
  }

  @Override
  public String fetchBuildType() {
    return ARTIFACTORY_REGISTRY;
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
