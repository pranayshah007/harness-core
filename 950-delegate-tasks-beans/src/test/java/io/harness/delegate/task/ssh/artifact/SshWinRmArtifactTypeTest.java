/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ssh.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class SshWinRmArtifactTypeTest extends CategoryTest {
  private Map<Integer, String> artifactTypeOrdinalMapping;
  private Map<String, Integer> artifactTypeConstantMapping;

  @Before
  public void setUp() {
    artifactTypeOrdinalMapping = new HashMap<>();
    artifactTypeOrdinalMapping.put(0, "ARTIFACTORY");
    artifactTypeOrdinalMapping.put(1, "JENKINS");
    artifactTypeOrdinalMapping.put(2, "CUSTOM_ARTIFACT");
    artifactTypeOrdinalMapping.put(3, "NEXUS");
    artifactTypeOrdinalMapping.put(4, "AWS_S3");
    artifactTypeOrdinalMapping.put(5, "NEXUS_PACKAGE");
    artifactTypeOrdinalMapping.put(6, "AZURE");
    artifactTypeOrdinalMapping.put(7, "ECR");
    artifactTypeOrdinalMapping.put(8, "ACR");
    artifactTypeOrdinalMapping.put(9, "GCR");
    artifactTypeOrdinalMapping.put(10, "DOCKER");
    artifactTypeOrdinalMapping.put(11, "GITHUB_PACKAGE");
    artifactTypeOrdinalMapping.put(12, "GCS");

    artifactTypeConstantMapping = artifactTypeOrdinalMapping.entrySet().stream().collect(
        Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testMappingExistsForAllEnumConstants() {
    Arrays.stream(SshWinRmArtifactType.values()).forEach(artifactType -> {
      if (!artifactType.name().equals(artifactTypeOrdinalMapping.get(artifactType.ordinal()))) {
        Assertions.fail(String.format("Not all constants from enum [%s] mapped in test [%s].",
            SshWinRmArtifactType.class.getCanonicalName(), this.getClass().getCanonicalName()));
      }
    });
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testEnumConstantAddedAtTheEndWithoutMapping() {
    if (SshWinRmArtifactType.values().length > artifactTypeOrdinalMapping.size()) {
      Arrays.stream(SshWinRmArtifactType.values()).forEach(artifactType -> {
        if (!artifactType.name().equals(artifactTypeOrdinalMapping.get(artifactType.ordinal()))
            && !artifactTypeConstantMapping.containsKey(artifactType.name())
            && artifactType.ordinal() >= artifactTypeOrdinalMapping.size()) {
          Assertions.fail(String.format(
              "New constant added at the end of Enum [%s] at ordinal [%s] with name [%s]. This is expected for Kryo serialization/deserialization to work in the backward compatible manner. Please add this new enum constant mapping in test [%s].",
              SshWinRmArtifactType.class.getCanonicalName(), artifactType.ordinal(), artifactType.name(),
              this.getClass().getCanonicalName()));
        }
      });
    }
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testEnumConstantNotAddedInBetween() {
    if (artifactTypeOrdinalMapping.size() < SshWinRmArtifactType.values().length) {
      Arrays.stream(SshWinRmArtifactType.values()).forEach(artifactType -> {
        if (!artifactType.name().equals(artifactTypeOrdinalMapping.get(artifactType.ordinal()))
            && !artifactTypeConstantMapping.containsKey(artifactType.name())
            && artifactType.ordinal() < artifactTypeOrdinalMapping.size()) {
          Assertions.fail(String.format(
              "New constant added in Enum [%s] at ordinal [%s] with name [%s]. You have to add constant at the end for Kryo serialization/deserialization to work in the backward compatible manner.",
              SshWinRmArtifactType.class.getCanonicalName(), artifactType.ordinal(), artifactType.name()));
        }
      });
    }
  }
}
