/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.utils.ArtifactType;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AzureArtifactUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDetectType() {
    LogCallback logCallback = mock(LogCallback.class);
    assertThat(AzureArtifactUtils.detectArtifactType("/paths/artifacts/artifact.zip", logCallback))
        .isEqualTo(ArtifactType.ZIP);
    assertThat(AzureArtifactUtils.detectArtifactType("artifact.zip", logCallback)).isEqualTo(ArtifactType.ZIP);
    assertThat(AzureArtifactUtils.detectArtifactType("paths/artifacts/artifact.jar.zip", logCallback))
        .isEqualTo(ArtifactType.ZIP);
    assertThat(AzureArtifactUtils.detectArtifactType("/path/artifact.war", logCallback)).isEqualTo(ArtifactType.WAR);
    assertThat(AzureArtifactUtils.detectArtifactType("artifact.jar", logCallback)).isEqualTo(ArtifactType.JAR);
    assertThat(AzureArtifactUtils.detectArtifactType("", logCallback)).isEqualTo(ArtifactType.ZIP);
  }
}