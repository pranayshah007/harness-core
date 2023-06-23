/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageResponse;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.utils.ResourceTestRule;

import javax.ws.rs.core.GenericType;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ExecutionResourceTest extends CategoryTest {
  private static final AppService appService = mock(AppService.class);
  private static final AuthService authService = mock(AuthService.class);
  private static final WorkflowExecutionService workflowExecutionService = mock(WorkflowExecutionService.class);

  /**
   * The constant resources.
   */
  @ClassRule public static final ResourceTestRule resources = init(workflowExecutionService);

  private static ResourceTestRule init(WorkflowExecutionService workflowExecutionService) {
    try {
      ExecutionResource executionResource = new ExecutionResource();
      FieldUtils.writeField(executionResource, "authService", authService, true);
      FieldUtils.writeField(executionResource, "workflowExecutionService", workflowExecutionService, true);
      return ResourceTestRule.builder().instance(executionResource).build();
    } catch (IllegalAccessException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Tear down.
   */
  @After
  public void tearDown() {
    // we have to reset the mock after each test because of the
    // @ClassRule, or use a @Rule as mentioned below.
    reset(appService, workflowExecutionService);
  }

  /**
   * Test find by name.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void testListExecutions() {
    String appId = generateUuid();

    PageResponse<Application> applicationPageResponse =
        aPageResponse().withResponse(newArrayList(anApplication().uuid(appId).build())).build();
    when(appService.list(any())).thenReturn(applicationPageResponse);

    PageResponse<WorkflowExecution> workflowExecutionPageResponse = aPageResponse().build();
    when(workflowExecutionService.listExecutions(any(), eq(true), eq(true), eq(true), eq(true), eq(true), eq(true)))
        .thenReturn(workflowExecutionPageResponse);

    RestResponse<PageResponse<WorkflowExecution>> actual =
        resources.client()
            .target("/executions")
            .request()
            .get(new GenericType<RestResponse<PageResponse<WorkflowExecution>>>() {});
    assertThat(actual.getResource()).isEqualTo(workflowExecutionPageResponse);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  @Ignore("Platform Team to fix later")
  public void testGetExecution() {
    String appId = generateUuid();

    PageResponse<Application> applicationPageResponse =
        aPageResponse().withResponse(newArrayList(anApplication().uuid(appId).build())).build();
    when(appService.list(any())).thenReturn(applicationPageResponse);

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .appId(APP_ID)
                                              .uuid(WORKFLOW_EXECUTION_ID)
                                              .workflowId(WORKFLOW_ID)
                                              .workflowType(WorkflowType.ORCHESTRATION)
                                              .build();
    doNothing().when(authService).authorizeAppAccess(anyString(), anyString(), any(User.class), any());
    when(workflowExecutionService.getExecutionDetails(any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn(workflowExecution);

    RestResponse<WorkflowExecution> actual = resources.client()
                                                 .target("/executions/" + WORKFLOW_EXECUTION_ID)
                                                 .request()
                                                 .get(new GenericType<RestResponse<WorkflowExecution>>() {});
    assertThat(actual.getResource()).isEqualTo(workflowExecution);
  }
}
