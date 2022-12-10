/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.cdng.resources.CVNGStepResource;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CVNGStepResourceTest extends CvNextGenTestBase {
  @Inject private Injector injector;
  @Inject private CVNGStepTaskService cvngStepTaskService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private CVNGLogService cvngLogService;
  private static CVNGStepResource cvngStepResource = new CVNGStepResource();
  private BuilderFactory builderFactory;

  String callbackId;
  String verificationJobInstanceId;
  String cvConfigId;

  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder().addResource(cvngStepResource).build();

  @Before
  public void setup() {
    injector.injectMembers(cvngStepResource);
    builderFactory = BuilderFactory.getDefault();
    callbackId = generateUuid();
    verificationJobInstanceId = generateUuid();
    cvConfigId = generateUuid();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogs() {
    cvngStepTaskService.create(builderFactory.cvngStepTaskBuilder()
                                   .callbackId(callbackId)
                                   .verificationJobInstanceId(verificationJobInstanceId)
                                   .build());
    verificationTaskService.createDeploymentVerificationTask(
        builderFactory.getContext().getAccountId(), cvConfigId, verificationJobInstanceId, DataSourceType.APP_DYNAMICS);
    Set<String> verificationTaskIds = verificationTaskService.maybeGetVerificationTaskIds(
        builderFactory.getContext().getAccountId(), verificationJobInstanceId);

    List<CVNGLogDTO> cvngLogDTOs =
        IntStream.range(0, 3)
            .mapToObj(index
                -> builderFactory.executionLogDTOBuilder().traceableId(verificationTaskIds.iterator().next()).build())
            .collect(Collectors.toList());
    cvngLogService.save(cvngLogDTOs);

    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/verify-step/" + callbackId + "/logs")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("logType", "ExecutionLog")
                              .queryParam("pageNumber", 0)
                              .queryParam("pageSize", 10);

    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class)).contains("\"totalItems\":3");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogs_withNoVerifyStep() {
    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/verify-step/"
                                  + "callbackId"
                                  + "/logs")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("logType", "ExecutionLog")
                              .queryParam("pageNumber", 0)
                              .queryParam("pageSize", 10);

    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.readEntity(String.class)).contains("java.lang.NullPointerException");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLogs_withMissingLogType() {
    cvngStepTaskService.create(builderFactory.cvngStepTaskBuilder()
                                   .callbackId(callbackId)
                                   .verificationJobInstanceId(verificationJobInstanceId)
                                   .build());

    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/verify-step/" + callbackId + "/logs")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("pageNumber", 0)
                              .queryParam("pageSize", 10);

    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class)).contains("\"totalItems\":0");
  }
}
