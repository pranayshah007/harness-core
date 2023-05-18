/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.beans.Workflow;
import software.wings.beans.stats.CloneMetadata;
import software.wings.exception.WingsExceptionMapper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.WorkflowService;
import software.wings.utils.ResourceTestRule;

import com.google.common.collect.Lists;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

/**
 * Created by rishi on 12/28/16.
 */
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class WorkflowResourceTest extends WingsBaseTest {
  private static final WorkflowService WORKFLOW_SERVICE = mock(WorkflowService.class);
  private static final AuthService AUTH_SERVICE = mock(AuthService.class);
  private static final AppService APP_SERVICE = mock(AppService.class);

  private static final FeatureFlagService FEATURE_FLAG_SERVICE = mock(FeatureFlagService.class);
  @Captor private ArgumentCaptor<PageRequest<Workflow>> pageRequestArgumentCaptor;

  /**
   * The constant RESOURCES.
   */
  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new WorkflowResource(WORKFLOW_SERVICE, AUTH_SERVICE, APP_SERVICE, FEATURE_FLAG_SERVICE))
          .type(WingsExceptionMapper.class)
          .build();

  private static String APP_ID = "APP_ID";
  private static String WORKFLOW_ID = "WORKFLOW_ID";
  private static final Workflow WORKFLOW =
      aWorkflow().appId(APP_ID).uuid(WORKFLOW_ID).orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build();

  /**
   * Should create workflow.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCreateWorkflow() {
    Workflow workflow2 = aWorkflow()
                             .appId(APP_ID)
                             .uuid(generateUuid())
                             .orchestrationWorkflow(aCanaryOrchestrationWorkflow().build())
                             .build();
    when(WORKFLOW_SERVICE.createWorkflow(WORKFLOW)).thenReturn(workflow2);

    RestResponse<Workflow> restResponse =
        RESOURCES.client()
            .target(format("/workflows?appId=%s", APP_ID))
            .request()
            .post(entity(WORKFLOW, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Workflow>>() {});

    assertThat(restResponse).isNotNull().hasFieldOrPropertyWithValue("resource", workflow2);
    verify(WORKFLOW_SERVICE).createWorkflow(WORKFLOW);
  }

  /**
   * Should create workflow.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCloneWorkflow() {
    Workflow workflow2 = aWorkflow()
                             .appId(APP_ID)
                             .uuid(generateUuid())
                             .orchestrationWorkflow(aCanaryOrchestrationWorkflow().build())
                             .build();
    CloneMetadata cloneMetadata = CloneMetadata.builder().workflow(WORKFLOW).build();
    when(WORKFLOW_SERVICE.cloneWorkflow(APP_ID, workflow2, cloneMetadata)).thenReturn(workflow2);
    when(WORKFLOW_SERVICE.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow2);
    RestResponse<Workflow> restResponse =
        RESOURCES.client()
            .target(format("/workflows/%s/clone?appId=%s", WORKFLOW_ID, APP_ID))
            .request()
            .post(entity(cloneMetadata, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Workflow>>() {});

    assertThat(restResponse).isNotNull().hasFieldOrPropertyWithValue("resource", workflow2);
    verify(WORKFLOW_SERVICE).cloneWorkflow(APP_ID, workflow2, cloneMetadata);
  }

  /**
   * Should list workflows.
   */
  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void shouldListWorkflow() {
    PageRequest<Workflow> pageRequest = aPageRequest().build();
    PageResponse<Workflow> pageResponse = aPageResponse().withResponse(Lists.newArrayList(WORKFLOW)).build();
    when(FEATURE_FLAG_SERVICE.isEnabled(any(), anyString())).thenReturn(false);
    when(WORKFLOW_SERVICE.listWorkflows(any(PageRequest.class), any(), anyBoolean(), any())).thenReturn(pageResponse);

    RestResponse<PageResponse<Workflow>> restResponse =
        RESOURCES.client()
            .target(format("/workflows?appId=%s&previousExecutionsCount=2", APP_ID))
            .request()
            .get(new GenericType<RestResponse<PageResponse<Workflow>>>() {});

    log.info(JsonUtils.asJson(restResponse));
    verify(WORKFLOW_SERVICE).listWorkflows(pageRequestArgumentCaptor.capture(), eq(2), eq(false), eq(null));
    assertThat(pageRequestArgumentCaptor.getValue()).isNotNull();
    assertThat(restResponse).isNotNull().hasFieldOrPropertyWithValue("resource", pageResponse);
  }

  /**
   * Should read workflow.
   */
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldReadWorkflow() {
    when(WORKFLOW_SERVICE.readWorkflow(APP_ID, WORKFLOW_ID, null)).thenReturn(WORKFLOW);

    RestResponse<Workflow> restResponse = RESOURCES.client()
                                              .target(format("/workflows/%s?appId=%s", WORKFLOW_ID, APP_ID))
                                              .request()
                                              .get(new GenericType<RestResponse<Workflow>>() {});

    assertThat(restResponse).isNotNull().hasFieldOrPropertyWithValue("resource", WORKFLOW);
    verify(WORKFLOW_SERVICE).readWorkflow(APP_ID, WORKFLOW_ID, null);
  }
}
