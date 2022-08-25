/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.governance;

import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.governance.beans.SLOPolicyDTO;
import io.harness.cvng.governance.beans.SLOPolicyDTO.MonitoredServiceStatus;
import io.harness.cvng.governance.services.SLOPolicyExpansionHandler;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class SLOPolicyExpansionHandlerTest extends CvNextGenTestBase {
  @Inject SLOPolicyExpansionHandler sloPolicyExpansionHandler;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;
  private BuilderFactory builderFactory;
  private Map<String, SLOHealthIndicator> sloMappedToTheirHealthIndicators;
  private String pipelineYaml;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    MockitoAnnotations.initMocks(this);
    pipelineYaml = IOUtils.resourceToString("governance/SLOPolicyExpansionHandlerFromStagePipeline.yaml",
        StandardCharsets.UTF_8, this.getClass().getClassLoader());
    BuilderFactory.Context context = BuilderFactory.Context.builder()
                                         .projectParams(ProjectParams.builder()
                                                            .accountIdentifier(randomAlphabetic(20))
                                                            .orgIdentifier(randomAlphabetic(20))
                                                            .projectIdentifier(randomAlphabetic(20))
                                                            .build())
                                         .envIdentifier("env")
                                         .serviceIdentifier("service")
                                         .build();
    builderFactory = BuilderFactory.builder().context(context).build();
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(MonitoredServiceDTO.Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveDTO serviceLevelObjectiveDTO = builderFactory.getServiceLevelObjectiveDTOBuilder().build();
    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), serviceLevelObjectiveDTO);
    List<SLOHealthIndicator> sloHealthIndicatorList = sloHealthIndicatorService.getByMonitoredServiceIdentifiers(
        builderFactory.getProjectParams(), Collections.singletonList(monitoredServiceDTO.getIdentifier()));
    sloMappedToTheirHealthIndicators = new HashMap<>();
    for (SLOHealthIndicator sloHealthIndicator : sloHealthIndicatorList) {
      sloMappedToTheirHealthIndicators.put(sloHealthIndicator.getServiceLevelObjectiveIdentifier(), sloHealthIndicator);
    }
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testExpand() throws IOException {
    SLOPolicyDTO sloPolicyDTO = SLOPolicyDTO.builder()
                                    .sloErrorBudgetRemainingPercentage(100D)
                                    .statusOfMonitoredService(MonitoredServiceStatus.CONFIGURED)
                                    .sloMappedToTheirHealthIndicators(sloMappedToTheirHealthIndicators)
                                    .build();
    final String yaml = IOUtils.resourceToString(
        "governance/SLOPolicyExpansionHandlerInput.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(yaml, JsonNode.class);
    ExpansionRequestMetadata metadataProject =
        ExpansionRequestMetadata.newBuilder()
            .setAccountId(builderFactory.getProjectParams().getAccountIdentifier())
            .setOrgId(builderFactory.getProjectParams().getOrgIdentifier())
            .setProjectId(builderFactory.getProjectParams().getProjectIdentifier())
            .build();
    ExpansionResponse expansionResponse = sloPolicyExpansionHandler.expand(jsonNode, metadataProject, null);
    assertThat(expansionResponse.isSuccess()).isTrue();
    assertThat(expansionResponse.getKey()).isEqualTo("sloPolicy");
    assertThat(expansionResponse.getValue().toJson()).isEqualTo(JsonUtils.asJson(sloPolicyDTO));
    assertThat(expansionResponse.getPlacement()).isEqualTo(ExpansionPlacementStrategy.APPEND);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExpand_withServiceKeyInput() throws IOException {
    SLOPolicyDTO sloPolicyDTO = SLOPolicyDTO.builder()
                                    .sloErrorBudgetRemainingPercentage(100D)
                                    .statusOfMonitoredService(MonitoredServiceStatus.CONFIGURED)
                                    .sloMappedToTheirHealthIndicators(sloMappedToTheirHealthIndicators)
                                    .build();
    final String yaml = IOUtils.resourceToString("governance/SLOPolicyExpansionHandlerWithServiceKey.json",
        StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(yaml, JsonNode.class);
    ExpansionRequestMetadata metadataProject =
        ExpansionRequestMetadata.newBuilder()
            .setAccountId(builderFactory.getProjectParams().getAccountIdentifier())
            .setOrgId(builderFactory.getProjectParams().getOrgIdentifier())
            .setProjectId(builderFactory.getProjectParams().getProjectIdentifier())
            .build();
    ExpansionResponse expansionResponse = sloPolicyExpansionHandler.expand(jsonNode, metadataProject, null);
    assertThat(expansionResponse.isSuccess()).isTrue();
    assertThat(expansionResponse.getKey()).isEqualTo("sloPolicy");
    assertThat(expansionResponse.getValue().toJson()).isEqualTo(JsonUtils.asJson(sloPolicyDTO));
    assertThat(expansionResponse.getPlacement()).isEqualTo(ExpansionPlacementStrategy.APPEND);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testExpand_notConfigured() throws IOException {
    SLOPolicyDTO sloPolicyDTO =
        SLOPolicyDTO.builder().statusOfMonitoredService(MonitoredServiceStatus.NOT_CONFIGURED).build();
    final String yaml = IOUtils.resourceToString("governance/SLOPolicyExpansionHandlerWrongInput.json",
        StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(yaml, JsonNode.class);
    ExpansionRequestMetadata metadataProject =
        ExpansionRequestMetadata.newBuilder()
            .setAccountId(builderFactory.getProjectParams().getAccountIdentifier())
            .setOrgId(builderFactory.getProjectParams().getOrgIdentifier())
            .setProjectId(builderFactory.getProjectParams().getProjectIdentifier())
            .build();
    ExpansionResponse expansionResponse = sloPolicyExpansionHandler.expand(jsonNode, metadataProject, null);
    assertThat(expansionResponse.isSuccess()).isTrue();
    assertThat(expansionResponse.getKey()).isEqualTo("sloPolicy");
    assertThat(expansionResponse.getValue().toJson()).isEqualTo(JsonUtils.asJson(sloPolicyDTO));
    assertThat(expansionResponse.getPlacement()).isEqualTo(ExpansionPlacementStrategy.APPEND);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testExpand_withFromStage() throws IOException {
    SLOPolicyDTO sloPolicyDTO = SLOPolicyDTO.builder()
                                    .sloErrorBudgetRemainingPercentage(100D)
                                    .statusOfMonitoredService(MonitoredServiceStatus.CONFIGURED)
                                    .sloMappedToTheirHealthIndicators(sloMappedToTheirHealthIndicators)
                                    .build();
    final String stageYaml = IOUtils.resourceToString(
        "governance/SLOPolicyExpansionHandlerFromStage.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(stageYaml, JsonNode.class);
    ExpansionRequestMetadata metadataProject =
        ExpansionRequestMetadata.newBuilder()
            .setAccountId(builderFactory.getProjectParams().getAccountIdentifier())
            .setOrgId(builderFactory.getProjectParams().getOrgIdentifier())
            .setProjectId(builderFactory.getProjectParams().getProjectIdentifier())
            .setYaml(ByteString.copyFromUtf8(pipelineYaml))
            .build();
    ExpansionResponse expansionResponse = sloPolicyExpansionHandler.expand(jsonNode, metadataProject, null);
    assertThat(expansionResponse.isSuccess()).isTrue();
    assertThat(expansionResponse.getKey()).isEqualTo("sloPolicy");
    assertThat(expansionResponse.getValue().toJson()).isEqualTo(JsonUtils.asJson(sloPolicyDTO));
    assertThat(expansionResponse.getPlacement()).isEqualTo(ExpansionPlacementStrategy.APPEND);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testExpand_withServiceIdentifier() throws IOException {
    SLOPolicyDTO sloPolicyDTO = SLOPolicyDTO.builder()
                                    .sloErrorBudgetRemainingPercentage(100D)
                                    .statusOfMonitoredService(MonitoredServiceStatus.CONFIGURED)
                                    .sloMappedToTheirHealthIndicators(sloMappedToTheirHealthIndicators)
                                    .build();
    final String stageYaml = IOUtils.resourceToString("governance/SLOPolicyExpansionHandlerServiceIdentifier.json",
        StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(stageYaml, JsonNode.class);
    ExpansionRequestMetadata metadataProject =
        ExpansionRequestMetadata.newBuilder()
            .setAccountId(builderFactory.getProjectParams().getAccountIdentifier())
            .setOrgId(builderFactory.getProjectParams().getOrgIdentifier())
            .setProjectId(builderFactory.getProjectParams().getProjectIdentifier())
            .setYaml(ByteString.copyFromUtf8(pipelineYaml))
            .build();
    ExpansionResponse expansionResponse = sloPolicyExpansionHandler.expand(jsonNode, metadataProject, null);
    assertThat(expansionResponse.isSuccess()).isTrue();
    assertThat(expansionResponse.getKey()).isEqualTo("sloPolicy");
    assertThat(expansionResponse.getValue().toJson()).isEqualTo(JsonUtils.asJson(sloPolicyDTO));
    assertThat(expansionResponse.getPlacement()).isEqualTo(ExpansionPlacementStrategy.APPEND);
  }

  @Test(expected = IllegalStateException.class)
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testExpand_withoutFromStage() throws IOException {
    SLOPolicyDTO sloPolicyDTO = SLOPolicyDTO.builder()
                                    .sloErrorBudgetRemainingPercentage(100D)
                                    .statusOfMonitoredService(MonitoredServiceStatus.CONFIGURED)
                                    .sloMappedToTheirHealthIndicators(sloMappedToTheirHealthIndicators)
                                    .build();
    final String stageYaml = IOUtils.resourceToString("governance/SLOPolicyExpansionHandlerInValidStage.json",
        StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(stageYaml, JsonNode.class);
    ExpansionRequestMetadata metadataProject =
        ExpansionRequestMetadata.newBuilder()
            .setAccountId(builderFactory.getProjectParams().getAccountIdentifier())
            .setOrgId(builderFactory.getProjectParams().getOrgIdentifier())
            .setProjectId(builderFactory.getProjectParams().getProjectIdentifier())
            .setYaml(ByteString.copyFromUtf8(pipelineYaml))
            .build();
    ExpansionResponse expansionResponse = sloPolicyExpansionHandler.expand(jsonNode, metadataProject, null);
    assertThat(expansionResponse.isSuccess()).isTrue();
    assertThat(expansionResponse.getKey()).isEqualTo("sloPolicy");
    assertThat(expansionResponse.getValue().toJson()).isEqualTo(JsonUtils.asJson(sloPolicyDTO));
    assertThat(expansionResponse.getPlacement()).isEqualTo(ExpansionPlacementStrategy.APPEND);
  }
}
