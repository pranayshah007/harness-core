/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.auth;

import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static software.wings.security.PermissionAttribute.Action.CREATE;
import static software.wings.security.PermissionAttribute.Action.DELETE;
import static software.wings.security.PermissionAttribute.Action.READ;
import static software.wings.security.PermissionAttribute.Action.UPDATE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.User;
import software.wings.beans.User.Builder;
import software.wings.beans.trigger.Trigger;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AuthService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class WorkflowAuthHandlerTest extends WingsBaseTest {
  @Mock private AuthService authService;
  @InjectMocks private WorkflowAuthHandler workflowAuthHandler;

  private static final String appId = generateUuid();
  private static final String workflowId = generateUuid();

  User user;

  @Before
  public void setUp() {
    UserRequestContext userRequestContext = UserRequestContext.builder().accountId(generateUuid()).build();
    user = Builder.anUser().uuid(generateUuid()).userRequestContext(userRequestContext).build();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflow() {
    UserThreadLocal.set(user);
    Trigger trigger = Trigger.builder().workflowType(ORCHESTRATION).workflowId(workflowId).build();

    doNothing().when(authService).authorize(anyString(), anyList(), eq(workflowId), any(), anyList());

    workflowAuthHandler.authorizeWorkflowAction(appId, trigger, DELETE);
    workflowAuthHandler.authorizeWorkflowAction(appId, trigger, UPDATE);
    workflowAuthHandler.authorizeWorkflowAction(appId, trigger, READ);
    workflowAuthHandler.authorizeWorkflowAction(appId, trigger, CREATE);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testAuthorizeDeleteWorkflowShouldFail() {
    try {
      UserThreadLocal.set(user);
      Trigger trigger = Trigger.builder().workflowType(ORCHESTRATION).workflowId(workflowId).build();

      doThrow(new AccessDeniedException("Not authorized to access the account", USER))
          .when(authService)
          .authorize(anyString(), anyList(), eq(workflowId), any(), anyList());

      workflowAuthHandler.authorizeWorkflowAction(appId, trigger, DELETE);

    } catch (Exception e) {
      assertThat(e).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testAuthorizeCreateWorkflowShouldFail() {
    try {
      UserThreadLocal.set(user);
      Trigger trigger = Trigger.builder().workflowType(ORCHESTRATION).workflowId(workflowId).build();

      doThrow(new AccessDeniedException("Not authorized to access the account", USER))
          .when(authService)
          .authorize(anyString(), anyList(), eq(workflowId), any(), anyList());

      workflowAuthHandler.authorizeWorkflowAction(appId, trigger, CREATE);

    } catch (Exception e) {
      assertThat(e).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testAuthorizeReadWorkflowShouldFail() {
    try {
      UserThreadLocal.set(user);
      Trigger trigger = Trigger.builder().workflowType(ORCHESTRATION).workflowId(workflowId).build();

      doThrow(new AccessDeniedException("Not authorized to access the account", USER))
          .when(authService)
          .authorize(anyString(), anyList(), eq(workflowId), any(), anyList());

      workflowAuthHandler.authorizeWorkflowAction(appId, trigger, READ);

    } catch (Exception e) {
      assertThat(e).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testAuthorizeUpdateWorkflowShouldFail() {
    try {
      UserThreadLocal.set(user);
      Trigger trigger = Trigger.builder().workflowType(ORCHESTRATION).workflowId(workflowId).build();

      doThrow(new AccessDeniedException("Not authorized to access the account", USER))
          .when(authService)
          .authorize(anyString(), anyList(), eq(workflowId), any(), anyList());

      workflowAuthHandler.authorizeWorkflowAction(appId, trigger, UPDATE);

    } catch (Exception e) {
      assertThat(e).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }
}
