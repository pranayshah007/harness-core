/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.delegate;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.JENNY;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.datafetcher.delegate.AddDelegateScopeDataFetcher;
import io.harness.app.schema.mutation.delegate.input.QLAddDelegateScopeInput;
import io.harness.app.schema.mutation.delegate.input.QLAddDelegateScopeInput.QLAddDelegateScopeInputBuilder;
import io.harness.app.schema.mutation.delegate.payload.QLAddDelegateScopePayload;
import io.harness.app.schema.type.delegate.QLDelegateStatus;
import io.harness.app.schema.type.delegate.QLTaskGroup;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.TaskGroup;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.type.QLEnvironmentType;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.service.intfc.DelegateScopeService;

import com.google.inject.Inject;
import io.jsonwebtoken.lang.Assert;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(DEL)
public class AddDelegateScopeDataFetcherTest extends AbstractDataFetcherTestBase {
  @Inject DelegateScopeService delegateScopeService;
  @Inject AddDelegateScopeDataFetcher delegateScopeDataFetcher;

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAddDelegateScope() {
    String accountId = generateUuid();
    QLAddDelegateScopeInputBuilder qlAddDelegateScopeInputBuilder =
        QLAddDelegateScopeInput.builder()
            .accountId(accountId)
            .name("DELEGATE_SCOPE")
            .application(null)
            .environment(null)
            .service(null)
            .environmentTypes(Collections.singletonList(QLEnvironmentType.NON_PROD))
            .taskGroup(QLTaskGroup.APPDYNAMICS);
    QLAddDelegateScopePayload addDelegateScopePayload = delegateScopeDataFetcher.mutateAndFetch(
        qlAddDelegateScopeInputBuilder.build(), MutationContext.builder().accountId(accountId).build());
    Assert.notNull(addDelegateScopePayload.getDelegateScope());
  }
  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAddDelegateScope2() {
    String accountId = generateUuid();
    String[] applications = {"app1", "app2"};
    String[] environments = {"env1"};
    String[] services = {"serv1", "sev2"};

    QLIdFilter applicationsFilter = QLIdFilter.builder().values(applications).build();
    QLIdFilter environmentFilter = QLIdFilter.builder().values(environments).build();
    QLIdFilter servicesFilter = QLIdFilter.builder().values(services).build();

    QLAddDelegateScopeInputBuilder qlAddDelegateScopeInputBuilder =
        QLAddDelegateScopeInput.builder()
            .accountId(accountId)
            .name("DELEGATE_SCOPE")
            .application(applicationsFilter)
            .environment(environmentFilter)
            .service(servicesFilter)
            .environmentTypes(Collections.singletonList(QLEnvironmentType.NON_PROD))
            .taskGroup(QLTaskGroup.APPDYNAMICS);
    QLAddDelegateScopePayload addDelegateScopePayload = delegateScopeDataFetcher.mutateAndFetch(
        qlAddDelegateScopeInputBuilder.build(), MutationContext.builder().accountId(accountId).build());
    Assert.notNull(addDelegateScopePayload.getDelegateScope());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAddInvalidDelegateScope() {
    String accountId = generateUuid();
    QLAddDelegateScopeInputBuilder qlAddDelegateScopeInputBuilder =
        QLAddDelegateScopeInput.builder().accountId(accountId).name("DELEGATE_SCOPE");
    assertThatThrownBy(()
                           -> delegateScopeDataFetcher.mutateAndFetch(qlAddDelegateScopeInputBuilder.build(),
                               MutationContext.builder().accountId(accountId).build()))
        .isInstanceOf(WingsException.class)
        .hasMessage("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testTaskGroupEnumCheck() {
    Set<TaskGroup> taskGroupSet = EnumSet.allOf(TaskGroup.class);
    Set<QLTaskGroup> qlTaskGroupSet = EnumSet.allOf(QLTaskGroup.class);
    Assert.isTrue(taskGroupSet.size() == qlTaskGroupSet.size());
    List<String> qlTaskGroupTaskList = new ArrayList<>();
    qlTaskGroupSet.forEach(qltaskGroup -> qlTaskGroupTaskList.add(qltaskGroup.name()));
    for (TaskGroup taskGroup : taskGroupSet) {
      Assert.isTrue(qlTaskGroupTaskList.contains(taskGroup.name()));
    }
  }
  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateStatusEnumCheck() {
    Set<DelegateInstanceStatus> delegateInstanceStatuses = EnumSet.allOf(DelegateInstanceStatus.class);
    Set<QLDelegateStatus> qlDelegateStatuses = EnumSet.allOf(QLDelegateStatus.class);
    List<String> statusList = new ArrayList<>();
    qlDelegateStatuses.forEach(qlDelegateStatus -> statusList.add(qlDelegateStatus.getStringValue()));
    for (DelegateInstanceStatus delegateInstanceStatus : delegateInstanceStatuses) {
      if (delegateInstanceStatus == DelegateInstanceStatus.DISABLED) {
        continue;
      }
      Assert.isTrue(statusList.contains(delegateInstanceStatus.toString()));
    }
  }
}
