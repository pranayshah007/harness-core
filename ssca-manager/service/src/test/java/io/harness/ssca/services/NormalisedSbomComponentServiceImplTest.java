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
import io.harness.repositories.SBOMComponentRepo;
import io.harness.rule.Owner;
import io.harness.spec.server.ssca.v1.model.Artifact;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public class NormalisedSbomComponentServiceImplTest extends SSCAManagerTestBase {
  @Inject NormalisedSbomComponentService normalisedSbomComponentService;
  @Mock ArtifactService artifactService;
  @Mock SBOMComponentRepo sbomComponentRepo;

  private BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(normalisedSbomComponentService, "sbomComponentRepo", sbomComponentRepo, true);
    FieldUtils.writeField(normalisedSbomComponentService, "artifactService", artifactService, true);
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testListNormalizedSbomComponent() {
    NormalizedSBOMComponentEntity entity = builderFactory.getNormalizedSBOMComponentBuilder().build();
    List<NormalizedSBOMComponentEntity> entityList = Arrays.asList(entity, entity, entity, entity, entity);
    Pageable page = PageRequest.of(1, 2);
    Page<NormalizedSBOMComponentEntity> entities = new PageImpl<>(entityList, page, entityList.size());

    Mockito
        .when(sbomComponentRepo.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndOrchestrationId(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(entities);

    Mockito.when(artifactService.getArtifact(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(Optional.ofNullable(ArtifactEntity.builder().build()));

    Artifact artifact = new Artifact();
    artifact.setRegistryUrl("registryUrl");
    artifact.setName("imageName");

    Response response =
        normalisedSbomComponentService.listNormalizedSbomComponent("org", "project", 2, 2, artifact, "accountId");
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.hasEntity()).isEqualTo(true);
  }
}
