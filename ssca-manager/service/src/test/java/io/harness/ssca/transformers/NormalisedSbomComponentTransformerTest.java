/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.transformers;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.BuilderFactory;
import io.harness.SSCAManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.ssca.v1.model.NormalizedSbomComponentDTO;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class NormalisedSbomComponentTransformerTest extends SSCAManagerTestBase {
  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testToEntity() {
    NormalizedSBOMComponentEntity normalizedSBOMComponentEntity =
        NormalisedSbomComponentTransformer.toEntity(builderFactory.getNormalizedSbomComponentDTO());

    assertThat(normalizedSBOMComponentEntity.equals(builderFactory.getNormalizedSBOMComponentBuilder().build()))
        .isEqualTo(true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testToDTO() {
    NormalizedSbomComponentDTO normalizedSbomComponentDTO =
        NormalisedSbomComponentTransformer.toDTO(builderFactory.getNormalizedSBOMComponentBuilder().build());
    assertThat(normalizedSbomComponentDTO.equals(builderFactory.getNormalizedSbomComponentDTO())).isEqualTo(true);
  }
}
