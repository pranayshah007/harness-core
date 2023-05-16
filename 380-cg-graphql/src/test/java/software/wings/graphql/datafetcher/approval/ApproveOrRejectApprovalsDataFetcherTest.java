/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.approval;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.RAFAEL;

import static software.wings.sm.states.ApprovalState.ApprovalStateType.JIRA;
import static software.wings.sm.states.ApprovalState.ApprovalStateType.SERVICENOW;
import static software.wings.sm.states.ApprovalState.ApprovalStateType.SHELL_SCRIPT;
import static software.wings.sm.states.ApprovalState.ApprovalStateType.USER_GROUP;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.GraphQLException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.ApprovalDetails;
import software.wings.beans.NameValuePair;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.approval.input.QLApproveOrRejectApprovalsInput;
import software.wings.service.impl.AuthServiceImpl;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WorkflowExecutionService;

import dev.morphia.query.Query;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDC)
public class ApproveOrRejectApprovalsDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private GraphQLContext graphQLContext;
  @Mock private DataFetchingEnvironment dataFetchingEnvironment;
  @Mock private ApiKeyEntry apiKeyEntry;

  @Mock private FeatureFlagService featureFlagService;

  @Mock protected WingsPersistence persistence;
  @Mock private Query<WorkflowExecution> workflowExecutionQuery;

  @Mock private AuthServiceImpl authService;

  @Mock private UserService userService;
  @InjectMocks @Spy ApproveOrRejectApprovalsDataFetcher approveOrRejectApprovalsDataFetcher;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testMutateAndFetchForJiraApprovalType() throws Exception {
    QLApproveOrRejectApprovalsInput approveOrRejectApprovalsInput = new QLApproveOrRejectApprovalsInput("executionId",
        "approvalId", ApprovalDetails.Action.APPROVE, null, "applicationId", "comment", "clientMutationId", null);
    ApprovalDetails approvalDetails = new ApprovalDetails();
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalId("approvalId")
                                                   .approvedBy(new EmbeddedUser())
                                                   .comments("comments")
                                                   .approvalFromSlack(false)
                                                   .approvalStateType(JIRA)
                                                   .build();

    MutationContext mutationContext =
        MutationContext.builder().accountId("accountId").dataFetchingEnvironment(dataFetchingEnvironment).build();

    when(workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
             "applicationId", "executionId", null, approvalDetails))
        .thenReturn(executionData);
    when(dataFetchingEnvironment.getContext()).thenReturn(graphQLContext);
    when(graphQLContext.get("apiKeyEntry")).thenReturn(apiKeyEntry);

    doThrow(new GraphQLException(executionData.getApprovalStateType() + " Approval Type not supported", USER))
        .when(approveOrRejectApprovalsDataFetcher)
        .mutateAndFetch(approveOrRejectApprovalsInput, mutationContext);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testMutateAndFetchForServiceNowApprovalType() throws Exception {
    QLApproveOrRejectApprovalsInput approveOrRejectApprovalsInput = new QLApproveOrRejectApprovalsInput("executionId",
        "approvalId", ApprovalDetails.Action.APPROVE, null, "applicationId", "comment", "clientMutationId", null);
    ApprovalDetails approvalDetails = new ApprovalDetails();
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalId("approvalId")
                                                   .approvedBy(new EmbeddedUser())
                                                   .comments("comments")
                                                   .approvalFromSlack(false)
                                                   .approvalStateType(SERVICENOW)
                                                   .build();

    MutationContext mutationContext =
        MutationContext.builder().accountId("accountId").dataFetchingEnvironment(dataFetchingEnvironment).build();

    when(workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
             "applicationId", "executionId", null, approvalDetails))
        .thenReturn(executionData);
    when(dataFetchingEnvironment.getContext()).thenReturn(graphQLContext);
    when(graphQLContext.get("apiKeyEntry")).thenReturn(apiKeyEntry);

    doThrow(new GraphQLException(executionData.getApprovalStateType() + " Approval Type not supported", USER))
        .when(approveOrRejectApprovalsDataFetcher)
        .mutateAndFetch(approveOrRejectApprovalsInput, mutationContext);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testMutateAndFetchForShellApprovalType() throws Exception {
    QLApproveOrRejectApprovalsInput approveOrRejectApprovalsInput = new QLApproveOrRejectApprovalsInput("executionId",
        "approvalId", ApprovalDetails.Action.APPROVE, null, "applicationId", "comment", "clientMutationId", null);
    ApprovalDetails approvalDetails = new ApprovalDetails();
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalId("approvalId")
                                                   .approvedBy(new EmbeddedUser())
                                                   .comments("comments")
                                                   .approvalFromSlack(false)
                                                   .approvalStateType(SHELL_SCRIPT)
                                                   .build();

    MutationContext mutationContext =
        MutationContext.builder().accountId("accountId").dataFetchingEnvironment(dataFetchingEnvironment).build();

    when(workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
             "applicationId", "executionId", null, approvalDetails))
        .thenReturn(executionData);
    when(dataFetchingEnvironment.getContext()).thenReturn(graphQLContext);
    when(graphQLContext.get("apiKeyEntry")).thenReturn(apiKeyEntry);

    doThrow(new GraphQLException(executionData.getApprovalStateType() + " Approval Type not supported", USER))
        .when(approveOrRejectApprovalsDataFetcher)
        .mutateAndFetch(approveOrRejectApprovalsInput, mutationContext);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testMutateAndFetchForInvalidExecutionId() throws Exception {
    MutationContext mutationContext =
        MutationContext.builder().accountId("accountId").dataFetchingEnvironment(dataFetchingEnvironment).build();

    QLApproveOrRejectApprovalsInput approveOrRejectApprovalsInput = new QLApproveOrRejectApprovalsInput("executionId",
        "approvalId", ApprovalDetails.Action.APPROVE, null, "applicationId", "comment", "clientMutationId", null);

    when(persistence.createAuthorizedQuery(WorkflowExecution.class)).thenReturn(workflowExecutionQuery);

    when(workflowExecutionQuery.filter("_id", approveOrRejectApprovalsInput.getExecutionId()))
        .thenReturn(workflowExecutionQuery);

    when(workflowExecutionQuery.get()).thenReturn(null);

    doThrow(new InvalidRequestException("Execution does not exist or access is denied", WingsException.USER))
        .when(approveOrRejectApprovalsDataFetcher)
        .mutateAndFetch(approveOrRejectApprovalsInput, mutationContext);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testMutateAndFetchForInvalidVariableName() throws Exception {
    List<NameValuePair> variableInputs = new LinkedList<>();
    variableInputs.add(new NameValuePair("name", null, null));

    List<NameValuePair> approvalVariableInputs = new LinkedList<>();
    approvalVariableInputs.add(new NameValuePair("approvalName", null, null));

    QLApproveOrRejectApprovalsInput approveOrRejectApprovalsInput =
        new QLApproveOrRejectApprovalsInput("executionId", "approvalId", ApprovalDetails.Action.APPROVE, variableInputs,
            "applicationId", "comment", "clientMutationId", null);
    ApprovalDetails approvalDetails = new ApprovalDetails();
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalId("approvalId")
                                                   .approvedBy(new EmbeddedUser())
                                                   .comments("comments")
                                                   .approvalFromSlack(false)
                                                   .approvalStateType(USER_GROUP)
                                                   .variables(approvalVariableInputs)
                                                   .build();

    MutationContext mutationContext =
        MutationContext.builder().accountId("accountId").dataFetchingEnvironment(dataFetchingEnvironment).build();

    when(workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
             "applicationId", "executionId", null, approvalDetails))
        .thenReturn(executionData);
    when(dataFetchingEnvironment.getContext()).thenReturn(graphQLContext);
    when(graphQLContext.get("apiKeyEntry")).thenReturn(apiKeyEntry);

    doThrow(new InvalidRequestException("Variable with name \"" + variableInputs.get(0).getName() + "\" not present"))
        .when(approveOrRejectApprovalsDataFetcher)
        .mutateAndFetch(approveOrRejectApprovalsInput, mutationContext);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testMutateAndFetchWhenApprovalVariableNotPresentButPassedInInput() throws Exception {
    List<NameValuePair> variableInputs = new LinkedList<>();
    variableInputs.add(new NameValuePair("name", null, null));

    List<NameValuePair> approvalVariableInputs = new LinkedList<>();
    approvalVariableInputs.add(new NameValuePair("approvalName", null, null));

    QLApproveOrRejectApprovalsInput approveOrRejectApprovalsInput =
        new QLApproveOrRejectApprovalsInput("executionId", "approvalId", ApprovalDetails.Action.APPROVE, variableInputs,
            "applicationId", "comment", "clientMutationId", null);
    ApprovalDetails approvalDetails = new ApprovalDetails();
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalId("approvalId")
                                                   .approvedBy(new EmbeddedUser())
                                                   .comments("comments")
                                                   .approvalFromSlack(false)
                                                   .approvalStateType(USER_GROUP)
                                                   .variables(null)
                                                   .build();

    MutationContext mutationContext =
        MutationContext.builder().accountId("accountId").dataFetchingEnvironment(dataFetchingEnvironment).build();

    when(workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
             "applicationId", "executionId", null, approvalDetails))
        .thenReturn(executionData);
    when(dataFetchingEnvironment.getContext()).thenReturn(graphQLContext);
    when(graphQLContext.get("apiKeyEntry")).thenReturn(apiKeyEntry);

    doThrow(new InvalidRequestException("Variable were not present for the given approval"))
        .when(approveOrRejectApprovalsDataFetcher)
        .mutateAndFetch(approveOrRejectApprovalsInput, mutationContext);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldReturnTrueWhenTheEmailIsValid() throws Exception {
    String email = "test@harness.io";
    String accountId = "xpto";
    UserGroup ug = UserGroup.builder().uuid("ug1").build();
    List<UserGroup> mockUserGroupList = List.of(ug);
    List<String> mockStringUserGroupList = List.of("ug1");
    User user = User.Builder.anUser()
                    .uuid("userid")
                    .name("test")
                    .userGroups(mockUserGroupList)
                    .email(email)
                    .emailVerified(true)
                    .build();
    ApiKeyEntry mockApiKeyEntry = ApiKeyEntry.builder()
                                      .accountId(accountId)
                                      .uuid("uuid")
                                      .userGroups(mockUserGroupList)
                                      .userGroupIds(mockStringUserGroupList)
                                      .build();
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalId("approvalId")
                                                   .approvedBy(new EmbeddedUser())
                                                   .userGroups(mockStringUserGroupList)
                                                   .comments("comments")
                                                   .approvalFromSlack(false)
                                                   .approvalStateType(USER_GROUP)
                                                   .variables(null)
                                                   .build();
    when(featureFlagService.isEnabled(FeatureName.SPG_ENABLE_EMAIL_VALIDATION, accountId)).thenReturn(true);
    when(userService.getUserByEmail(email)).thenReturn(user);
    when(authService.getUserGroups(accountId, user)).thenReturn(mockUserGroupList);
    assertThatCode(()
                       -> approveOrRejectApprovalsDataFetcher.validateEmailWithApprovalUserGroups(
                           email, accountId, executionData.getUserGroups()))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldThrowWhenTheEmailIsEmpty() throws Exception {
    String email = "";
    String accountId = "xpto";
    UserGroup ug = UserGroup.builder().uuid("ug1").build();
    List<UserGroup> mockUserGroupList = List.of(ug);
    List<String> mockStringUserGroupList = List.of("ug1");
    ApiKeyEntry mockApiKeyEntry = ApiKeyEntry.builder()
                                      .accountId(accountId)
                                      .uuid("uuid")
                                      .userGroups(mockUserGroupList)
                                      .userGroupIds(mockStringUserGroupList)
                                      .build();
    User user = User.Builder.anUser()
                    .uuid("userid")
                    .name("test")
                    .userGroups(mockUserGroupList)
                    .email(email)
                    .emailVerified(true)
                    .build();
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalId("approvalId")
                                                   .approvedBy(new EmbeddedUser())
                                                   .userGroups(mockStringUserGroupList)
                                                   .comments("comments")
                                                   .approvalFromSlack(false)
                                                   .approvalStateType(USER_GROUP)
                                                   .variables(null)
                                                   .build();
    when(featureFlagService.isEnabled(FeatureName.SPG_ENABLE_EMAIL_VALIDATION, accountId)).thenReturn(true);
    when(userService.getUserByEmail(email)).thenReturn(null);
    when(authService.getUserGroups(accountId, user)).thenReturn(mockUserGroupList);
    assertThatThrownBy(()
                           -> approveOrRejectApprovalsDataFetcher.validateEmailWithApprovalUserGroups(
                               email, accountId, executionData.getUserGroups()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testMutateAndFetchForJiraApprovalTypeWithUserEmail() throws Exception {
    String email = "test@harness.io";
    String accountId = "xpto";
    UserGroup ug = UserGroup.builder().uuid("ug1").build();
    List<UserGroup> mockUserGroupList = List.of(ug);
    List<String> mockStringUserGroupList = List.of("ug1");
    User user = User.Builder.anUser()
                    .uuid("userid")
                    .name("test")
                    .userGroups(mockUserGroupList)
                    .email(email)
                    .emailVerified(true)
                    .build();
    QLApproveOrRejectApprovalsInput approveOrRejectApprovalsInput = new QLApproveOrRejectApprovalsInput("executionId",
        "approvalId", ApprovalDetails.Action.APPROVE, null, "applicationId", "comment", "clientMutationId", email);
    ApprovalDetails approvalDetails = new ApprovalDetails();
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalId("approvalId")
                                                   .approvedBy(new EmbeddedUser())
                                                   .userGroups(mockStringUserGroupList)
                                                   .comments("comments")
                                                   .approvalFromSlack(false)
                                                   .approvalStateType(JIRA)
                                                   .build();

    MutationContext mutationContext =
        MutationContext.builder().accountId(accountId).dataFetchingEnvironment(dataFetchingEnvironment).build();

    when(workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
             "applicationId", "executionId", null, approvalDetails))
        .thenReturn(executionData);
    when(dataFetchingEnvironment.getContext()).thenReturn(graphQLContext);
    when(graphQLContext.get("apiKeyEntry")).thenReturn(apiKeyEntry);
    when(userService.getUserByEmail(email)).thenReturn(user);
    when(authService.getUserGroups(accountId, user)).thenReturn(mockUserGroupList);
    when(featureFlagService.isEnabled(eq(FeatureName.SPG_ENABLE_EMAIL_VALIDATION), anyString())).thenReturn(true);

    doThrow(new GraphQLException(executionData.getApprovalStateType() + " Approval Type not supported", USER))
        .when(approveOrRejectApprovalsDataFetcher)
        .mutateAndFetch(approveOrRejectApprovalsInput, mutationContext);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testMutateAndFetchForServiceNowApprovalTypeWithUserEmail() throws Exception {
    String email = "test@harness.io";
    String accountId = "xpto";
    UserGroup ug = UserGroup.builder().uuid("ug1").build();
    List<UserGroup> mockUserGroupList = List.of(ug);
    List<String> mockStringUserGroupList = List.of("ug1");
    User user = User.Builder.anUser()
                    .uuid("userid")
                    .name("test")
                    .userGroups(mockUserGroupList)
                    .email(email)
                    .emailVerified(true)
                    .build();
    QLApproveOrRejectApprovalsInput approveOrRejectApprovalsInput =
        new QLApproveOrRejectApprovalsInput("executionId", "approvalId", ApprovalDetails.Action.APPROVE, null,
            "applicationId", "comment", "clientMutationId", "admin@harness.io");
    ApprovalDetails approvalDetails = new ApprovalDetails();
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalId("approvalId")
                                                   .approvedBy(new EmbeddedUser())
                                                   .userGroups(mockStringUserGroupList)
                                                   .comments("comments")
                                                   .approvalFromSlack(false)
                                                   .approvalStateType(SERVICENOW)
                                                   .build();

    MutationContext mutationContext =
        MutationContext.builder().accountId("accountId").dataFetchingEnvironment(dataFetchingEnvironment).build();

    when(workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
             "applicationId", "executionId", null, approvalDetails))
        .thenReturn(executionData);
    when(dataFetchingEnvironment.getContext()).thenReturn(graphQLContext);
    when(graphQLContext.get("apiKeyEntry")).thenReturn(apiKeyEntry);
    when(userService.getUserByEmail(email)).thenReturn(user);
    when(authService.getUserGroups(accountId, user)).thenReturn(mockUserGroupList);
    when(featureFlagService.isEnabled(eq(FeatureName.SPG_ENABLE_EMAIL_VALIDATION), anyString())).thenReturn(true);

    doThrow(new GraphQLException(executionData.getApprovalStateType() + " Approval Type not supported", USER))
        .when(approveOrRejectApprovalsDataFetcher)
        .mutateAndFetch(approveOrRejectApprovalsInput, mutationContext);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testMutateAndFetchForShellApprovalTypeWithUserEmail() throws Exception {
    String email = "test@harness.io";
    String accountId = "xpto";
    UserGroup ug = UserGroup.builder().uuid("ug1").build();
    List<UserGroup> mockUserGroupList = List.of(ug);
    List<String> mockStringUserGroupList = List.of("ug1");
    User user = User.Builder.anUser()
                    .uuid("userid")
                    .name("test")
                    .userGroups(mockUserGroupList)
                    .email(email)
                    .emailVerified(true)
                    .build();
    QLApproveOrRejectApprovalsInput approveOrRejectApprovalsInput = new QLApproveOrRejectApprovalsInput("executionId",
        "approvalId", ApprovalDetails.Action.APPROVE, null, "applicationId", "comment", "clientMutationId", null);
    ApprovalDetails approvalDetails = new ApprovalDetails();
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalId("approvalId")
                                                   .approvedBy(new EmbeddedUser())
                                                   .userGroups(mockStringUserGroupList)
                                                   .comments("comments")
                                                   .approvalFromSlack(false)
                                                   .approvalStateType(SHELL_SCRIPT)
                                                   .build();

    MutationContext mutationContext =
        MutationContext.builder().accountId("accountId").dataFetchingEnvironment(dataFetchingEnvironment).build();

    when(workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
             "applicationId", "executionId", null, approvalDetails))
        .thenReturn(executionData);
    when(dataFetchingEnvironment.getContext()).thenReturn(graphQLContext);
    when(graphQLContext.get("apiKeyEntry")).thenReturn(apiKeyEntry);
    when(userService.getUserByEmail(email)).thenReturn(user);
    when(authService.getUserGroups(accountId, user)).thenReturn(mockUserGroupList);
    when(featureFlagService.isEnabled(eq(FeatureName.SPG_ENABLE_EMAIL_VALIDATION), anyString())).thenReturn(true);

    doThrow(new GraphQLException(executionData.getApprovalStateType() + " Approval Type not supported", USER))
        .when(approveOrRejectApprovalsDataFetcher)
        .mutateAndFetch(approveOrRejectApprovalsInput, mutationContext);
  }
}
