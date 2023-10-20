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
import io.harness.repositories.ArtifactRepository;
import io.harness.repositories.CdInstanceSummaryRepo;
import io.harness.repositories.EnforcementSummaryRepo;
import io.harness.repositories.SBOMComponentRepo;
import io.harness.rule.Owner;
import io.harness.spec.server.ssca.v1.model.ArtifactComponentViewResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewResponse.AttestedStatusEnum;
import io.harness.spec.server.ssca.v1.model.ArtifactListingRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactListingRequestBody.EnvironmentTypeEnum;
import io.harness.spec.server.ssca.v1.model.ArtifactListingRequestBody.PolicyViolationEnum;
import io.harness.spec.server.ssca.v1.model.ArtifactListingResponse;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.CdInstanceSummary.CdInstanceSummaryBuilder;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMComponentEntityBuilder;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;

public class ArtifactServiceImplTest extends SSCAManagerTestBase {
  @Inject ArtifactService artifactService;
  @Inject NormalisedSbomComponentService normalisedSbomComponentService;
  @Mock SBOMComponentRepo sbomComponentRepo;
  @Mock ArtifactRepository artifactRepository;
  @Mock EnforcementSummaryRepo enforcementSummaryRepo;
  @Mock CdInstanceSummaryRepo cdInstanceSummaryRepo;

