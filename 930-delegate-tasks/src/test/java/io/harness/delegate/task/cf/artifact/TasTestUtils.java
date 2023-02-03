/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.pcf.artifact.TasArtifactRegistryType;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;

import java.util.Collections;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class TasTestUtils {
  public static final String TEST_IMAGE = "test.registry.io/test-image:tag";
  public static final String TEST_IMAGE_TAG = "tag-image";
  public static final String REGISTRY_HOSTNAME = "test.registry.io";
  public static final String TEST_REGION = "test-region";

  public TasContainerArtifactConfig createTestContainerArtifactConfig(
      ConnectorConfigDTO connector, TasArtifactRegistryType tasArtifactRegistryType) {
    return TasContainerArtifactConfig.builder()
        .encryptedDataDetails(Collections.emptyList())
        .connectorConfig(connector)
        .image(TEST_IMAGE)
        .tag(TEST_IMAGE_TAG)
        .registryType(tasArtifactRegistryType)
        .registryHostname(REGISTRY_HOSTNAME)
        .region(TEST_REGION)
        .build();
  }

  public TasContainerArtifactConfig createTestContainerArtifactConfigWithoutHostname(
      ConnectorConfigDTO connector, TasArtifactRegistryType tasArtifactRegistryType) {
    return TasContainerArtifactConfig.builder()
        .encryptedDataDetails(Collections.emptyList())
        .connectorConfig(connector)
        .image(TEST_IMAGE)
        .tag(TEST_IMAGE_TAG)
        .registryType(tasArtifactRegistryType)
        .region(TEST_REGION)
        .build();
  }
}
