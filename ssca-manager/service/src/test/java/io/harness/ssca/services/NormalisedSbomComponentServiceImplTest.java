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
import io.harness.spec.server.ssca.v1.model.ComponentFilter;
import io.harness.spec.server.ssca.v1.model.LicenseFilter;
import io.harness.spec.server.ssca.v1.model.Operator;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMEntityKeys;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.mongodb.BasicDBList;
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
    String componentValue1 = randomAlphabetic(10);
    String componentValue2 = randomAlphabetic(10);
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(sbomComponentRepo.findDistinctOrchestrationIds(any())).thenReturn(List.of("orch1", "orch2"));
    LicenseFilter licenseFilter = new LicenseFilter().operator(Operator.EQUALS).value(licenseValue);
    List<ComponentFilter> componentFilter =
        Lists.newArrayList(new ComponentFilter()
                               .fieldName(ComponentFilter.FieldNameEnum.COMPONENTNAME)
                               .operator(Operator.CONTAINS)
                               .value(componentValue1),
            new ComponentFilter()
                .fieldName(ComponentFilter.FieldNameEnum.COMPONENTVERSION)
                .operator(Operator.STARTSWITH)
                .value(componentValue2));
    List<String> orchestrationIds =
        normalisedSbomComponentService.getOrchestrationIds("account", "org", "project", licenseFilter, componentFilter);
    assertEquals(orchestrationIds.size(), 2);
    verify(sbomComponentRepo, times(1)).findDistinctOrchestrationIds(criteriaArgumentCaptor.capture());
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document document = criteria.getCriteriaObject();
    assertEquals(4, document.size());
    BasicDBList filterList = (BasicDBList) document.get("$and");
    assertEquals(filterList.size(), 2);
    Document licenseDocument = (Document) filterList.get(0);
    assertEquals(licenseDocument.get(NormalizedSBOMEntityKeys.packageLicense), licenseValue);
    Document componetDocument = (Document) filterList.get(1);
    BasicDBList componentFilterList = (BasicDBList) componetDocument.get("$and");
    assertEquals(componentFilterList.size(), 2);
    assertThat(((Document) componentFilterList.get(0)).get(NormalizedSBOMEntityKeys.packageName).toString())
        .isEqualTo(componentValue1);
    assertThat(((Document) componentFilterList.get(1)).get(NormalizedSBOMEntityKeys.packageVersion).toString())
        .isEqualTo("^" + componentValue2);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testGetOrchestrationIdsWithNoLicenseFilter() {
    String componentValue1 = randomAlphabetic(10);
    String componentValue2 = randomAlphabetic(10);
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(sbomComponentRepo.findDistinctOrchestrationIds(any())).thenReturn(List.of("orch1", "orch2"));
    LicenseFilter licenseFilter = null;
    List<ComponentFilter> componentFilter =
        Lists.newArrayList(new ComponentFilter()
                               .fieldName(ComponentFilter.FieldNameEnum.COMPONENTNAME)
                               .operator(Operator.CONTAINS)
                               .value(componentValue1),
            new ComponentFilter()
                .fieldName(ComponentFilter.FieldNameEnum.COMPONENTVERSION)
                .operator(Operator.STARTSWITH)
                .value(componentValue2));
    List<String> orchestrationIds =
        normalisedSbomComponentService.getOrchestrationIds("account", "org", "project", licenseFilter, componentFilter);
    assertEquals(orchestrationIds.size(), 2);
    verify(sbomComponentRepo, times(1)).findDistinctOrchestrationIds(criteriaArgumentCaptor.capture());
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document document = criteria.getCriteriaObject();
    assertEquals(4, document.size());
    BasicDBList filterList = (BasicDBList) document.get("$and");
    assertEquals(filterList.size(), 2);
    assertEquals(((Document) filterList.get(0)).size(), 0);
    Document componetDocument = (Document) filterList.get(1);
    BasicDBList componentFilterList = (BasicDBList) componetDocument.get("$and");
    assertEquals(componentFilterList.size(), 2);
    assertThat(((Document) componentFilterList.get(0)).get(NormalizedSBOMEntityKeys.packageName).toString())
        .isEqualTo(componentValue1);
    assertThat(((Document) componentFilterList.get(1)).get(NormalizedSBOMEntityKeys.packageVersion).toString())
        .isEqualTo("^" + componentValue2);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testGetOrchestrationIdsWithNoComponentFilter() {
    String licenseValue = randomAlphabetic(10);
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(sbomComponentRepo.findDistinctOrchestrationIds(any())).thenReturn(List.of("orch1", "orch2"));
    LicenseFilter licenseFilter = new LicenseFilter().operator(Operator.EQUALS).value(licenseValue);
    List<ComponentFilter> componentFilter = Lists.newArrayList();
    List<String> orchestrationIds =
        normalisedSbomComponentService.getOrchestrationIds("account", "org", "project", licenseFilter, componentFilter);
    assertEquals(orchestrationIds.size(), 2);
    verify(sbomComponentRepo, times(1)).findDistinctOrchestrationIds(criteriaArgumentCaptor.capture());
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document document = criteria.getCriteriaObject();
    assertEquals(4, document.size());
    BasicDBList filterList = (BasicDBList) document.get("$and");
    assertEquals(filterList.size(), 2);
    Document licenseDocument = (Document) filterList.get(0);
    assertEquals(licenseDocument.get(NormalizedSBOMEntityKeys.packageLicense), licenseValue);
    assertEquals(((Document) filterList.get(1)).size(), 0);
  }
}
