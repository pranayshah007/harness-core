/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifact;

import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.PUNEET;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ArtifactUtilitiesTest extends CategoryTest {
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExtractNexusRegistryUrl() {
    assertThat(ArtifactUtilities.getNexusRegistryUrl("https://nexus.harness.io", "5000", null))
        .isEqualTo("https://nexus.harness.io:5000");

    assertThat(ArtifactUtilities.getNexusRegistryUrl("https://nexus.harness.io", null, null))
        .isEqualTo("https://nexus.harness.io");

    assertThat(ArtifactUtilities.getNexusRegistryUrl("https://nexus.harness.io", null, "nexus3.harness.io"))
        .isEqualTo("https://nexus3.harness.io");

    assertThat(ArtifactUtilities.getNexusRegistryUrl("https://nexus.harness.io", null, "http://nexus3.harness.io"))
        .isEqualTo("http://nexus3.harness.io");

    assertThat(ArtifactUtilities.getNexusRegistryUrl("https://nexus.harness.io", null, "nexus3.harness.io:5000"))
        .isEqualTo("https://nexus3.harness.io:5000");

    assertThat(ArtifactUtilities.getNexusRegistryUrl("https://nexus.harness.io", null, "http://nexus3.harness.io:5000"))
        .isEqualTo("http://nexus3.harness.io:5000");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExtractNexusRepositoryName() {
    assertThat(
        ArtifactUtilities.getNexusRepositoryName("https://nexus.harness.io", "5000", null, "harness/todolist-sample"))
        .isEqualTo("nexus.harness.io:5000/harness/todolist-sample");

    assertThat(
        ArtifactUtilities.getNexusRepositoryName("https://nexus.harness.io", null, null, "harness/todolist-sample"))
        .isEqualTo("nexus.harness.io/harness/todolist-sample");

    assertThat(ArtifactUtilities.getNexusRepositoryName(
                   "https://nexus.harness.io", null, "nexus3.harness.io", "harness/todolist-sample"))
        .isEqualTo("nexus3.harness.io/harness/todolist-sample");

    assertThat(ArtifactUtilities.getNexusRepositoryName(
                   "https://nexus.harness.io", null, "http://nexus3.harness.io", "harness/todolist-sample"))
        .isEqualTo("nexus3.harness.io/harness/todolist-sample");

    assertThat(ArtifactUtilities.getNexusRepositoryName(
                   "https://nexus.harness.io", null, "nexus3.harness.io:5000", "harness/todolist-sample"))
        .isEqualTo("nexus3.harness.io:5000/harness/todolist-sample");

    assertThat(ArtifactUtilities.getNexusRepositoryName(
                   "https://nexus.harness.io", null, "http://nexus3.harness.io:5000", "harness/todolist-sample"))
        .isEqualTo("nexus3.harness.io:5000/harness/todolist-sample");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void getFileSearchPatternTest() {
    assertThat(ArtifactUtilities.getFileSearchPattern("harness/todolist-sample")).isEqualTo("todolist-sample");
    assertThat(ArtifactUtilities.getFileSearchPattern("harness/todolist-sample/")).isEqualTo("*");
    assertThat(ArtifactUtilities.getFileSearchPattern("harness\\todolist-sample\\")).isEqualTo("*");
    assertThat(ArtifactUtilities.getFileSearchPattern("harness\\todolist-*.zip")).isEqualTo("todolist-*.zip");
    assertThat(ArtifactUtilities.getFileSearchPattern("prefix*")).isEqualTo("prefix*");
    assertThat(ArtifactUtilities.getFileSearchPattern("")).isEqualTo("*");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void getFileParentPathTest() {
    assertThat(ArtifactUtilities.getFileParentPath("harness/todolist-sample")).isEqualTo("harness");
    assertThat(ArtifactUtilities.getFileParentPath("harness/todolist-sample/")).isEqualTo("harness/todolist-sample");
    assertThat(ArtifactUtilities.getFileParentPath("harness\\todolist-sample\\")).isEqualTo("harness\\todolist-sample");
    assertThat(ArtifactUtilities.getFileParentPath("harness\\todolist-*.zip")).isEqualTo("harness");
    assertThat(ArtifactUtilities.getFileParentPath("prefix*")).isEqualTo("");
    assertThat(ArtifactUtilities.getFileParentPath("")).isEqualTo("");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void getArtifactNameTest() {
    assertThat(ArtifactUtilities.getArtifactName(null)).isEqualTo(null);
    assertThat(ArtifactUtilities.getArtifactName("")).isEqualTo("");
    assertThat(ArtifactUtilities.getArtifactName(
                   "https://nexus3.dev.harness.io/repository/maven-releases/mygroup/myartifact/1.8/myartifact-1.8.war"))
        .isEqualTo("myartifact-1.8.war");
    assertThat(ArtifactUtilities.getArtifactName(
                   "https://nexus3.dev.harness.io/repository/maven-releases/mygroup/myartifact/1.8/"))
        .isEqualTo("");
  }
}
