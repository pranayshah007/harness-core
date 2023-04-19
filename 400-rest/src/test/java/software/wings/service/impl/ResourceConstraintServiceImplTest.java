/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PRANJAL;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.infra.InfraDefinitionTestConstants.RESOURCE_CONSTRAINT_NAME;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.beans.FeatureName;
import io.harness.beans.ResourceConstraint;
import io.harness.beans.ResourceConstraint.ResourceConstraintKeys;
import io.harness.category.element.UnitTests;
import io.harness.distribution.constraint.Constraint.Strategy;
import io.harness.distribution.constraint.Consumer.State;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.ResourceConstraintInstance;
import software.wings.beans.ResourceConstraintInstance.ResourceConstraintInstanceBuilder;
import software.wings.beans.ResourceConstraintInstance.ResourceConstraintInstanceKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ResourceConstraintService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.states.HoldingScope;

import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.Arrays;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ResourceConstraintServiceImplTest extends WingsBaseTest {
  private static final String RESOURCE_CONSTRAINT_ID = "RC_ID";
  private static final String WORKFLOW_ID_1 = "WF_ID_1";
  private static final String WORKFLOW_ID_2 = "WF_ID_2";

  @Mock private WingsPersistence wingsPersistence;
  @Mock private Query query;
  @Mock private FieldEnd fieldEnd;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private WorkflowExecutionService workflowExecutionService;

  @InjectMocks @Inject private ResourceConstraintService resourceConstraintService;

  private final ResourceConstraint resourceConstraint = ResourceConstraint.builder()
                                                            .name(RESOURCE_CONSTRAINT_NAME)
                                                            .accountId(ACCOUNT_ID)
                                                            .capacity(1)
                                                            .strategy(Strategy.FIFO)
                                                            .build();

  private final ResourceConstraintInstanceBuilder instanceBuilder = ResourceConstraintInstance.builder()
                                                                        .uuid(generateUuid())
                                                                        .appId(APP_ID)
                                                                        .resourceConstraintId(RESOURCE_CONSTRAINT_ID)
                                                                        .acquiredAt(System.currentTimeMillis())
                                                                        .permits(4);

  private ResourceConstraintInstance instance1 = instanceBuilder.releaseEntityId(WORKFLOW_ID_1)
                                                     .state(State.ACTIVE.name())
                                                     .resourceUnit(INFRA_MAPPING_ID)
                                                     .releaseEntityType(HoldingScope.WORKFLOW.name())
                                                     .build();

  private ResourceConstraintInstance instance2 = instanceBuilder.releaseEntityId(WORKFLOW_ID_2)
                                                     .state(State.BLOCKED.name())
                                                     .resourceUnit(INFRA_MAPPING_ID)
                                                     .releaseEntityType(HoldingScope.WORKFLOW.name())
                                                     .build();

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void ensureResourceConstraintForInfrastructureThrottlingWhenExists() {
    doReturn(query).when(wingsPersistence).createQuery(eq(ResourceConstraint.class));
    doReturn(query).doReturn(query).when(query).filter(ResourceConstraintKeys.accountId, ACCOUNT_ID);
    doReturn(query).when(query).filter(ResourceConstraintKeys.name, "Queuing");
    doReturn(resourceConstraint).when(query).get();
    doThrow(DuplicateKeyException.class).when(wingsPersistence).save(any(ResourceConstraint.class));
    ResourceConstraint savedResourceConstraint =
        resourceConstraintService.ensureResourceConstraintForConcurrency(ACCOUNT_ID, "Queuing");

    assertThat(savedResourceConstraint.getUuid()).isEqualTo(resourceConstraint.getUuid());
    assertThat(savedResourceConstraint.getName()).isEqualTo(resourceConstraint.getName());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void ensureResourceConstraintForInfrastructureThrottlingWhenDoNotExists() {
    doReturn(query).when(wingsPersistence).createQuery(eq(ResourceConstraint.class));
    doReturn(query).doReturn(query).when(query).filter(ResourceConstraintKeys.accountId, ACCOUNT_ID);
    doReturn(query).when(query).filter(ResourceConstraintKeys.name, "Queuing");
    doReturn(resourceConstraint).when(query).get();
    doThrow(DuplicateKeyException.class).when(wingsPersistence).save(resourceConstraint);
    ResourceConstraint savedResourceConstraint =
        resourceConstraintService.ensureResourceConstraintForConcurrency(ACCOUNT_ID, RESOURCE_CONSTRAINT_NAME);

    assertThat(savedResourceConstraint.getUuid()).isEqualTo(resourceConstraint.getUuid());
    assertThat(savedResourceConstraint.getName()).isEqualTo(resourceConstraint.getName());
  }

  @Test
  @Owner(developers = PRANJAL)
  @Category(UnitTests.class)
  @Ignore("This test is noop, rewrite to actually store resource constraints and check what the returns")
  public void shouldTestFetchEntityIdListForUnitAndEntityType() {
    doReturn(query).when(wingsPersistence).createQuery(eq(ResourceConstraintInstance.class));
    doReturn(query).doReturn(query).when(query).filter(ResourceConstraintInstanceKeys.appId, APP_ID);
    doReturn(query).when(query).filter(ResourceConstraintInstanceKeys.resourceConstraintId, RESOURCE_CONSTRAINT_ID);
    doReturn(query).when(query).filter(ResourceConstraintInstanceKeys.resourceUnit, INFRA_MAPPING_ID);
    doReturn(query).doReturn(query).when(query).filter(
        ResourceConstraintInstanceKeys.releaseEntityType, HoldingScope.WORKFLOW.name());
    doReturn(fieldEnd).when(query).field(ResourceConstraintInstanceKeys.state);
    doReturn(query).when(fieldEnd).in(asList(State.ACTIVE.name(), State.BLOCKED.name()));
    doReturn(asList(instance1, instance2)).when(query).asList();
    List<ResourceConstraintInstance> entityIds =
        resourceConstraintService.fetchResourceConstraintInstancesForUnitAndEntityType(
            APP_ID, RESOURCE_CONSTRAINT_ID, INFRA_MAPPING_ID, HoldingScope.WORKFLOW.name());

    assertThat(entityIds).isNotNull().hasSize(2);
  }

  // TODO: YOGESH use fake mongo in all tests
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetAllCurrentlyAcquiredPermits() {
    final int permits = 3;
    ResourceConstraintInstance resourceConstraintInstance =
        ResourceConstraintInstance.builder().permits(permits).build();
    Query mockQuery = mock(Query.class);
    doReturn(mockQuery).when(wingsPersistence).createQuery(any());
    doReturn(mockQuery).when(mockQuery).filter(ResourceConstraintInstanceKeys.appId, APP_ID);
    doReturn(mockQuery).when(mockQuery).filter(
        ResourceConstraintInstanceKeys.releaseEntityType, HoldingScope.WORKFLOW.name());
    doReturn(mockQuery).when(mockQuery).filter(ResourceConstraintInstanceKeys.releaseEntityId, WORKFLOW_EXECUTION_ID);
    doReturn(mockQuery).when(mockQuery).project(ResourceConstraintInstanceKeys.permits, true);
    doReturn(Arrays.asList(resourceConstraintInstance)).when(mockQuery).asList();
    assertThat(resourceConstraintService.getAllCurrentlyAcquiredPermits(
                   HoldingScope.WORKFLOW.name(), WORKFLOW_EXECUTION_ID, APP_ID))
        .isEqualTo(permits);

    // if resourceInstance is null
    doReturn(null).when(mockQuery).asList();
    assertThat(resourceConstraintService.getAllCurrentlyAcquiredPermits(
                   HoldingScope.WORKFLOW.name(), WORKFLOW_EXECUTION_ID, APP_ID))
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testUpdateWithCapacityGreaterThanUsage() {
    ResourceConstraintInstance resourceConstraintInstance = ResourceConstraintInstance.builder()
                                                                .resourceConstraintId(RESOURCE_CONSTRAINT_ID)
                                                                .permits(100)
                                                                .state(State.ACTIVE.name())
                                                                .build();
    doReturn(query).when(wingsPersistence).createQuery(ResourceConstraintInstance.class, excludeAuthority);
    doReturn(query).when(query).filter(ResourceConstraintInstanceKeys.resourceConstraintId, RESOURCE_CONSTRAINT_ID);
    doReturn(query).when(query).filter(ResourceConstraintInstanceKeys.state, State.ACTIVE.name());
    doReturn(query).when(query).project(ResourceConstraintInstanceKeys.permits, true);
    doReturn(Arrays.asList(resourceConstraintInstance)).when(query).asList();

    ResourceConstraint resourceConstraint = ResourceConstraint.builder()
                                                .uuid(RESOURCE_CONSTRAINT_ID)
                                                .lastUpdatedAt(1234)
                                                .accountId(ACCOUNT_ID)
                                                .name(RESOURCE_CONSTRAINT_NAME)
                                                .capacity(99)
                                                .build();
    assertThatThrownBy(() -> resourceConstraintService.update(resourceConstraint))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Resource Constraint capacity cannot be less than the current usage");
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testUpdateActiveConstraintsForPipeline() {
    ResourceConstraintInstance resourceConstraintInstance = ResourceConstraintInstance.builder()
                                                                .resourceConstraintId(RESOURCE_CONSTRAINT_ID)
                                                                .uuid("uuid")
                                                                .permits(100)
                                                                .state(State.ACTIVE.name())
                                                                .appId("appid")
                                                                .releaseEntityId("releaseid")
                                                                .releaseEntityType(PIPELINE)
                                                                .build();

    FieldEnd fieldEnd = mock(FieldEnd.class);
    UpdateOperations<ResourceConstraintInstance> mockOps = mock(UpdateOperations.class);
    UpdateResults updateResults = mock(UpdateResults.class);

    doReturn(query).when(wingsPersistence).createQuery(eq(ResourceConstraintInstance.class));
    doReturn(query).when(query).filter(eq(ResourceConstraintInstanceKeys.appId), any());
    doReturn(query).when(query).filter(eq(ResourceConstraintInstanceKeys.uuid), any());
    doReturn(query).when(query).filter(eq(ResourceConstraintInstanceKeys.resourceUnit), any());
    doReturn(fieldEnd).when(query).field(ResourceConstraintInstanceKeys.state);
    doReturn(query).when(fieldEnd).in(any());
    doReturn(mockOps).when(wingsPersistence).createUpdateOperations(any());
    doReturn(mockOps).when(mockOps).set(eq(ResourceConstraintInstanceKeys.state), any());
    doReturn(updateResults).when(wingsPersistence).update(query, mockOps);
    doReturn(2).when(updateResults).getUpdatedCount();
    doReturn(true).when(featureFlagService).isEnabled(any(), any());
    doReturn(true).when(workflowExecutionService).checkWorkflowExecutionInFinalStatus(any(), any());

    boolean response = resourceConstraintService.updateActiveConstraintForInstance(resourceConstraintInstance);

    assertThat(response).isTrue();

    verify(workflowExecutionService)
        .checkWorkflowExecutionInFinalStatus(
            eq(resourceConstraintInstance.getAppId()), eq(resourceConstraintInstance.getReleaseEntityId()));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testUpdateAConstraintsForPipelineWithFFDisabled() {
    ResourceConstraintInstance resourceConstraintInstance = ResourceConstraintInstance.builder()
                                                                .resourceConstraintId(RESOURCE_CONSTRAINT_ID)
                                                                .uuid("uuid")
                                                                .permits(100)
                                                                .state(State.ACTIVE.name())
                                                                .appId("appid")
                                                                .releaseEntityId("releaseid")
                                                                .releaseEntityType(PIPELINE)
                                                                .build();

    doReturn(false).when(featureFlagService).isEnabled(any(FeatureName.class), anyString());

    boolean response = resourceConstraintService.updateActiveConstraintForInstance(resourceConstraintInstance);

    assertThat(response).isFalse();
  }
}
