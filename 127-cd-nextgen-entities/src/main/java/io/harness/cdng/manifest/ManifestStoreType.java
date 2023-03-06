/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public interface ManifestStoreType {
  String GIT = "Git";
  String LOCAL = "Local";
  String GITHUB = "Github";
  String BITBUCKET = "Bitbucket";
  String GITLAB = "GitLab";
  String HTTP = "Http";
  String S3 = "S3";
  String GCS = "Gcs";
  String ARTIFACTORY = "Artifactory";
  String INLINE = "Inline";
  String S3URL = "S3Url";
  String InheritFromManifest = "InheritFromManifest";
  String OCI = "OciHelmChart";
  String AZURE_REPO = "AzureRepo";
  String CUSTOM_REMOTE = "CustomRemote";
  String HARNESS = "Harness";

  static boolean isInGitSubset(String manifestType) {
    switch (manifestType) {
      case GIT:
      case BITBUCKET:
      case GITLAB:
      case GITHUB:
      case AZURE_REPO:
        return true;

      default:
        return false;
    }
  }

  static boolean isInStorageRepository(String manifestType) {
    switch (manifestType) {
      case HTTP:
      case S3:
      case GCS:
      case OCI:
        return true;

      default:
        return false;
    }
  }

  Set<String> HelmChartRepo = ImmutableSet.of(HTTP, GCS, S3, OCI);
  Set<String> HelmAllRepo =
      ImmutableSet.of(HTTP, GCS, S3, GIT, GITHUB, GITLAB, BITBUCKET, OCI, CUSTOM_REMOTE, AZURE_REPO, HARNESS);
  Set<String> GitSubsetRepo = ImmutableSet.of(GIT, GITHUB, GITLAB, BITBUCKET, AZURE_REPO);
}
