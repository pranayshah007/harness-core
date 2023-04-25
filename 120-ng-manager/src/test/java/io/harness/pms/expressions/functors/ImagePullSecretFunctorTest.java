/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.SidecarsOutcome;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expressions.utils.ImagePullSecretUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class ImagePullSecretFunctorTest extends CategoryTest {
  @Mock ImagePullSecretUtils imagePullSecretUtils;
  @Mock OutcomeService outcomeService;
  @InjectMocks ImagePullSecretFunctor imagePullSecretFunctor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGet() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    SidecarsOutcome sidecarsOutcome = new SidecarsOutcome();
    sidecarsOutcome.put(
        "sidecar1", DockerArtifactOutcome.builder().primaryArtifact(false).image("image").identifier("id").build());
    when(outcomeService.resolve(any(), any()))
        .thenReturn(
            ArtifactsOutcome.builder()
                .primary(DockerArtifactOutcome.builder().primaryArtifact(true).image("image").identifier("id").build())
                .sidecars(sidecarsOutcome)
                .build());
    when(imagePullSecretUtils.getImagePullSecret(any(), any())).thenReturn("ImagePullSecret");
    assertNotNull(imagePullSecretFunctor.get(ambiance, "primary"));
    assertNotNull(imagePullSecretFunctor.get(ambiance, "sidecars"));
    assertNull(imagePullSecretFunctor.get(ambiance, "invalid"));
    when(outcomeService.resolve(any(), any())).thenReturn(ArtifactsOutcome.builder().build());
    assertNull(imagePullSecretFunctor.get(ambiance, "primary"));
  }
}
