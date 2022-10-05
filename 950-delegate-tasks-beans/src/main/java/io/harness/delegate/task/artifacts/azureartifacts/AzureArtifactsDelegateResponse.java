/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.azureartifacts;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class AzureArtifactsDelegateResponse extends ArtifactDelegateResponse {
  /**
   * Package Name in repos need to be referenced.
   */
  String packageName;

  /**
   * Package Id in repos need to be referenced.
   */
  String packageId;

  /**
   * Package Url.
   */
  String packageUrl;

  /**
   * Version Url.
   */
  String versionUrl;

  /**
   * Package Type
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
   * Feed
   */
  String feed;

  @Builder
  public AzureArtifactsDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String packageName, String packageId, String packageUrl, String versionUrl, String packageType, String version,
      String versionRegex, String feed) {
    super(buildDetails, sourceType);

    this.packageName = packageName;
    this.packageId = packageId;
    this.packageUrl = packageUrl;
    this.versionUrl = versionUrl;
    this.packageType = packageType;
    this.version = version;
    this.versionRegex = versionRegex;
    this.feed = feed;
  }
}