  @Inject CdInstanceSummaryService cdInstanceSummaryService;
  private BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(artifactService, "artifactRepository", artifactRepository, true);
    FieldUtils.writeField(artifactService, "enforcementSummaryRepo", enforcementSummaryRepo, true);
    FieldUtils.writeField(artifactService, "cdInstanceSummaryRepo", cdInstanceSummaryRepo, true);
    FieldUtils.writeField(normalisedSbomComponentService, "sbomComponentRepo", sbomComponentRepo, true);
    FieldUtils.writeField(artifactService, "normalisedSbomComponentService", normalisedSbomComponentService, true);
    FieldUtils.writeField(cdInstanceSummaryService, "cdInstanceSummaryRepo", cdInstanceSummaryRepo, true);
    FieldUtils.writeField(artifactService, "cdInstanceSummaryService", cdInstanceSummaryService, true);
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetArtifactFromSbomPayload() {
    ArtifactEntity artifact = artifactService.getArtifactFromSbomPayload(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        builderFactory.getSbomProcessRequestBody("spdx_json", "testData"), builderFactory.getSpdxDTOBuilder().build());
    assertThat(artifact.getArtifactId())
        .isEqualTo(UUID.nameUUIDFromBytes(("https://index.docker.com"
                                              + ":"
                                              + "test/image")
                                              .getBytes())
                       .toString());
    assertThat(artifact.getOrchestrationId()).isEqualTo("stepExecution-1");
    assertThat(artifact.getArtifactCorrelationId()).isEqualTo("index.docker.com/test/image:tag");
    assertThat(artifact.getUrl()).isEqualTo("https://index.docker.com");
    assertThat(artifact.getName()).isEqualTo("test/image");
    assertThat(artifact.getType()).isEqualTo("image/repo");
    assertThat(artifact.getTag()).isEqualTo("tag");
    assertThat(artifact.getAccountId()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(artifact.getOrgId()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
    assertThat(artifact.getProjectId()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(artifact.getPipelineExecutionId()).isEqualTo("execution-1");
    assertThat(artifact.getPipelineId()).isEqualTo("pipelineId");
    assertThat(artifact.getStageId()).isEqualTo("stageId");
    assertThat(artifact.getSequenceId()).isEqualTo("1");
    assertThat(artifact.getStepId()).isEqualTo("orchestrationStepId");
    assertThat(artifact.getSbomName()).isEqualTo("testSbom");
    assertThat(artifact.getCreatedOn()).isBefore(Instant.now());
    assertThat(artifact.isAttested()).isEqualTo(true);
    assertThat(artifact.getAttestedFileUrl()).isEqualTo("www.google.com");
    assertThat(artifact.getSbom())
        .isEqualTo(ArtifactEntity.Sbom.builder()
                       .sbomFormat("spdx_json")
                       .tool("syft")
                       .sbomVersion("3.0")
                       .toolVersion("2.0")
                       .build());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetArtifact() {
    Mockito
        .when(artifactRepository.findByAccountIdAndOrgIdAndProjectIdAndOrchestrationId(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(Optional.ofNullable(builderFactory.getArtifactEntityBuilder().build()));
    ArtifactEntity artifact =
        artifactService
            .getArtifact(builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(),
                builderFactory.getContext().getProjectIdentifier(), "stepExecutionId")
            .get();
    assertThat(artifact.getAccountId()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(artifact.getOrgId()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
    assertThat(artifact.getProjectId()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(artifact.getArtifactId()).isEqualTo("artifactId");
    assertThat(artifact.getOrchestrationId()).isEqualTo("stepExecutionId");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetArtifact_byArtifactId() {
    Mockito
        .when(artifactRepository.findFirstByAccountIdAndOrgIdAndProjectIdAndArtifactIdLike(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(Optional.ofNullable(builderFactory.getArtifactEntityBuilder().build()));
    ArtifactEntity artifact =
        artifactService
            .getArtifact(builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(),
                builderFactory.getContext().getProjectIdentifier(), "artifactId", Sort.by("ASC", "name"))
            .get();
    assertThat(artifact.getAccountId()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(artifact.getOrgId()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
    assertThat(artifact.getProjectId()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(artifact.getArtifactId()).isEqualTo("artifactId");
    assertThat(artifact.getOrchestrationId()).isEqualTo("stepExecutionId");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetArtifactByCorrelationId() {
    Mockito.when(artifactRepository.findOne(Mockito.any()))
        .thenReturn(builderFactory.getArtifactEntityBuilder().build());
    ArtifactEntity artifact = artifactService.getArtifactByCorrelationId(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        "artifactCorrelationId");
    assertThat(artifact.getAccountId()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(artifact.getOrgId()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
    assertThat(artifact.getProjectId()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(artifact.getArtifactId()).isEqualTo("artifactId");
    assertThat(artifact.getOrchestrationId()).isEqualTo("stepExecutionId");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetLatestArtifact() {
    Mockito.when(artifactRepository.findOne(Mockito.any()))
        .thenReturn(builderFactory.getArtifactEntityBuilder().build());
    ArtifactEntity artifact = artifactService.getLatestArtifact(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        "artifactId", "tag");
    assertThat(artifact.getAccountId()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(artifact.getOrgId()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
    assertThat(artifact.getProjectId()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(artifact.getArtifactId()).isEqualTo("artifactId");
    assertThat(artifact.getOrchestrationId()).isEqualTo("stepExecutionId");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGenerateArtifactId() {
    String generatedId = artifactService.generateArtifactId("https://index.docker.com/v2/", "arpit/image-5");
    assertThat(generatedId).isEqualTo("fab60212-b5a7-3449-97fb-792c4d9c9bff");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testSaveArtifactAndInvalidateOldArtifact() {}

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testListLatestArtifacts() {
    List<ArtifactEntity> artifactEntities = Arrays.asList(builderFactory.getArtifactEntityBuilder()
                                                              .artifactId("artifactId")
                                                              .artifactCorrelationId("artifactCorrelationId")
                                                              .build(),
        builderFactory.getArtifactEntityBuilder()
            .artifactId("artifact2")
            .artifactCorrelationId("artifactCorrelation2")
            .build());
    Mockito.when(artifactRepository.findAll(Mockito.any(Aggregation.class))).thenReturn(artifactEntities);

    Mockito.when(artifactRepository.getCount(Mockito.any())).thenReturn(3L);

    Mockito.when(enforcementSummaryRepo.findAll(Mockito.any(Aggregation.class)))
        .thenReturn(List.of(builderFactory.getEnforcementSummaryBuilder().build()));

    Page<ArtifactListingResponse> artifactEntityPage = artifactService.listLatestArtifacts(
        builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(),
        builderFactory.getContext().getProjectIdentifier(), Pageable.ofSize(2).withPage(0));

    List<ArtifactListingResponse> artifactListingResponses = artifactEntityPage.toList();

    assertThat(artifactEntityPage.getTotalElements()).isEqualTo(3);
    assertThat(artifactEntityPage.getTotalPages()).isEqualTo(2);
    assertThat(artifactListingResponses.size()).isEqualTo(2);

    assertThat(artifactListingResponses.get(0).getArtifactId()).isEqualTo("artifactId");
    assertThat(artifactListingResponses.get(0).getArtifactName()).isEqualTo("test/image");
    assertThat(artifactListingResponses.get(0).getTag()).isEqualTo("tag");
    assertThat(artifactListingResponses.get(0).getAllowListViolationCount()).isEqualTo(0);
    assertThat(artifactListingResponses.get(0).getDenyListViolationCount()).isEqualTo(0);
    assertThat(artifactListingResponses.get(0).getComponentsCount()).isEqualTo(35);
    assertThat(artifactListingResponses.get(0).getNonProdEnvCount()).isEqualTo(1);
    assertThat(artifactListingResponses.get(0).getProdEnvCount()).isEqualTo(2);
    // assertThat(artifactListingResponses.get(0).getSbomUrl()).isEqualTo("artifact1");
    assertThat(artifactListingResponses.get(0).getUpdatedAt())
        .isLessThanOrEqualTo(String.format("%d", Instant.now().toEpochMilli()));

    assertThat(artifactListingResponses.get(1).getArtifactId()).isEqualTo("artifact2");
    assertThat(artifactListingResponses.get(1).getArtifactName()).isEqualTo("test/image");
    assertThat(artifactListingResponses.get(1).getTag()).isEqualTo("tag");
    assertThat(artifactListingResponses.get(1).getAllowListViolationCount()).isEqualTo(0);
    assertThat(artifactListingResponses.get(1).getDenyListViolationCount()).isEqualTo(0);
    assertThat(artifactListingResponses.get(1).getComponentsCount()).isEqualTo(35);
    assertThat(artifactListingResponses.get(1).getNonProdEnvCount()).isEqualTo(1);
    assertThat(artifactListingResponses.get(1).getProdEnvCount()).isEqualTo(2);
    // assertThat(artifactListingResponses.get(0).getSbomUrl()).isEqualTo("artifact1");
    assertThat(artifactListingResponses.get(1).getUpdatedAt())
        .isLessThanOrEqualTo(String.format("%d", Instant.now().toEpochMilli()));
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testListArtifacts() {
    List<ArtifactEntity> artifactEntities = Arrays.asList(builderFactory.getArtifactEntityBuilder()
                                                              .artifactId("artifactId")
                                                              .artifactCorrelationId("artifactCorrelationId")
                                                              .build(),
        builderFactory.getArtifactEntityBuilder()
            .artifactId("artifact2")
            .artifactCorrelationId("artifactCorrelation2")
            .prodEnvCount(0)
            .nonProdEnvCount(1)
            .build());
    Mockito.when(artifactRepository.findAll(Mockito.any(), Mockito.any()))
        .thenReturn(new PageImpl<>(artifactEntities, Pageable.ofSize(2).withPage(0), 3));

    Mockito.when(enforcementSummaryRepo.findAll(Mockito.any(Aggregation.class)))
        .thenReturn(List.of(builderFactory.getEnforcementSummaryBuilder().build()));
    ArtifactListingRequestBody filterBody = new ArtifactListingRequestBody()
                                                .environmentType(EnvironmentTypeEnum.ALL)
                                                .policyViolation(PolicyViolationEnum.ALLOW);

    Page<ArtifactListingResponse> artifactEntityPage = artifactService.listArtifacts(
        builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(),
        builderFactory.getContext().getProjectIdentifier(), filterBody, Pageable.ofSize(2).withPage(0));

    List<ArtifactListingResponse> artifactListingResponses = artifactEntityPage.toList();

    assertThat(artifactEntityPage.getTotalElements()).isEqualTo(3);
    assertThat(artifactEntityPage.getTotalPages()).isEqualTo(2);
    assertThat(artifactListingResponses.size()).isEqualTo(2);

    assertThat(artifactListingResponses.get(0).getArtifactId()).isEqualTo("artifactId");
    assertThat(artifactListingResponses.get(0).getArtifactName()).isEqualTo("test/image");
    assertThat(artifactListingResponses.get(0).getTag()).isEqualTo("tag");
    assertThat(artifactListingResponses.get(0).getAllowListViolationCount()).isEqualTo(0);
    assertThat(artifactListingResponses.get(0).getDenyListViolationCount()).isEqualTo(0);
    assertThat(artifactListingResponses.get(0).getComponentsCount()).isEqualTo(35);
    assertThat(artifactListingResponses.get(0).getNonProdEnvCount()).isEqualTo(1);
    assertThat(artifactListingResponses.get(0).getProdEnvCount()).isEqualTo(2);
    assertThat(artifactListingResponses.get(0).getUpdatedAt())
        .isLessThanOrEqualTo(String.format("%d", Instant.now().toEpochMilli()));

    assertThat(artifactListingResponses.get(1).getArtifactId()).isEqualTo("artifact2");
    assertThat(artifactListingResponses.get(1).getArtifactName()).isEqualTo("test/image");
    assertThat(artifactListingResponses.get(1).getTag()).isEqualTo("tag");
    assertThat(artifactListingResponses.get(1).getAllowListViolationCount()).isEqualTo(0);
    assertThat(artifactListingResponses.get(1).getDenyListViolationCount()).isEqualTo(0);
    assertThat(artifactListingResponses.get(1).getComponentsCount()).isEqualTo(35);
    assertThat(artifactListingResponses.get(1).getNonProdEnvCount()).isEqualTo(1);
    assertThat(artifactListingResponses.get(1).getProdEnvCount()).isEqualTo(0);
    // assertThat(artifactListingResponses.get(0).getSbomUrl()).isEqualTo("artifact1");
    assertThat(artifactListingResponses.get(1).getUpdatedAt())
        .isLessThanOrEqualTo(String.format("%d", Instant.now().toEpochMilli()));
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetArtifactComponentView_noFilter() {
    Mockito.when(artifactRepository.findOne(Mockito.any()))
        .thenReturn(builderFactory.getArtifactEntityBuilder().build());
    NormalizedSBOMComponentEntityBuilder builder = builderFactory.getNormalizedSBOMComponentBuilder();
    Page<NormalizedSBOMComponentEntity> entities =
        new PageImpl<>(List.of(builder.build(), builder.build()), Pageable.ofSize(2).withPage(0), 5);

    Mockito.when(sbomComponentRepo.findAll(Mockito.any(), Mockito.any())).thenReturn(entities);

    Page<ArtifactComponentViewResponse> responses = artifactService.getArtifactComponentView(
        builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(),
        builderFactory.getContext().getProjectIdentifier(), "artifactId", "tag", null, Pageable.ofSize(2).withPage(0));

    List<ArtifactComponentViewResponse> responseList = responses.get().collect(Collectors.toList());

    assertThat(responses.getTotalElements()).isEqualTo(5);
    assertThat(responseList.size()).isEqualTo(2);
    assertThat(responseList.get(0).getName()).isEqualTo("packageName");
    assertThat(responseList.get(0).getLicense()).isEqualTo("license1, license2");
    assertThat(responseList.get(0).getPurl()).isEqualTo("purl");
    assertThat(responseList.get(0).getPackageManager()).isEqualTo("packageManager");
    assertThat(responseList.get(0).getSupplier()).isEqualTo("packageOriginatorName");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetArtifactDeploymentView_noFilter() {
    Mockito.when(artifactRepository.findOne(Mockito.any()))
        .thenReturn(builderFactory.getArtifactEntityBuilder().build());
    CdInstanceSummaryBuilder builder = builderFactory.getCdInstanceSummaryBuilder();
    Page<CdInstanceSummary> entities =
        new PageImpl<>(List.of(builder.envIdentifier("env1").build(), builder.envIdentifier("env2").build()),
            Pageable.ofSize(2).withPage(0), 5);

    Mockito.when(cdInstanceSummaryRepo.findAll(Mockito.any(), Mockito.any())).thenReturn(entities);

    Page<ArtifactDeploymentViewResponse> responses = artifactService.getArtifactDeploymentView(
        builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(),
        builderFactory.getContext().getProjectIdentifier(), "artifactId", "tag", null, Pageable.ofSize(2).withPage(0));

    List<ArtifactDeploymentViewResponse> responseList = responses.get().collect(Collectors.toList());
    assertThat(responseList.size()).isEqualTo(2);
    assertThat(responseList.get(0).getName()).isEqualTo("envName");
    assertThat(responseList.get(0).getId()).isEqualTo("env1");
    assertThat(responseList.get(0).getPipelineId()).isEqualTo("K8sDeploy");
    assertThat(responseList.get(0).getPipelineExecutionId()).isEqualTo("lastExecutionId");
    assertThat(responseList.get(0).getTriggeredBy()).isEqualTo("username");
    assertThat(responseList.get(0).getAttestedStatus()).isEqualTo(AttestedStatusEnum.PASS);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpdateArtifactEnvCount_prod() {
    ArtifactEntity artifact = builderFactory.getArtifactEntityBuilder().build();
    artifactService.updateArtifactEnvCount(artifact, EnvType.Production, 1);
    ArgumentCaptor<ArtifactEntity> argument = ArgumentCaptor.forClass(ArtifactEntity.class);
    Mockito.verify(artifactRepository).save(argument.capture());
    assertThat(argument.getValue().getNonProdEnvCount()).isEqualTo(1);
    assertThat(argument.getValue().getProdEnvCount()).isEqualTo(3);

    artifactService.updateArtifactEnvCount(artifact, EnvType.Production, -4);
    assertThat(argument.getValue().getNonProdEnvCount()).isEqualTo(1);
    assertThat(argument.getValue().getProdEnvCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpdateArtifactEnvCount_nonProd() {
    ArtifactEntity artifact = builderFactory.getArtifactEntityBuilder().build();
    artifactService.updateArtifactEnvCount(artifact, EnvType.PreProduction, 1);
    ArgumentCaptor<ArtifactEntity> argument = ArgumentCaptor.forClass(ArtifactEntity.class);
    Mockito.verify(artifactRepository).save(argument.capture());
    assertThat(argument.getValue().getNonProdEnvCount()).isEqualTo(2);
    assertThat(argument.getValue().getProdEnvCount()).isEqualTo(2);

    artifactService.updateArtifactEnvCount(artifact, EnvType.PreProduction, -4);
    assertThat(argument.getValue().getNonProdEnvCount()).isEqualTo(0);
    assertThat(argument.getValue().getProdEnvCount()).isEqualTo(2);
  }
}
