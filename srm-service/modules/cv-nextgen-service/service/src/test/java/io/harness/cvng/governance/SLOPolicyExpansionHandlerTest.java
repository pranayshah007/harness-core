/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.governance;

import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.DHRUVX;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.reflect.FieldUtils.writeField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.cdng.services.api.CDStageMetaDataService;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.monitoredService.SloHealthIndicatorDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.governance.beans.SLOPolicyDTO;
import io.harness.cvng.governance.beans.SLOPolicyDTO.MonitoredServiceStatus;
import io.harness.cvng.governance.services.SLOPolicyExpansionHandler;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.CDStageMetaDataDTO.ServiceEnvRef;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SLOPolicyExpansionHandlerTest extends CvNextGenTestBase {
  @Inject SLOPolicyExpansionHandler sloPolicyExpansionHandler;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;
  @Mock CDStageMetaDataService cdStageMetaDataService;
  @Mock MonitoredServiceService mockedMonitoredServiceService;
  @Mock SLOHealthIndicatorService mockedSloHealthIndicatorService;
  private BuilderFactory builderFactory;
  private Map<String, SloHealthIndicatorDTO> sloMappedToTheirHealthIndicators;
  private static final String SLO_POLICY = "sloPolicy";
  private ExpansionRequestMetadata metadataProject;
  private List<SLOHealthIndicator> sloHealthIndicatorList;
  private MonitoredServiceDTO monitoredServiceDTO;
  ResponseDTO<CDStageMetaDataDTO> responseDTO;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    MockitoAnnotations.initMocks(this);
    writeField(sloPolicyExpansionHandler, "cdStageMetaDataService", cdStageMetaDataService, true);
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
    monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(MonitoredServiceDTO.Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveDTO);
    sloHealthIndicatorList = sloHealthIndicatorService.getByMonitoredServiceIdentifiers(
        builderFactory.getProjectParams(), Collections.singletonList(monitoredServiceDTO.getIdentifier()));
    sloMappedToTheirHealthIndicators = new HashMap<>();
    for (SLOHealthIndicator sloHealthIndicator : sloHealthIndicatorList) {
      SloHealthIndicatorDTO sloHealthIndicatorDTO =
          SloHealthIndicatorDTO.builder()
              .serviceLevelObjectiveIdentifier(sloHealthIndicator.getServiceLevelObjectiveIdentifier())
              .monitoredServiceIdentifier(sloHealthIndicator.getMonitoredServiceIdentifier())
              .errorBudgetRisk(sloHealthIndicator.getErrorBudgetRisk())
              .errorBudgetBurnRate(sloHealthIndicator.getErrorBudgetBurnRate())
              .errorBudgetRemainingPercentage(sloHealthIndicator.getErrorBudgetRemainingPercentage())
              .errorBudgetRemainingMinutes(sloHealthIndicator.getErrorBudgetRemainingMinutes())
              .build();
      sloMappedToTheirHealthIndicators.put(
          sloHealthIndicator.getServiceLevelObjectiveIdentifier(), sloHealthIndicatorDTO);
    }
    metadataProject = getExpansionRequestMetaData();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testExpand() throws IOException {
    SLOPolicyDTO sloPolicyDTO = SLOPolicyDTO.builder()
                                    .sloErrorBudgetRemainingPercentage(100D)
                                    .statusOfMonitoredService(MonitoredServiceStatus.CONFIGURED)
                                    .slos(sloMappedToTheirHealthIndicators)
                                    .build();
    JsonNode jsonNode = getJsonfromString("governance/SLOPolicyExpansionHandlerInput.json");
    responseDTO = ResponseDTO.newResponse(
        CDStageMetaDataDTO.builder()
            .serviceEnvRefList(
                Collections.singletonList(ServiceEnvRef.builder().serviceRef("service").environmentRef("env").build()))
            .build());
    when(cdStageMetaDataService.getServiceAndEnvironmentRef(any(), any())).thenReturn(responseDTO);

    ExpansionResponse expansionResponse = sloPolicyExpansionHandler.expand(jsonNode, metadataProject, null);
    assertThat(expansionResponse.isSuccess()).isTrue();
    assertThat(expansionResponse.getKey()).isEqualTo(SLO_POLICY);
    assertThat(expansionResponse.getValue().toJson()).isEqualTo(JsonUtils.asJson(sloPolicyDTO));
    assertThat(expansionResponse.getPlacement()).isEqualTo(ExpansionPlacementStrategy.APPEND);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testExpand_notConfigured() throws IOException {
    SLOPolicyDTO sloPolicyDTO =
        SLOPolicyDTO.builder().statusOfMonitoredService(MonitoredServiceStatus.NOT_CONFIGURED).build();
    JsonNode jsonNode = getJsonfromString("governance/SLOPolicyExpansionHandlerWrongInput.json");
    responseDTO = ResponseDTO.newResponse(
        CDStageMetaDataDTO.builder()
            .serviceEnvRefList(Collections.singletonList(
                ServiceEnvRef.builder().serviceRef("service_wrong").environmentRef("env_wrong").build()))
            .build());
    when(cdStageMetaDataService.getServiceAndEnvironmentRef(any(), any())).thenReturn(responseDTO);
    ExpansionResponse expansionResponse = sloPolicyExpansionHandler.expand(jsonNode, metadataProject, null);
    assertThat(expansionResponse.isSuccess()).isTrue();
    assertThat(expansionResponse.getKey()).isEqualTo(SLO_POLICY);
    assertThat(expansionResponse.getValue().toJson()).isEqualTo(JsonUtils.asJson(sloPolicyDTO));
    assertThat(expansionResponse.getPlacement()).isEqualTo(ExpansionPlacementStrategy.APPEND);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExpand_multipleServiceEnvRefs() throws IOException, IllegalAccessException {
    writeField(sloPolicyExpansionHandler, "monitoredServiceService", mockedMonitoredServiceService, true);
    writeField(sloPolicyExpansionHandler, "sloHealthIndicatorService", mockedSloHealthIndicatorService, true);
    JsonNode jsonNode = getJsonfromString("governance/SLOPolicyExpansionHandlerInput.json");
    responseDTO = ResponseDTO.newResponse(
        CDStageMetaDataDTO.builder()
            .serviceEnvRef(ServiceEnvRef.builder().serviceRef("service1").environmentRef("env1").build())
            .serviceEnvRef(ServiceEnvRef.builder().serviceRef("service2").environmentRef("env2").build())
            .build());
    MonitoredServiceResponse monitoredServiceResponse =
        MonitoredServiceResponse.builder().monitoredService(monitoredServiceDTO).build();
    when(cdStageMetaDataService.getServiceAndEnvironmentRef(any(), any())).thenReturn(responseDTO);
    when(mockedMonitoredServiceService.getApplicationMonitoredServiceResponse(any()))
        .thenReturn(monitoredServiceResponse);
    when(mockedSloHealthIndicatorService.getByMonitoredServiceIdentifiers(any(), any()))
        .thenReturn(sloHealthIndicatorList);
    Map<String, SloHealthIndicatorDTO> sloHealthIndicatorDTOMap = new HashMap<>();
    sloHealthIndicatorDTOMap.put("sloIdentifier",
        SloHealthIndicatorDTO.builder()
            .errorBudgetBurnRate(0.0)
            .errorBudgetRisk(ErrorBudgetRisk.HEALTHY)
            .errorBudgetRemainingMinutes(8640)
            .errorBudgetRemainingPercentage(100.0)
            .monitoredServiceIdentifier("service_env")
            .serviceLevelObjectiveIdentifier("sloIdentifier")
            .build());
    SLOPolicyDTO sloPolicyDTO = SLOPolicyDTO.builder()
                                    .statusOfMonitoredService(MonitoredServiceStatus.CONFIGURED)
                                    .sloErrorBudgetRemainingPercentage(100D)
                                    .slos(sloHealthIndicatorDTOMap)
                                    .build();
    ExpansionResponse expansionResponse = sloPolicyExpansionHandler.expand(jsonNode, metadataProject, null);
    verify(mockedMonitoredServiceService, times(2)).getApplicationMonitoredServiceResponse(any());
    assertThat(expansionResponse.isSuccess()).isTrue();
    assertThat(expansionResponse.getKey()).isEqualTo(SLO_POLICY);
    assertThat(expansionResponse.getValue().toJson()).isEqualTo(JsonUtils.asJson(sloPolicyDTO));
    assertThat(expansionResponse.getPlacement()).isEqualTo(ExpansionPlacementStrategy.APPEND);
  }

  private ExpansionRequestMetadata getExpansionRequestMetaData() {
    return ExpansionRequestMetadata.newBuilder()
        .setAccountId(builderFactory.getProjectParams().getAccountIdentifier())
        .setOrgId(builderFactory.getProjectParams().getOrgIdentifier())
        .setProjectId(builderFactory.getProjectParams().getProjectIdentifier())
        .build();
  }

  private JsonNode getJsonfromString(String file) throws IOException {
    final String yaml = getResource(file);
    return JsonUtils.asObject(yaml, JsonNode.class);
  }
}
