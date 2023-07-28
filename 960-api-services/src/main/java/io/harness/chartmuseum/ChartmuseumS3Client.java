/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.chartmuseum;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.AMAZON_S3_COMMAND_TEMPLATE;
import static io.harness.chartmuseum.ChartMuseumConstants.AMAZON_S3_COMMAND_TEMPLATE_DEBUG;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.version.Version;

import java.io.IOException;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@OwnedBy(CDP)
public class ChartmuseumS3Client extends AbstractChartmuseumClient {
  private final String bucket;
  private final String basePath;
  private final String region;
  private final boolean useEc2IamCredentials;
  private final char[] accessKey;
  private final char[] secretKey;
  private final boolean useIRSA;

  @Builder
  public ChartmuseumS3Client(ChartMuseumClientHelper clientHelper, String cliPath, Version version, String bucket,
      String basePath, String region, boolean useEc2IamCredentials, char[] accessKey, char[] secretKey,
      boolean useIRSA) {
    super(clientHelper, cliPath, version);
    this.bucket = bucket;
    this.basePath = basePath;
    this.region = region;
    this.useEc2IamCredentials = useEc2IamCredentials;
    this.accessKey = accessKey;
    this.secretKey = secretKey;
    this.useIRSA = useIRSA;
  }

  @Override
  public ChartMuseumServer start() throws IOException {
    Map<String, String> environment =
        clientHelper.getEnvForAwsConfig(accessKey, secretKey, useEc2IamCredentials, useIRSA);
    String evaluatedArguments = (log.isDebugEnabled() ? AMAZON_S3_COMMAND_TEMPLATE_DEBUG : AMAZON_S3_COMMAND_TEMPLATE)
                                    .replace("${BUCKET_NAME}", bucket)
                                    .replace("${FOLDER_PATH}", basePath == null ? "" : basePath)
                                    .replace("${REGION}", region);

    return startServer(evaluatedArguments, environment);
  }
}
