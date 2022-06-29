/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.artifact;

import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AzureArtifactsArtifactStreamTest extends CategoryTest {
  private static final String FEED = "FEED";
  private static final String PACKAGE_ID = "PACKAGE_ID";

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGenerateSourceName() {
    AzureArtifactsArtifactStream artifactStream = AzureArtifactsArtifactStream.builder().build();
    assertThat(artifactStream.generateSourceName()).isEqualTo("");

    artifactStream = AzureArtifactsArtifactStream.builder()
                         .protocolType(AzureArtifactsArtifactStreamProtocolType.maven.name())
                         .packageName("gid:aid")
                         .build();
    assertThat(artifactStream.generateSourceName()).isEqualTo("gid:aid");
    artifactStream.setPackageName(null);
    assertThat(artifactStream.generateSourceName()).isEqualTo("");

    artifactStream = AzureArtifactsArtifactStream.builder()
                         .protocolType(AzureArtifactsArtifactStreamProtocolType.nuget.name())
                         .packageName("pn")
                         .build();
    assertThat(artifactStream.generateSourceName()).isEqualTo("pn");
    artifactStream.setPackageName(null);
    assertThat(artifactStream.generateSourceName()).isEqualTo("");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldCheckIfArtifactSourceChanged() {
    AzureArtifactsArtifactStream artifactStream = prepareMavenArtifactStream();
    assertThat(artifactStream.artifactSourceChanged(prepareMavenArtifactStream())).isFalse();
    assertThat(artifactStream.artifactSourceChanged(prepareNuGetArtifactStream())).isTrue();
    artifactStream.setPackageName("random:aid");
    artifactStream.setSourceName(artifactStream.generateSourceName());
    assertThat(artifactStream.artifactSourceChanged(prepareMavenArtifactStream())).isTrue();

    artifactStream = prepareNuGetArtifactStream();
    assertThat(artifactStream.artifactSourceChanged(prepareNuGetArtifactStream())).isFalse();
    assertThat(artifactStream.artifactSourceChanged(prepareMavenArtifactStream())).isTrue();
    artifactStream.setPackageName("random");
    artifactStream.setSourceName(artifactStream.generateSourceName());
    assertThat(artifactStream.artifactSourceChanged(prepareNuGetArtifactStream())).isTrue();

    artifactStream = prepareMavenArtifactStream();
    artifactStream.setProject("random");
    assertThat(artifactStream.artifactSourceChanged(prepareMavenArtifactStream())).isTrue();

    artifactStream = prepareMavenArtifactStream();
    artifactStream.setFeed("random");
    assertThat(artifactStream.artifactSourceChanged(prepareMavenArtifactStream())).isTrue();

    artifactStream = prepareMavenArtifactStream();
    artifactStream.setPackageId("random");
    assertThat(artifactStream.artifactSourceChanged(prepareMavenArtifactStream())).isTrue();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateRequiredFields() {
    AzureArtifactsArtifactStream artifactStream = AzureArtifactsArtifactStream.builder().appId(GLOBAL_APP_ID).build();
    assertValidationException(artifactStream);

    artifactStream = AzureArtifactsArtifactStream.builder().appId(GLOBAL_APP_ID).feed(FEED).build();
    assertValidationException(artifactStream);

    artifactStream = AzureArtifactsArtifactStream.builder().appId(GLOBAL_APP_ID).feed(PACKAGE_ID).build();
    assertValidationException(artifactStream);

    artifactStream = prepareMavenArtifactStream();
    assertNoValidationException(artifactStream);

    artifactStream.setPackageName("");
    assertValidationException(artifactStream);

    artifactStream = prepareNuGetArtifactStream();
    assertNoValidationException(artifactStream);

    artifactStream.setPackageName("");
    assertValidationException(artifactStream);
  }

  private AzureArtifactsArtifactStream prepareMavenArtifactStream() {
    AzureArtifactsArtifactStream artifactStream =
        AzureArtifactsArtifactStream.builder()
            .appId(GLOBAL_APP_ID)
            .protocolType(AzureArtifactsArtifactStreamProtocolType.maven.name())
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .packageName("gid:aid")
            .build();
    artifactStream.setSourceName(artifactStream.generateSourceName());
    return artifactStream;
  }

  private AzureArtifactsArtifactStream prepareNuGetArtifactStream() {
    AzureArtifactsArtifactStream artifactStream =
        AzureArtifactsArtifactStream.builder()
            .appId(GLOBAL_APP_ID)
            .protocolType(AzureArtifactsArtifactStreamProtocolType.nuget.name())
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .packageName("pn")
            .build();
    artifactStream.setSourceName(artifactStream.generateSourceName());
    return artifactStream;
  }

  private void assertNoValidationException(AzureArtifactsArtifactStream artifactStream) {
    try {
      artifactStream.validateRequiredFields();
      assertThat(true).isTrue();
    } catch (Exception e) {
      assertThat(true).isFalse();
    }
  }

  private void assertValidationException(AzureArtifactsArtifactStream artifactStream) {
    try {
      artifactStream.validateRequiredFields();
      assertThat(true).isFalse();
    } catch (Exception e) {
      assertThat(true).isTrue();
    }
  }
}
