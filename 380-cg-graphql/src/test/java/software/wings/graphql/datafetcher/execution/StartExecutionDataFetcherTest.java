/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.execution.input.QLExecutionType;
import software.wings.graphql.schema.mutation.execution.input.QLStartExecutionInput;
import software.wings.graphql.schema.mutation.execution.payload.QLStartExecutionPayload;
import software.wings.graphql.schema.type.QLPipelineExecution;
import software.wings.graphql.schema.type.QLWorkflowExecution;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class StartExecutionDataFetcherTest extends CategoryTest {
  @InjectMocks @Inject private StartExecutionDataFetcher startExecutionDataFetcher;
  @Mock private WorkflowExecutionController workflowExecutionController;
  @Mock private PipelineExecutionController pipelineExecutionController;
  @Mock private DeploymentAuthHandler deploymentAuthHandler;
  @Mock private AppService appService;
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(appService.getAccountIdByAppId("appId")).thenReturn("accountId");
    //        configureAppService();
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void startPipelineExecution() {
    final MutationContext mutationContext = getMutationContext();
    QLStartExecutionInput input = QLStartExecutionInput.builder()
                                      .applicationId("appId")
                                      .clientMutationId("clientMutationId")
                                      .entityId("pipelineId")
                                      .executionType(QLExecutionType.PIPELINE)
                                      .build();

    doNothing().when(deploymentAuthHandler).authorizeWorkflowExecution(anyString(), anyString());

    when(pipelineExecutionController.startPipelineExecution(any(), any()))
        .thenReturn(QLStartExecutionPayload.builder()
                        .clientMutationId("clientMutationId")
                        .execution(QLPipelineExecution.builder().build())
                        .build());
    QLStartExecutionPayload paylaod = startExecutionDataFetcher.mutateAndFetch(input, mutationContext);
    assertThat(paylaod.getClientMutationId()).isEqualTo("clientMutationId");
    assertThat(paylaod.getExecution()).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testMutateAndFetchAuth() {
    final MutationContext mutationContext = getMutationContext();
    QLStartExecutionInput input = QLStartExecutionInput.builder()
                                      .applicationId("appId")
                                      .clientMutationId("clientMutationId")
                                      .entityId("pipelineId")
                                      .executionType(QLExecutionType.PIPELINE)
                                      .build();

    doNothing().when(deploymentAuthHandler).authorizeWorkflowExecution(anyString(), anyString());

    when(pipelineExecutionController.startPipelineExecution(any(), any()))
        .thenReturn(QLStartExecutionPayload.builder()
                        .clientMutationId("clientMutationId")
                        .execution(QLPipelineExecution.builder().build())
                        .build());
    QLStartExecutionPayload paylaod = startExecutionDataFetcher.mutateAndFetch(input, mutationContext);
    assertThat(paylaod.getClientMutationId()).isEqualTo("clientMutationId");
    assertThat(paylaod.getExecution()).isNotNull();
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void startWorkflowExecution() {
    final MutationContext mutationContext = getMutationContext();
    QLStartExecutionInput input = QLStartExecutionInput.builder()
                                      .applicationId("appId")
                                      .clientMutationId("clientMutationId")
                                      .entityId("workflowId")
                                      .executionType(QLExecutionType.WORKFLOW)
                                      .build();

    doNothing().when(deploymentAuthHandler).authorizeWorkflowExecution(anyString(), anyString());

    when(workflowExecutionController.startWorkflowExecution(any(), any()))
        .thenReturn(QLStartExecutionPayload.builder()
                        .clientMutationId("clientMutationId")
                        .execution(QLWorkflowExecution.builder().build())
                        .build());
    QLStartExecutionPayload paylaod = startExecutionDataFetcher.mutateAndFetch(input, mutationContext);
    assertThat(paylaod.getClientMutationId()).isEqualTo("clientMutationId");
    assertThat(paylaod.getExecution()).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testWorkflowAuthMutateAndFetch() {
    final MutationContext mutationContext = getMutationContext();
    QLStartExecutionInput input = QLStartExecutionInput.builder()
                                      .applicationId("appId")
                                      .clientMutationId("clientMutationId")
                                      .entityId("workflowId")
                                      .executionType(QLExecutionType.WORKFLOW)
                                      .build();

    doNothing().when(deploymentAuthHandler).authorizeWorkflowExecution(anyString(), anyString());

    when(workflowExecutionController.startWorkflowExecution(any(), any()))
        .thenReturn(QLStartExecutionPayload.builder()
                        .clientMutationId("clientMutationId")
                        .execution(QLWorkflowExecution.builder().build())
                        .build());
    QLStartExecutionPayload paylaod = startExecutionDataFetcher.mutateAndFetch(input, mutationContext);
    assertThat(paylaod.getClientMutationId()).isEqualTo("clientMutationId");
    assertThat(paylaod.getExecution()).isNotNull();
  }

  private MutationContext getMutationContext() {
    return MutationContext.builder()
        .accountId("accountId")
        .dataFetchingEnvironment(Mockito.mock(DataFetchingEnvironment.class))
        .build();
  }
}
