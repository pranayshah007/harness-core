/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.nexus.NexusRequest;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class NexusUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testBuildArtifactFileNameFromMavenArtifactPath() {
    assertThat(
        NexusUtils.buildNexusArtifactFileNameFromMavenArtifactUrl(
            "https://nexus2.dev.harness.io/service/local/artifact/maven/content?r=releases&g=mygroup&a=todolist&v=1.0&p=war&e=war"))
        .isEqualTo("todolist-1.0.war");
    assertThat(
        NexusUtils.buildNexusArtifactFileNameFromMavenArtifactUrl(
            "https://nexus2.dev.harness.io/service/local/artifact/maven/content?r=releases&g=mygroup&a=todolist&v=1.0&c=javadoc&p=war&e=war"))
        .isEqualTo("todolist-1.0-javadoc.war");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetBasicAuthHeader() {
    assertThat(
        NexusUtils.getBasicAuthHeader(
            NexusRequest.builder().username("username").password("pwd".toCharArray()).hasCredentials(true).build()))
        .isEqualTo("Basic dXNlcm5hbWU6cHdk");
    assertThat(
        NexusUtils.getBasicAuthHeader(
            NexusRequest.builder().username("username").password("pwd".toCharArray()).hasCredentials(false).build()))
        .isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetNexusArtifactName() {
    assertThat(
        NexusUtils.getNexusArtifactFileName(NexusVersion.NEXUS2, "maven",
            "https://nexus2.dev.harness.io/service/local/artifact/maven/content?r=releases&g=mygroup&a=todolist&v=1.0&c=javadoc&p=war&e=war"))
        .isEqualTo("todolist-1.0-javadoc.war");
    assertThat(NexusUtils.getNexusArtifactFileName(NexusVersion.NEXUS3, "maven",
                   "https://nexus2.dev.harness.io/service/local/artifact/maven/todolist-1.0-javadoc.war"))
        .isEqualTo("todolist-1.0-javadoc.war");
  }
}
