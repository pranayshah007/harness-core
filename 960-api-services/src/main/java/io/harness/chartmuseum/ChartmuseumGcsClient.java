/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.chartmuseum;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.GCS_COMMAND_TEMPLATE;
import static io.harness.chartmuseum.ChartMuseumConstants.GCS_COMMAND_TEMPLATE_DEBUG;
import static io.harness.chartmuseum.ChartMuseumConstants.GOOGLE_APPLICATION_CREDENTIALS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.version.Version;
import io.harness.filesystem.FileIo;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@OwnedBy(CDP)
public class ChartmuseumGcsClient extends AbstractChartmuseumClient {
  private final String bucket;
  private final String basePath;
  private final char[] serviceAccountKey;
  private final String resourceDirectory;

  @Builder
  public ChartmuseumGcsClient(ChartMuseumClientHelper clientHelper, String cliPath, Version version, String bucket,
      String basePath, char[] serviceAccountKey, String resourceDirectory) {
    super(clientHelper, cliPath, version);
    this.bucket = bucket;
    this.basePath = basePath;
    this.serviceAccountKey = serviceAccountKey;
    this.resourceDirectory = resourceDirectory;
  }

  @Override
  public ChartMuseumServer start() throws IOException {
    Map<String, String> environment = new HashMap<>();
    if (serviceAccountKey != null) {
      String credentialFilePath = writeGCSCredentialsFile(resourceDirectory, serviceAccountKey);
      environment.put(GOOGLE_APPLICATION_CREDENTIALS, credentialFilePath);
    }

    String evaluatedArguments = (log.isDebugEnabled() ? GCS_COMMAND_TEMPLATE_DEBUG : GCS_COMMAND_TEMPLATE)
                                    .replace("${BUCKET_NAME}", bucket)
                                    .replace("${FOLDER_PATH}", basePath == null ? "" : basePath);

    return startServer(evaluatedArguments, environment);
  }

  private String writeGCSCredentialsFile(String resourceDirectory, char[] serviceAccountKey) throws IOException {
    String credentialFilePath = Paths.get(resourceDirectory, "credentials.json").toString();
    FileIo.writeUtf8StringToFile(credentialFilePath, String.valueOf(serviceAccountKey));
    return credentialFilePath;
  }
}
