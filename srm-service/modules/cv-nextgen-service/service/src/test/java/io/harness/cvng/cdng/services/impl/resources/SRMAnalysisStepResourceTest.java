/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl.resources;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.entities.SRMStepAnalysisActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.entities.SRMAnalysisStepDetailDTO;
import io.harness.cvng.beans.change.SRMAnalysisStatus;
import io.harness.cvng.cdng.resources.SRMAnalysisStepResource;
import io.harness.cvng.cdng.services.api.SRMAnalysisStepService;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import retrofit2.Call;

public class SRMAnalysisStepResourceTest extends CvNextGenTestBase {
  @Inject private Injector injector;

  @Inject SRMAnalysisStepService srmAnalysisStepService;

  @Inject ActivityService activityService;

  @Mock PipelineServiceClient pipelineServiceClient;
  private BuilderFactory builderFactory;

  private String monitoredServiceIdentifier;
  private ServiceEnvironmentParams serviceEnvironmentParams;

  private String analysisExecutionDetailsId;

  private String activityId;
  private String stepName;

  private static SRMAnalysisStepResource srmAnalysisStepResource = new SRMAnalysisStepResource();

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder().addResource(srmAnalysisStepResource).build();

  @Before
  public void setup() throws IOException, IllegalAccessException {
    injector.injectMembers(srmAnalysisStepResource);
    builderFactory = BuilderFactory.getDefault();
    monitoredServiceIdentifier = "service1_env1";
    stepName = "Mocked step name";
    serviceEnvironmentParams = ServiceEnvironmentParams.builderWithProjectParams(builderFactory.getProjectParams())
                                   .serviceIdentifier("service1")
                                   .environmentIdentifier("env1")
                                   .build();
    Call<ResponseDTO<Object>> pipelineSummaryCall = mock(Call.class);
    doReturn(pipelineSummaryCall).when(pipelineServiceClient).getExecutionDetailV2(any(), any(), any(), any());
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode pipelineExecutionSummary = objectMapper.createObjectNode();
    pipelineExecutionSummary.put("name", "Mocked Pipeline");
    ObjectNode mockResponse = objectMapper.createObjectNode();
    mockResponse.set("pipelineExecutionSummary", pipelineExecutionSummary);
    when(pipelineSummaryCall.execute()).thenReturn(retrofit2.Response.success(ResponseDTO.newResponse(mockResponse)));
    FieldUtils.writeField(srmAnalysisStepService, "pipelineServiceClient", pipelineServiceClient, true);
    analysisExecutionDetailsId = srmAnalysisStepService.createSRMAnalysisStepExecution(
        builderFactory.getAmbiance(builderFactory.getProjectParams()), monitoredServiceIdentifier, stepName,
        serviceEnvironmentParams, Duration.ofDays(1), Optional.empty());
    SRMStepAnalysisActivity stepAnalysisActivity = builderFactory.getSRMStepAnalysisActivityBuilder()
                                                       .executionNotificationDetailsId(analysisExecutionDetailsId)
                                                       .build();
    stepAnalysisActivity.setUuid(analysisExecutionDetailsId);
    activityId = activityService.createActivity(stepAnalysisActivity);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSummary_withNoDeploymentStep() {
    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/srm-analysis-step/"
                                  + "activityId"
                                  + "/analysis-summary")
                              .queryParam("accountId", builderFactory.getContext().getAccountId());

    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.readEntity(String.class)).contains("java.lang.NullPointerException");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSummary() {
    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/srm-analysis-step/" + activityId + "/analysis-summary")
                              .queryParam("accountId", builderFactory.getContext().getAccountId());

    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<SRMAnalysisStepDetailDTO> restResponse =
        response.readEntity(new GenericType<RestResponse<SRMAnalysisStepDetailDTO>>() {});
    SRMAnalysisStepDetailDTO stepDetailDTO = restResponse.getResource();
    assertThat(stepDetailDTO.getStepName()).isEqualTo(stepName);
    assertThat(stepDetailDTO.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(stepDetailDTO.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.RUNNING);
    assertThat(stepDetailDTO.getExecutionDetailIdentifier()).isEqualTo(analysisExecutionDetailsId);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testAbortStep() {
    WebTarget webTarget =
        RESOURCES.client()
            .target("http://localhost:9998/srm-analysis-step/" + analysisExecutionDetailsId + "/stop-analysis")
            .queryParam("accountId", builderFactory.getContext().getAccountId());

    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).put(Entity.text(""));
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<SRMAnalysisStepDetailDTO> restResponse =
        response.readEntity(new GenericType<RestResponse<SRMAnalysisStepDetailDTO>>() {});
    SRMAnalysisStepDetailDTO stepDetailDTO = restResponse.getResource();
    assertThat(stepDetailDTO.getStepName()).isEqualTo(stepName);
    assertThat(stepDetailDTO.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(stepDetailDTO.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.ABORTED);
    assertThat(stepDetailDTO.getExecutionDetailIdentifier()).isEqualTo(analysisExecutionDetailsId);
  }
}
