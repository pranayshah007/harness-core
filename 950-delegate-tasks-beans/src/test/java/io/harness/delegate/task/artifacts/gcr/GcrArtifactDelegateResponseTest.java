/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.gcr;

import static io.harness.rule.OwnerRule.SRIDHAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.rule.Owner;

import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class GcrArtifactDelegateResponseTest {
  @InjectMocks GcrArtifactDelegateResponse gcrArtifactDelegateResponse;

  @Before
  public void setup() throws Exception {}

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testDescribe() {
    ArtifactBuildDetailsNG buildDetailsNG = ArtifactBuildDetailsNG.builder().build();
    gcrArtifactDelegateResponse = new GcrArtifactDelegateResponse(
        buildDetailsNG, ArtifactSourceType.GCR, "cd-play/alpine", "v3.0", new HashMap<>());
    String resp = gcrArtifactDelegateResponse.describe();
    String[] values = resp.split("\n");
    assertThat(resp).isNotNull();
    assertThat(values[0]).contains("type:");
    assertThat(values[1]).contains("imagePath:");
    assertThat(values[2]).contains("tag:");
  }
}
