/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package software.wings.service.delegate;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.JENNY;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateCapacity;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.persistence.HPersistence;
import io.harness.queueservice.infc.DelegateCapacityManagementService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.TaskType;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.context.annotation.Description;

public class DelegateCapacityManagementServiceTest extends WingsBaseTest {
  @Inject private DelegateCapacityManagementService delegateCapacityManagementService;

  @Inject private HPersistence persistence;
  private static final String VERSION = "1.0.0";
  private static final String DELEGATE_TYPE = "dockerType";
  private static final List<String> supportedTasks = Arrays.stream(TaskType.values()).map(Enum::name).collect(toList());

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Test registering capacity for delegate")
  public void testRegisterDelegateCapacity() {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId);
    delegateCapacityManagementService.registerDelegateCapacity(
        accountId, delegate.getUuid(), DelegateCapacity.builder().maximumNumberOfBuilds(5).build());
    Delegate delegateAfterUpdate = persistence.get(Delegate.class, delegate.getUuid());
    assertThat(delegateAfterUpdate.getDelegateCapacity().getMaximumNumberOfBuilds()).isEqualTo(5);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Verify has delegate capacity set in delegate")
  public void testHasDelegateCapacity() {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId);
    assertThat(delegate.hasCapacityRegistered()).isFalse();
    delegateCapacityManagementService.registerDelegateCapacity(
        accountId, delegate.getUuid(), DelegateCapacity.builder().maximumNumberOfBuilds(5).build());
    Delegate delegateAfterUpdate = persistence.get(Delegate.class, delegate.getUuid());
    assertThat(delegateAfterUpdate.hasCapacityRegistered()).isTrue();
    assertThat(delegateAfterUpdate.getDelegateCapacity().getMaximumNumberOfBuilds()).isEqualTo(5);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Verify has delegate capacity set in delegate")
  public void testGetDelegateCapacity() {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId);
    delegateCapacityManagementService.registerDelegateCapacity(
        accountId, delegate.getUuid(), DelegateCapacity.builder().maximumNumberOfBuilds(5).build());
    Delegate delegateAfterUpdate = persistence.get(Delegate.class, delegate.getUuid());
    DelegateCapacity delegateCapacity = delegateCapacityManagementService.getDelegateCapacity(
        delegateAfterUpdate.getUuid(), delegateAfterUpdate.getAccountId());
    assertThat(delegateCapacity.getMaximumNumberOfBuilds()).isEqualTo(5);
  }

  private Delegate createDelegate(String accountId) {
    Delegate delegate = createDelegateBuilder(accountId).build();
    persistence.save(delegate);
    return delegate;
  }
  private DelegateBuilder createDelegateBuilder(String accountId) {
    return Delegate.builder()
        .accountId(accountId)
        .ip("127.0.0.1")
        .hostName("localhost")
        .delegateName("testDelegateName")
        .delegateType(DELEGATE_TYPE)
        .version(VERSION)
        .supportedTaskTypes(supportedTasks)
        .tags(ImmutableList.of("aws-delegate", "sel1", "sel2"))
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }
}
