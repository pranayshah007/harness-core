/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARPIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateHeartbeatDetails;
import io.harness.delegate.beans.DelegateInitializationDetails;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.service.intfc.DelegateService;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateVerificationNgResourceTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "testAccountId";
  private static final String TEST_ORG_ID = generateUuid();
  private static final String TEST_PROJECT_ID = generateUuid();
  private static final String TEST_DELEGATE_ID = "testDelegateId";
  private static final String TEST_DELEGATE_NAME = "testDelegateName";
  private static final long TEST_PROFILE_EXECUTION_TIME = System.currentTimeMillis();

  @Mock private DelegateService delegateService;
  @Mock private AccessControlClient accessControlClient;

  private DelegateVerificationNgResource resource;

  @Before
  public void setUp() {
    initMocks(this);
    resource = new DelegateVerificationNgResource(delegateService, accessControlClient);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testGetDelegatesHeartbeatDetails_noRegisteredDelegates() {
    when(delegateService.obtainDelegateIdsUsingName(any(String.class), any(String.class))).thenReturn(null);

    RestResponse<DelegateHeartbeatDetails> delegatesHeartbeatDetails =
        resource.getDelegatesHeartbeatDetailsV2(TEST_ACCOUNT_ID, TEST_ORG_ID, TEST_PROJECT_ID, TEST_DELEGATE_NAME);

    assertThat(delegatesHeartbeatDetails.getResource()).isNotNull();
    assertThat(delegatesHeartbeatDetails.getResource().getNumberOfRegisteredDelegates()).isZero();
    assertThat(delegatesHeartbeatDetails.getResource().getNumberOfConnectedDelegates()).isZero();
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testGetDelegatesHeartbeatDetails_noConnectedDelegates() {
    List<Delegate> registeredDelegates =
        Collections.singletonList(Delegate.builder().uuid(TEST_DELEGATE_ID).immutable(false).build());

    when(delegateService.obtainDelegatesUsingName(any(String.class), any(String.class)))
        .thenReturn(registeredDelegates);
    when(delegateService.getConnectedDelegates(TEST_ACCOUNT_ID, registeredDelegates))
        .thenReturn(Collections.emptyList());

    RestResponse<DelegateHeartbeatDetails> delegatesHeartbeatDetails =
        resource.getDelegatesHeartbeatDetailsV2(TEST_ACCOUNT_ID, TEST_ORG_ID, TEST_PROJECT_ID, TEST_DELEGATE_NAME);

    assertThat(delegatesHeartbeatDetails.getResource()).isNotNull();
    assertThat(delegatesHeartbeatDetails.getResource().getNumberOfRegisteredDelegates()).isEqualTo(1);
    assertThat(delegatesHeartbeatDetails.getResource().getNumberOfConnectedDelegates()).isZero();
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testGetDelegatesHeartbeatDetails_connectedDelegates() {
    List<Delegate> registeredDelegates =
        Collections.singletonList(Delegate.builder().uuid(TEST_DELEGATE_ID).immutable(false).build());

    when(delegateService.obtainDelegatesUsingName(any(String.class), any(String.class)))
        .thenReturn(registeredDelegates);
    when(delegateService.getConnectedDelegates(TEST_ACCOUNT_ID, registeredDelegates))
        .thenReturn(Collections.singletonList(Delegate.builder().uuid(TEST_DELEGATE_ID).immutable(false).build()));

    RestResponse<DelegateHeartbeatDetails> delegatesHeartbeatDetails =
        resource.getDelegatesHeartbeatDetailsV2(TEST_ACCOUNT_ID, TEST_ORG_ID, TEST_PROJECT_ID, TEST_DELEGATE_NAME);

    assertThat(delegatesHeartbeatDetails.getResource()).isNotNull();
    assertThat(delegatesHeartbeatDetails.getResource().getNumberOfRegisteredDelegates()).isEqualTo(1);
    assertThat(delegatesHeartbeatDetails.getResource().getNumberOfConnectedDelegates()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void getDelegatesInitializationDetails_noDelegateIds() {
    when(delegateService.obtainDelegateIdsUsingName(any(String.class), any(String.class))).thenReturn(null);

    RestResponse<List<DelegateInitializationDetails>> delegatesInitializationDetails =
        resource.getDelegatesInitializationDetailsV2(TEST_ACCOUNT_ID, TEST_ORG_ID, TEST_PROJECT_ID, TEST_DELEGATE_NAME);

    assertThat(delegatesInitializationDetails.getResource()).isEmpty();
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void getDelegatesInitializationDetails_success() {
    List<String> registeredDelegateIds = Collections.singletonList(TEST_DELEGATE_ID);

    when(delegateService.obtainDelegateIdsUsingName(any(String.class), any(String.class)))
        .thenReturn(registeredDelegateIds);
    when(delegateService.obtainDelegateInitializationDetails(TEST_ACCOUNT_ID, registeredDelegateIds))
        .thenReturn(Collections.singletonList(DelegateInitializationDetails.builder()
                                                  .delegateId(TEST_DELEGATE_ID)
                                                  .initialized(true)
                                                  .profileError(false)
                                                  .profileExecutedAt(TEST_PROFILE_EXECUTION_TIME)
                                                  .build()));

    RestResponse<List<DelegateInitializationDetails>> delegatesInitializationDetails =
        resource.getDelegatesInitializationDetailsV2(TEST_ACCOUNT_ID, TEST_ORG_ID, TEST_PROJECT_ID, TEST_DELEGATE_NAME);
    assertThat(delegatesInitializationDetails).isNotNull();

    List<DelegateInitializationDetails> initializationDetails = delegatesInitializationDetails.getResource();
    assertThat(initializationDetails).isNotEmpty();
    assertThat(initializationDetails.size()).isEqualTo(1);
    assertThat(initializationDetails.get(0).getDelegateId()).isEqualTo(TEST_DELEGATE_ID);
    assertThat(initializationDetails.get(0).isInitialized()).isTrue();
    assertThat(initializationDetails.get(0).isProfileError()).isFalse();
    assertThat(initializationDetails.get(0).getProfileExecutedAt()).isEqualTo(TEST_PROFILE_EXECUTION_TIME);
  }
}
