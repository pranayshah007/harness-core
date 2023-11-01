/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.REETIKA;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.BuilderFactory;
import io.harness.SSCAManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.repositories.SBOMComponentRepo;
import io.harness.rule.Owner;
import io.harness.spec.server.ssca.v1.model.Artifact;
import io.harness.spec.server.ssca.v1.model.ArtifactListingRequestBodyLicenseFilter;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMEntityKeys;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

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

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testGetOrchestrationIds() {
    String licenseValue = randomAlphabetic(10);
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(sbomComponentRepo.findAll(any(), any())).thenReturn(Page.empty());
    ArtifactListingRequestBodyLicenseFilter licenseFilter =
        new ArtifactListingRequestBodyLicenseFilter()
            .operator(ArtifactListingRequestBodyLicenseFilter.OperatorEnum.EQUALS)
            .value(licenseValue);
    normalisedSbomComponentService.getOrchestrationIds("account", "org", "project", licenseFilter);
    verify(sbomComponentRepo, times(1)).findAll(criteriaArgumentCaptor.capture(), any());
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document document = criteria.getCriteriaObject();
    assertEquals(4, document.size());
    assertEquals(licenseValue, document.get(NormalizedSBOMEntityKeys.packageLicense));
  }
}
