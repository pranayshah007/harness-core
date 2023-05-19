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
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.datafetcher.delegate.DelegateApprovalDataFetcher;
import io.harness.app.schema.mutation.delegate.input.QLDelegateApproveRejectInput;
import io.harness.app.schema.mutation.delegate.payload.QLDelegateApproveRejectPayload;
import io.harness.app.schema.type.delegate.QLDelegateApproval;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import io.jsonwebtoken.lang.Assert;
import java.sql.SQLException;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(DEL)
public class DelegateApprovalDataFetcherTest extends AbstractDataFetcherTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT-ID";

  @Inject DelegateService delegateService;
  @Inject private DelegateApprovalDataFetcher delegateApprovalDataFetcher;
  @Inject private HPersistence persistence;
  @Inject private BroadcasterFactory broadcasterFactory;
  @Mock private Broadcaster broadcaster;
  private static final String DELEGATE_TYPE = "dockerType";
  private static final String VERSION = "1.0.0";

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(broadcaster);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testApproveFromDelegateApprovalDataFetcher() {
    String accountId = generateUuid();
    String delegateId = generateUuid();

    Delegate existingDelegate =
        createDelegateBuilder(accountId, delegateId, DelegateInstanceStatus.WAITING_FOR_APPROVAL).build();
    persistence.save(existingDelegate);

    QLDelegateApproveRejectInput input = QLDelegateApproveRejectInput.builder()
                                             .accountId(existingDelegate.getAccountId())
                                             .delegateId(existingDelegate.getUuid())
                                             .delegateApproval(QLDelegateApproval.ACTIVATE)
                                             .build();
    QLDelegateApproveRejectPayload delegateApprovalPayload =
        delegateApprovalDataFetcher.mutateAndFetch(input, MutationContext.builder().accountId(accountId).build());
    Assert.notNull(delegateApprovalPayload.getDelegate());
    Assert.isTrue(delegateApprovalPayload.getDelegate().getStatus().equals(DelegateInstanceStatus.ENABLED.toString()));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRejectFromDelegateApprovalDataFetcher() {
    String accountId = generateUuid();
    String delegateId = generateUuid();

    Delegate existingDelegate =
        createDelegateBuilder(accountId, delegateId, DelegateInstanceStatus.WAITING_FOR_APPROVAL).build();
    persistence.save(existingDelegate);

    QLDelegateApproveRejectInput input = QLDelegateApproveRejectInput.builder()
                                             .accountId(existingDelegate.getAccountId())
                                             .delegateId(existingDelegate.getUuid())
                                             .delegateApproval(QLDelegateApproval.REJECT)
                                             .build();
    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(broadcaster);
    QLDelegateApproveRejectPayload delegateApprovalPayload =
        delegateApprovalDataFetcher.mutateAndFetch(input, MutationContext.builder().accountId(accountId).build());
    Assert.notNull(delegateApprovalPayload.getDelegate());
    Assert.isTrue(delegateApprovalPayload.getDelegate().getStatus().equals(DelegateInstanceStatus.DELETED.toString()));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testApprovalActionOnAlreadyApprovedRejectedDelegate() {
    String accountId = generateUuid();
    String delegateId = generateUuid();

    Delegate existingDelegate = createDelegateBuilder(accountId, delegateId, DelegateInstanceStatus.ENABLED).build();
    persistence.save(existingDelegate);

    QLDelegateApproveRejectInput input = QLDelegateApproveRejectInput.builder()
                                             .accountId(existingDelegate.getAccountId())
                                             .delegateId(existingDelegate.getUuid())
                                             .delegateApproval(QLDelegateApproval.REJECT)
                                             .build();
    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(broadcaster);
    assertThatThrownBy(
        () -> delegateApprovalDataFetcher.mutateAndFetch(input, MutationContext.builder().accountId(accountId).build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Delegate is already in state ENABLED");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testApprovalActionOnNonExistingDelegate() {
    String accountId = generateUuid();
    String delegateId = "TEST_DELEGATE_ID";

    QLDelegateApproveRejectInput input = QLDelegateApproveRejectInput.builder()
                                             .accountId(accountId)
                                             .delegateId(delegateId)
                                             .delegateApproval(QLDelegateApproval.ACTIVATE)
                                             .build();
    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(broadcaster);
    assertThatThrownBy(
        () -> delegateApprovalDataFetcher.mutateAndFetch(input, MutationContext.builder().accountId(accountId).build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unable to fetch delegate with delegate ID TEST_DELEGATE_ID");
  }

  private DelegateBuilder createDelegateBuilder(
      String accountId, String delegateId, DelegateInstanceStatus delegateInstanceStatus) {
    return Delegate.builder()
        .accountId(accountId)
        .ip("127.0.0.1")
        .uuid(delegateId)
        .hostName("localhost")
        .delegateName("testDelegateName")
        .delegateType(DELEGATE_TYPE)
        .version(VERSION)
        .status(delegateInstanceStatus)
        .lastHeartBeat(System.currentTimeMillis());
  }
}
