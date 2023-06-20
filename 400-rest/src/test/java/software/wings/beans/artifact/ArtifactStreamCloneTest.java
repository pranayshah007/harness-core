/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.utils.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ArtifactStreamCloneTest {
  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneS3ArtifactStream() {
    testForArtifactStream("artifacts/s3artifactStream.json", AmazonS3ArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneAmiArtifactStream() {
    testForArtifactStream("artifacts/amiArtifactStream.json", AmiArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneArtifactoryArtifactStream() {
    testForArtifactStream("artifacts/artifactoryArtifactStream.json", ArtifactoryArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneBambooArtifactStream() {
    testForArtifactStream("artifacts/bambooArtifactStream.json", BambooArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneSmbArtifactStream() {
    testForArtifactStream("artifacts/smbArtifactStream.json", SmbArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneNexusArtifactStream() {
    testForArtifactStream("artifacts/nexusArtifactStream.json", NexusArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneJenkinsArtifactStream() {
    testForArtifactStream("artifacts/jenkinsArtifactStream.json", JenkinsArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneAcrArtifactStream() {
    testForArtifactStream("artifacts/acrArtifactStream.json", AcrArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneDockerArtifactStream() {
    testForArtifactStream("artifacts/dockerArtifactStream.json", DockerArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneCustomArtifactStream() {
    testForArtifactStream("artifacts/customArtifactStream.json", CustomArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneGcsArtifactStream() {
    testForArtifactStream("artifacts/gcsArtifactStream.json", GcsArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneGcrArtifactStream() {
    testForArtifactStream("artifacts/gcrArtifactStream.json", GcrArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneAzureMIArtifactStream() {
    testForArtifactStream("artifacts/azuremiArtifactStream.json", AzureMachineImageArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneEcrArtifactStream() {
    testForArtifactStream("artifacts/ecrArtifactStream.json", EcrArtifactStream.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCloneSftpArtifactStream() {
    testForArtifactStream("artifacts/sftpArtifactStream.json", SftpArtifactStream.class);
  }

  private <T extends ArtifactStream> void testForArtifactStream(String filPath, Class<T> tClass) {
    T artifactStream = JsonUtils.readResourceFile(filPath, tClass);
    T clonedArtifactStream = (T) artifactStream.cloneInternal();
    JsonNode expected = JsonUtils.readResourceFile(filPath, JsonNode.class);
    assertThat(JsonUtils.readTree(JsonUtils.toJsonNode(clonedArtifactStream).toString()))
        .isEqualTo(JsonUtils.readTree(expected.toString()));
  }
}
