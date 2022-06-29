/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.artifact;

import static io.harness.rule.OwnerRule.VAIBHAV_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.settings.SettingVariableTypes;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ArtifactStreamAttributesMapperTest extends WingsBaseTest {
  @Test
  @Owner(developers = VAIBHAV_KUMAR)
  @Category(UnitTests.class)
  public void testGetArtifactPath() {
    // Case1: when jobName occurs as a substring inside artifactPath
    String path = "harness-internal/harness-internal/20211208.2.zip";
    String jobName1 = "harness-internal";
    String url1 = "https://harness.jfrog.io/artifactory/harness-internal/harness-internal/20211208.2.zip";
    Map<String, String> metadata = new HashMap<String, String>() {
      {
        put("artifactPath", path);
        put("url", url1);
      }
    };

    Artifact artifact = new Artifact();
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .artifactStreamType(SettingVariableTypes.ARTIFACTORY.name())
                                                            .jobName(jobName1)
                                                            .metadata(metadata)
                                                            .build();
    new ArtifactStreamAttributesMapper(artifact, artifactStreamAttributes);
    String result = artifactStreamAttributes.getMetadata().get("artifactPath");
    assertThat(result).isEqualTo(path);

    // Case2: when there is no overlap between jobName and artifactPath
    String jobName2 = "test";
    String url2 = "https://harness.jfrog.io/artifactory/test/harness-internal/20211208.2.zip";
    artifactStreamAttributes.setJobName(jobName2);
    metadata.put("url", url2);
    new ArtifactStreamAttributesMapper(artifact, artifactStreamAttributes);
    result = artifactStreamAttributes.getMetadata().get("artifactPath");
    assertThat(result).isEqualTo(path);
  }
}
