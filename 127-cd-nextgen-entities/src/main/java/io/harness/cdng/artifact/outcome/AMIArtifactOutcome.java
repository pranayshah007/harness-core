/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.AMIArtifactSummary;
import io.harness.cdng.artifact.ArtifactSummary;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(
    module = ProductModule.CDS, components = {HarnessModuleComponent.CDS_ARTIFACTS}, unitCoverageRequired = false)
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("AMIArtifactOutcome")
@JsonTypeName("AMIArtifactOutcome")
@OwnedBy(CDC)
@RecasterAlias("io.harness.ngpipeline.artifact.bean.AMIArtifactOutcome")
public class AMIArtifactOutcome implements ArtifactOutcome {
  /**
   * Azure Artifacts ConnectorDisconnectHandler.
   */
  String connectorRef;

  /**
   * AMI ID
   */
  String amiId;

  /**
   * metadata
   */
  Map<String, String> metadata;

  /**
   * version of the package.
   */
  String version;

  /**
   * version regex is used to get latest artifacts from builds matching the regex.
   */
  String versionRegex;

  /**
   * Identifier for artifact.
   */
  String identifier;

  /**
   * Artifact type.
   */
  String type;

  /**
   * Whether this config corresponds to primary artifact.
   */
  boolean primaryArtifact;

  /**
   * Image pull link.
   */
  String image;

  @Override
  public ArtifactSummary getArtifactSummary() {
    return AMIArtifactSummary.builder().version(version).build();
  }

  @Override
  public String getArtifactType() {
    return type;
  }

  @Override
  public String getTag() {
    return version;
  }

  @Override
  public Set<String> getMetaTags() {
    return Sets.newHashSet(version, identifier, type, image, amiId);
  }
}
