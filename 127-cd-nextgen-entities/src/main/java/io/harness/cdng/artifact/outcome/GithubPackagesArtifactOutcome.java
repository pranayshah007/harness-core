/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.GithubPackagesArtifactSummary;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("GithubPackagesArtifactOutcome")
@JsonTypeName("githubPackagesArtifactOutcome")
@OwnedBy(CDC)
@RecasterAlias("io.harness.ngpipeline.artifact.bean.GithubPackagesArtifactOutcome")
public class GithubPackagesArtifactOutcome implements ArtifactOutcome {
  /**
   * Github connector.
   */
  String connectorRef;

  /**
   * Package in repos
   */
  String packageName;

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
   * rtifact type.
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

  /**
   * imagePullSecret for Github Package credentials base encoded.
   */
  String imagePullSecret;
  /**
   * dockerConfigJson for docker credentials base encoded.
   */
  String dockerConfigJsonSecret;
  /**
   * package type for Github Package such as npm, maven, rubygems, nuget, container
   */
  String packageType;

  Map<String, String> metadata;

  Map<String, String> label;

  @Override
  public ArtifactSummary getArtifactSummary() {
    return GithubPackagesArtifactSummary.builder().packageName(packageName).version(version).build();
  }

  @Override
  public String getArtifactType() {
    return type;
  }

  @Override
  public String getTag() {
    return version;
  }
}
