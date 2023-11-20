/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.harness.BuilderFactory;
import io.harness.SSCAManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.entities.ArtifactDetails;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.CdInstanceSummaryRepo;
import io.harness.rule.Owner;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.CdInstanceSummary.CdInstanceSummaryBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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
    CdInstanceSummaryBuilder builder = builderFactory.getCdInstanceSummaryBuilder();
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

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateCdInstanceSummary() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream("pms-execution-response-with-slsa.json");
    Map<String, Object> pmsJsonResponse = objectMapper.readValue(inputStream, Map.class);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(pmsJsonResponse);
    ArtifactEntity artifact =
        builderFactory.getArtifactEntityBuilder().name("autosscauser/autosscauser-auto").tag("5").build();
    CdInstanceSummary cdInstanceSummary =
        cdInstanceSummaryService.createInstanceSummary(builderFactory.getInstanceNGEntityBuilder().build(), artifact);
    assertThat(cdInstanceSummary.getArtifactCorrelationId()).isEqualTo("artifactCorrelationId");
    assertThat(cdInstanceSummary.getAccountIdentifier()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(cdInstanceSummary.getOrgIdentifier()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
    assertThat(cdInstanceSummary.getProjectIdentifier()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(cdInstanceSummary.getEnvIdentifier()).isEqualTo("envId");
    assertThat(cdInstanceSummary.getEnvName()).isEqualTo("envName");
    assertThat(cdInstanceSummary.getEnvType()).isEqualTo(EnvType.Production);

    assertThat(cdInstanceSummary.getLastPipelineName()).isEqualTo("SLSA attestation and verification");
    assertThat(cdInstanceSummary.getLastPipelineExecutionName()).isEqualTo("K8sDeploy");
    assertThat(cdInstanceSummary.getLastPipelineExecutionId()).isEqualTo("executionId");
    assertThat(cdInstanceSummary.getLastDeployedAt())
        .isEqualTo(Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC).millis());
    assertThat(cdInstanceSummary.getLastDeployedByName()).isEqualTo("username");
    assertThat(cdInstanceSummary.getLastDeployedById()).isEqualTo("userId");
    assertThat(cdInstanceSummary.getTriggerType()).isEqualTo("MANUAL");
    assertThat(cdInstanceSummary.getSequenceId()).isEqualTo("5");
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().isHasSlsaVerification()).isEqualTo(true);
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().getSlsaPolicyOutcomeStatus()).isEqualTo("warning");
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().getProvenanceArtifact()).isNotNull();
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().getProvenanceArtifact().get("predicateType").asText())
        .isEqualTo("https://slsa.dev/provenance/v1");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateCdInstanceSummary_withUnrelatedSlsa() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream("pms-execution-response-with-uncorrelated-slsa.json");
    Map<String, Object> pmsJsonResponse = objectMapper.readValue(inputStream, Map.class);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(pmsJsonResponse);
    ArtifactEntity artifact =
        builderFactory.getArtifactEntityBuilder().name("autosscauser/autosscauser-auto").tag("5").build();
    CdInstanceSummary cdInstanceSummary =
        cdInstanceSummaryService.createInstanceSummary(builderFactory.getInstanceNGEntityBuilder().build(), artifact);
    assertThat(cdInstanceSummary.getArtifactCorrelationId()).isEqualTo("artifactCorrelationId");
    assertThat(cdInstanceSummary.getAccountIdentifier()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(cdInstanceSummary.getOrgIdentifier()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
    assertThat(cdInstanceSummary.getProjectIdentifier()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(cdInstanceSummary.getEnvIdentifier()).isEqualTo("envId");
    assertThat(cdInstanceSummary.getEnvName()).isEqualTo("envName");
    assertThat(cdInstanceSummary.getEnvType()).isEqualTo(EnvType.Production);

    assertThat(cdInstanceSummary.getLastPipelineName()).isEqualTo("SLSA attestation and verification");
    assertThat(cdInstanceSummary.getLastPipelineExecutionName()).isEqualTo("K8sDeploy");
    assertThat(cdInstanceSummary.getLastPipelineExecutionId()).isEqualTo("executionId");
    assertThat(cdInstanceSummary.getLastDeployedAt())
        .isEqualTo(Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC).millis());
    assertThat(cdInstanceSummary.getLastDeployedByName()).isEqualTo("username");
    assertThat(cdInstanceSummary.getLastDeployedById()).isEqualTo("userId");
    assertThat(cdInstanceSummary.getTriggerType()).isEqualTo("MANUAL");
    assertThat(cdInstanceSummary.getSequenceId()).isEqualTo("5");
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().isHasSlsaVerification()).isEqualTo(false);
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().getSlsaPolicyOutcomeStatus()).isNull();
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().getProvenanceArtifact()).isNull();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateCdInstanceSummary_withoutSlsa() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream("pms-execution-response-without-slsa.json");
    Map<String, Object> pmsJsonResponse = objectMapper.readValue(inputStream, Map.class);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(pmsJsonResponse);
    ArtifactEntity artifact =
        builderFactory.getArtifactEntityBuilder().name("autosscauser/autosscauser-auto").tag("5").build();
    CdInstanceSummary cdInstanceSummary =
        cdInstanceSummaryService.createInstanceSummary(builderFactory.getInstanceNGEntityBuilder().build(), artifact);
    assertThat(cdInstanceSummary.getArtifactCorrelationId()).isEqualTo("artifactCorrelationId");
    assertThat(cdInstanceSummary.getAccountIdentifier()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(cdInstanceSummary.getOrgIdentifier()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
    assertThat(cdInstanceSummary.getProjectIdentifier()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(cdInstanceSummary.getEnvIdentifier()).isEqualTo("envId");
    assertThat(cdInstanceSummary.getEnvName()).isEqualTo("envName");
    assertThat(cdInstanceSummary.getEnvType()).isEqualTo(EnvType.Production);

    assertThat(cdInstanceSummary.getLastPipelineName()).isNull();
    assertThat(cdInstanceSummary.getLastPipelineExecutionName()).isEqualTo("K8sDeploy");
    assertThat(cdInstanceSummary.getLastPipelineExecutionId()).isEqualTo("executionId");
    assertThat(cdInstanceSummary.getLastDeployedAt())
        .isEqualTo(Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC).millis());
    assertThat(cdInstanceSummary.getLastDeployedByName()).isEqualTo("username");
    assertThat(cdInstanceSummary.getLastDeployedById()).isEqualTo("userId");
    assertThat(cdInstanceSummary.getTriggerType()).isEqualTo("MANUAL");
    assertThat(cdInstanceSummary.getSequenceId()).isEqualTo("5");
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().isHasSlsaVerification()).isEqualTo(false);
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().getSlsaPolicyOutcomeStatus()).isNull();
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().getProvenanceArtifact()).isNull();
  }
}
