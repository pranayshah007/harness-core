/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.chartmuseum;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(CDP)
@Singleton
public class ChartmuseumClientFactory {
  @Inject private ChartMuseumClientHelper clientHelper;

  public ChartmuseumClient s3(String cliPath, String bucket, String basePath, String region,
      boolean useEc2IamCredentials, char[] accessKey, char[] secretKey, boolean useIRSA) {
    Version version = clientHelper.getVersion(cliPath);
    return new ChartmuseumS3Client(
        clientHelper, cliPath, version, bucket, basePath, region, useEc2IamCredentials, accessKey, secretKey, useIRSA);
  }

  public ChartmuseumClient gcs(
      String cliPath, String bucket, String basePath, char[] serviceAccountKey, String resourceDirectory) {
    Version version = clientHelper.getVersion(cliPath);
    return new ChartmuseumGcsClient(
        clientHelper, cliPath, version, bucket, basePath, serviceAccountKey, resourceDirectory);
  }
}
