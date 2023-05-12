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

import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.datafetcher.delegate.DeleteDelegateDataFetcher;
import io.harness.app.schema.mutation.delegate.input.QLDeleteDelegateInput;
import io.harness.app.schema.mutation.delegate.payload.QLDeleteDelegatePayload;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.beans.User;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.security.UserThreadLocal;
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
public class DeleteDelegateDataFetcherTest extends AbstractDataFetcherTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT-ID";

  @Inject DelegateService delegateService;
  @Inject private DeleteDelegateDataFetcher deleteDelegateDataFetcher;
  @Inject private HPersistence persistence;
  @Inject BroadcasterFactory broadcasterFactory;
  @Mock Broadcaster broadcaster;
  private static final String DELEGATE_TYPE = "dockerType";
  private static final String VERSION = "1.0.0";
  private static final String STREAM_DELEGATE = "/stream/delegate/";
  public static final String APPLICATION_NAME = "APPLICATION_NAME";
  private User user;

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
    user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testSetStatusDeleteDelegate() {
    String accountId = generateUuid();
    String delegateId = generateUuid();

    Delegate existingDelegate = createDelegateBuilder().build();
    existingDelegate.setUuid(delegateId);
    existingDelegate.setAccountId(accountId);
    existingDelegate.setStatus(DelegateInstanceStatus.WAITING_FOR_APPROVAL);
    existingDelegate.setLastHeartBeat(System.currentTimeMillis() - ofMinutes(2).toMillis());
    persistence.save(existingDelegate);
    QLDeleteDelegateInput deleteDelegateInput =
        QLDeleteDelegateInput.builder().delegateId(delegateId).accountId(accountId).build();
    when(broadcasterFactory.lookup(any(), anyBoolean())).thenReturn(broadcaster);
    QLDeleteDelegatePayload deleteDelegatePayload = deleteDelegateDataFetcher.mutateAndFetch(
        deleteDelegateInput, MutationContext.builder().accountId(accountId).build());

    Assert.isTrue(deleteDelegatePayload.getMessage().equals("Delegate deleted"));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testNonExistingDeleteDelegate() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    QLDeleteDelegateInput deleteDelegateInput =
        QLDeleteDelegateInput.builder().delegateId(delegateId).accountId(accountId).build();
    assertThatThrownBy(()
                           -> deleteDelegateDataFetcher.mutateAndFetch(
                               deleteDelegateInput, MutationContext.builder().accountId(accountId).build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unable to fetch delegate with delegate id " + delegateId);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testNonExistingSetStatusDeleteDelegate() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    QLDeleteDelegateInput deleteDelegateInput =
        QLDeleteDelegateInput.builder().delegateId(delegateId).accountId(accountId).build();
    assertThatThrownBy(()
                           -> deleteDelegateDataFetcher.mutateAndFetch(
                               deleteDelegateInput, MutationContext.builder().accountId(accountId).build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unable to fetch delegate with delegate id " + delegateId);
  }

  private DelegateBuilder createDelegateBuilder() {
    return Delegate.builder()
        .accountId(ACCOUNT_ID)
        .ip("127.0.0.1")
        .hostName("localhost")
        .delegateName("testDelegateName")
        .delegateType(DELEGATE_TYPE)
        .version(VERSION)
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }
}
