/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.buckets.resources.s3;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;
import java.util.Map;

@OwnedBy(CDP)
public interface S3ResourceService {
  Map<String, String> getBuckets(
      IdentifierRef awsConnectorRef, String region, String orgIdentifier, String projectIdentifier);

  List<BuildDetails> getFilePaths(IdentifierRef awsConnectorRef, String region, String bucketName, String filePathRegex,
      String orgIdentifier, String projectIdentifier);
}
