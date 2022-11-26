/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.resources;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.persistence.HPersistence;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import io.serializer.HObjectMapper;
import java.time.Clock;
import java.time.Duration;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class LogAnalysisResourceTest extends CvNextGenTestBase {
  private static LogAnalysisResource logAnalysisResource = new LogAnalysisResource();
  private BuilderFactory builderFactory;

  @Inject private LearningEngineTaskService learningEngineTaskService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private Injector injector;
  @Inject private HPersistence hPersistence;
  @Inject private Clock clock;

  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder().addResource(logAnalysisResource).build();

  private String verificationTaskId;
  private String learningEngineTaskId;

  @Before
  public void before() {
    builderFactory = BuilderFactory.getDefault();
    injector.injectMembers(logAnalysisResource);
    verificationTaskId = "verificationTaskId";
    learningEngineTaskId = "learningEngineTaskId";

    VerificationTask verificationTask = VerificationTask.builder()
                                            .accountId(builderFactory.getContext().getAccountId())
                                            .taskInfo(VerificationTask.DeploymentInfo.builder()
                                                          .cvConfigId("cvConfigId")
                                                          .verificationJobInstanceId("verificationJobInstanceId")
                                                          .build())
                                            .uuid(verificationTaskId)
                                            .build();
    hPersistence.save(verificationTask);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  @SneakyThrows
  public void testSaveDeploymentAnalysisAndGetPreviousDeploymentAnalysis() {
    LearningEngineTask canaryLogAnalysisLearningEngineTask = builderFactory.canaryLogAnalysisLearningEngineTaskBuilder()
                                                                 .uuid("")
                                                                 .verificationTaskId(verificationTaskId)
                                                                 .build();

    learningEngineTaskId = learningEngineTaskService.createLearningEngineTask(canaryLogAnalysisLearningEngineTask);

    String sampleDeploymentAnalysisDTOJson = getResource("analysis/sample-deployment-log-analysis-dto.json");
    Response saveResponse = RESOURCES.client()
                                .target("http://localhost:9998/log-analysis/deployment-save-analysis")
                                .queryParam("taskId", learningEngineTaskId)
                                .request(MediaType.APPLICATION_JSON_TYPE)
                                .post(Entity.json(sampleDeploymentAnalysisDTOJson));

    assertThat(saveResponse.getStatus()).isEqualTo(200);

    Response previousAnalysisResponse =
        RESOURCES.client()
            .target("http://localhost:9998/log-analysis/previous-analysis")
            .queryParam("verificationTaskId", verificationTaskId)
            .queryParam("analysisStartTime", clock.instant().plus(Duration.ofMinutes(10)).toEpochMilli())
            .queryParam("analysisEndTime", clock.instant().plus(Duration.ofMinutes(10)).toEpochMilli())
            .request(MediaType.APPLICATION_JSON_TYPE)
            .get();

    assertThat(previousAnalysisResponse.getStatus()).isEqualTo(200);
    DeploymentLogAnalysisDTO deploymentLogAnalysisDTO =
        previousAnalysisResponse.readEntity(new GenericType<RestResponse<DeploymentLogAnalysisDTO>>() {}).getResource();
    DeploymentLogAnalysisDTO expectedResponse =
        HObjectMapper.get().readValue(sampleDeploymentAnalysisDTOJson, DeploymentLogAnalysisDTO.class);
    assertThat(deploymentLogAnalysisDTO).isEqualTo(expectedResponse);
  }
}
