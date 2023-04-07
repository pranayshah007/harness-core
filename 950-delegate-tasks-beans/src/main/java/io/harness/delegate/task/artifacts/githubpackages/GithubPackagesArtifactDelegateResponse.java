/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.githubpackages;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class GithubPackagesArtifactDelegateResponse extends ArtifactDelegateResponse {
  /**
   * Package Id in repos need to be referenced.
   */
  String packageId;

  /**
   * Package Name in repos need to be referenced.
   */
  String packageName;

  /**
   * Package Name in repos need to be referenced.
   */
  String packageType;

  /**
   * Exact Version of the artifact
   */
  String version;

  /**
   * Version Regex
   */
  String versionRegex;

  /**
   * Package Visibility
   */
  String packageVisibility;

  /**
   * Package Url
   */
  String packageUrl;

  /**
   * Version Url
   */
  String versionUrl;

  Map<String, String> label;

  @Builder
  public GithubPackagesArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String packageId, String packageName, String packageType, String version, String versionRegex,
      String packageVisibility, String packageUrl, String versionUrl, Map<String, String> label) {
    super(buildDetails, sourceType);
    this.packageId = packageId;
    this.packageName = packageName;
    this.packageType = packageType;
    this.version = version;
    this.versionRegex = versionRegex;
    this.packageVisibility = packageVisibility;
    this.packageUrl = packageUrl;
    this.versionUrl = versionUrl;
    this.label = label;
  }
}
