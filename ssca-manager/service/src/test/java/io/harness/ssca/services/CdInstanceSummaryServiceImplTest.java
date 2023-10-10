/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.BuilderFactory;
import io.harness.SSCAManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.entities.ArtifactDetails;
import io.harness.repositories.CdInstanceSummaryRepo;
import io.harness.rule.Owner;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.CdInstanceSummary;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class CdInstanceSummaryServiceImplTest extends SSCAManagerTestBase {
  @Inject CdInstanceSummaryService cdInstanceSummaryService;

  @Mock CdInstanceSummaryRepo cdInstanceSummaryRepo;
  private BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(cdInstanceSummaryService, "cdInstanceSummaryRepo", cdInstanceSummaryRepo, true);
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpsertInstance_noArtifactIdentity() {
    Boolean response = cdInstanceSummaryService.upsertInstance(
        builderFactory.getInstanceNGEntityBuilder()
            .primaryArtifact(ArtifactDetails.builder().artifactId("artifactId").displayName("image").tag("tag").build())
            .build());
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpsertInstance() {
    Boolean response = cdInstanceSummaryService.upsertInstance(
        builderFactory.getInstanceNGEntityBuilder()
            .primaryArtifact(ArtifactDetails.builder().artifactId("artifactId").displayName("image").tag("tag").build())
            .build());
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testRemoveInstance() {
    Boolean response = cdInstanceSummaryService.removeInstance(builderFactory.getInstanceNGEntityBuilder().build());
    assertThat(response).isEqualTo(true);
    Mockito.when(cdInstanceSummaryRepo.findOne(Mockito.any()))
        .thenReturn(builderFactory.getCdInstanceSummaryBuilder().build());

    response = cdInstanceSummaryService.removeInstance(builderFactory.getInstanceNGEntityBuilder().build());
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetCdInstanceSummaries() {
    CdInstanceSummary.CdInstanceSummaryBuilder builder = builderFactory.getCdInstanceSummaryBuilder();
    Page<CdInstanceSummary> entities =
        new PageImpl<>(List.of(builder.envIdentifier("env1").build(), builder.envIdentifier("env2").build(),
                           builder.envIdentifier("env3").build()),
            Pageable.ofSize(2).withPage(0), 5);

    Mockito.when(cdInstanceSummaryRepo.findAll(Mockito.any(), Mockito.any())).thenReturn(entities);

    Page<CdInstanceSummary> cdInstanceSummaryPage =
        cdInstanceSummaryService.getCdInstanceSummaries(builderFactory.getContext().getAccountId(),
            builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
            ArtifactEntity.builder().build(), null, Pageable.ofSize(3).withPage(0));
    List<CdInstanceSummary> cdInstanceSummaryList = cdInstanceSummaryPage.get().collect(Collectors.toList());
    assertThat(cdInstanceSummaryList.size()).isEqualTo(3);
    assertThat(cdInstanceSummaryList.get(0))
        .isEqualTo(builderFactory.getCdInstanceSummaryBuilder().envIdentifier("env1").build());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetCdInstanceSummary() {
    Mockito.when(cdInstanceSummaryRepo.findOne(Mockito.any()))
        .thenReturn(builderFactory.getCdInstanceSummaryBuilder().build());

    CdInstanceSummary cdInstanceSummary = cdInstanceSummaryService.getCdInstanceSummary(
        builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(),
        builderFactory.getContext().getProjectIdentifier(), "artifactCorrelationId", "envId");

    assertThat(cdInstanceSummary).isEqualTo(builderFactory.getCdInstanceSummaryBuilder().build());
  }
}
