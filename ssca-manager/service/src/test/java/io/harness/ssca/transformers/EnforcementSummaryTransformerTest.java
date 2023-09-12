/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.transformers;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.SSCAManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.ssca.v1.model.EnforcementSummaryDTO;
import io.harness.ssca.beans.Artifact;
import io.harness.ssca.entities.EnforcementSummaryEntity;

import java.math.BigDecimal;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class EnforcementSummaryTransformerTest extends SSCAManagerTestBase {
  private EnforcementSummaryDTO dto;
  private EnforcementSummaryEntity entity;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    dto = new EnforcementSummaryDTO()
              .enforcementId("enforcementIdentifier")
              .allowListViolationCount(new BigDecimal(5))
              .denyListViolationCount(new BigDecimal(7))
              .orchestrationId("orchestrationIdentifier")
              .status("failed")
              .artifact(new io.harness.spec.server.ssca.v1.model.Artifact()
                            .id("artifactIdentifier")
                            .name("artifactName")
                            .tag("latest")
                            .type("artifactType")
                            .registryUrl("url"));
    entity = EnforcementSummaryEntity.builder()
                 .status("failed")
                 .allowListViolationCount(5)
                 .enforcementId("enforcementIdentifier")
                 .denyListViolationCount(7)
                 .orchestrationId("orchestrationIdentifier")
                 .artifact(Artifact.builder()
                               .url("url")
                               .type("artifactType")
                               .name("artifactName")
                               .tag("latest")
                               .artifactId("artifactIdentifier")
                               .build())
                 .build();
  }
  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testToEntity() {
    EnforcementSummaryEntity enforcementSummaryEntity = EnforcementSummaryTransformer.toEntity(dto);
    assertThat(enforcementSummaryEntity.equals(entity)).isEqualTo(true);
  }
  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testToDTO() {
    EnforcementSummaryDTO enforcementSummaryDTO = EnforcementSummaryTransformer.toDTO(entity);
    assertThat(enforcementSummaryDTO.equals(dto)).isEqualTo(true);
  }
}
