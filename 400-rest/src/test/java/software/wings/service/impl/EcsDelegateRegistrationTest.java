/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.MARKO;

import static software.wings.beans.DelegateSequenceConfig.Builder.aDelegateSequenceBuilder;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.AdditionalAnswers.returnsSecondArg;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateType;
import io.harness.delegate.utils.DelegateJreVersionHelper;
import io.harness.exception.GeneralException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.app.MainConfiguration;
import software.wings.beans.DelegateSequenceConfig;

import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.DEL)
@Slf4j
public class EcsDelegateRegistrationTest {
  @Mock private HPersistence persistence;
  @Mock(answer = RETURNS_SELF) private Query<DelegateSequenceConfig> query;
  @Mock(answer = RETURNS_SELF) private UpdateOperations<DelegateSequenceConfig> updateOperations;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private MainConfiguration mainConfiguration;
  @Mock private DelegateJreVersionHelper jreVersionHelper;
  private DelegateServiceImpl underTest;

  @Before
  public void setUp() {
    underTest = spy(DelegateServiceImpl.builder()
                        .persistence(persistence)
                        .featureFlagService(featureFlagService)
                        .mainConfiguration(mainConfiguration)
                        .jreVersionHelper(jreVersionHelper)
                        .build());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testHandleEcsDelegateRequest_KeepAliveRequest() {
    underTest.handleEcsDelegateRequest(Delegate.builder().keepAlivePacket(true).build());

    verify(underTest).handleEcsDelegateKeepAlivePacket(any());
    verify(underTest, never()).handleEcsDelegateRegistration(any());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testHandleEcsDelegateRequest_EcsDelegateRegistration() {
    final Delegate registeredDelegate = Delegate.builder().accountId(ACCOUNT_ID).hostName(HOST_NAME + "_5").build();
    final Delegate requestDelegate =
        Delegate.builder().accountId(ACCOUNT_ID).hostName(HOST_NAME + "_5").keepAlivePacket(false).build();

    doReturn(registeredDelegate).when(underTest).handleEcsDelegateRegistration(requestDelegate);
    doReturn(aDelegateSequenceBuilder().withHostName("delegate").withSequenceNum(1).withDelegateToken("token").build())
        .when(underTest)
        .getDelegateSequenceConfig(ACCOUNT_ID, HOST_NAME, 5);
    lenient().doReturn(false).when(featureFlagService).isEnabled(any(), any());

    underTest.handleEcsDelegateRequest(requestDelegate);

    verify(underTest, never()).handleEcsDelegateKeepAlivePacket(any());
    verify(underTest).handleEcsDelegateRegistration(any());
  }

  /**
   * Test HandleEcsDelegateKeepAlivePacket flow
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testHandleEcsDelegateKeepAlivePacket() {
    doReturn(null)
        .doReturn(Delegate.builder().build())
        .when(underTest)
        .getDelegateUsingSequenceNum(any(), any(), any());

    doReturn(aDelegateSequenceBuilder().withDelegateToken("aabbcc").build())
        .doReturn(aDelegateSequenceBuilder().withDelegateToken("aabbcc").build())
        .when(underTest)
        .getDelegateSequenceConfig(any(), any(), any());

    Delegate delegate = Delegate.builder().build();
    underTest.handleEcsDelegateKeepAlivePacket(delegate);
    verify(underTest, never()).getDelegateUsingSequenceNum(any(), any(), any());

    delegate.setHostName("host");
    underTest.handleEcsDelegateKeepAlivePacket(delegate);
    verify(underTest, never()).getDelegateUsingSequenceNum(any(), any(), any());

    delegate.setDelegateRandomToken("token");
    underTest.handleEcsDelegateKeepAlivePacket(delegate);
    verify(underTest, never()).getDelegateUsingSequenceNum(any(), any(), any());

    delegate.setUuid("id");
    underTest.handleEcsDelegateKeepAlivePacket(delegate);
    verify(underTest, never()).getDelegateUsingSequenceNum(any(), any(), any());

    delegate.setSequenceNum("1");
    underTest.handleEcsDelegateKeepAlivePacket(delegate);
    verify(underTest).getDelegateUsingSequenceNum(any(), any(), any());

    underTest.handleEcsDelegateKeepAlivePacket(delegate);
    verify(persistence, never()).createQuery(any());
    verify(persistence, never()).update(any(Query.class), any(UpdateOperations.class));

    mockWingsPersistanceForUpdateCall();

    delegate.setDelegateRandomToken("aabbcc");
    underTest.handleEcsDelegateKeepAlivePacket(delegate);
    verify(persistence).createQuery(any());
    verify(persistence).update(any(Query.class), any(UpdateOperations.class));
  }

  /**
   * Delegate heartbeat with valid delegateId and token.
   * Expected : - get delegate from db with Id.
   *            - get DelegateSequenceConfig for that delegate
   *            - match seqNum and token sent by delegate with this config
   *            - just update this same existing delegate
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testHandleEcsDelegateRegistration_activeDelegateWithId() {
    final DelegateSequenceConfig config = aDelegateSequenceBuilder().withDelegateToken("token").build();
    doReturn(config).when(underTest).getDelegateSequenceConfig(any(), any(), any());

    final Delegate delegate =
        Delegate.builder().uuid("12345").delegateType("ECS").delegateRandomToken("token").sequenceNum("1").build();

    final Query<Delegate> delegateQuery = mock(Query.class, RETURNS_SELF);

    lenient().when(persistence.createQuery(Delegate.class, excludeAuthority)).thenReturn(delegateQuery);
    when(persistence.createQuery(Delegate.class)).thenReturn(delegateQuery);

    doReturn(delegate).when(delegateQuery).get();
    lenient().when(delegateQuery.asList()).thenReturn(Collections.emptyList());
    doAnswer(returnsSecondArg()).when(underTest).upsertDelegateOperation(any(), any());

    final Delegate actual = underTest.handleEcsDelegateRegistration(delegate);

    assertThat(actual).isNotNull();
    assertThat(actual.getUuid()).isEqualTo("12345");

    verify(underTest).handleECSRegistrationUsingID(any());
    verify(underTest, never()).handleECSRegistrationUsingSeqNumAndToken(any());
    verify(underTest, never()).registerDelegateWithNewSequenceGeneration(any());
  }

  /**
   * Delegate heartbeat with delegateId = null and delegateToken = null.
   * Expected - Should throw exception
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testHandleEcsDelegateRegistration_empty_UUid_token() {
    doReturn(null).when(underTest).getDelegateSequenceConfig(any(), any(), any());

    final Delegate delegate = Delegate.builder().delegateType("ECS").build();
    assertThatThrownBy(() -> underTest.handleEcsDelegateRegistration(delegate))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Received invalid token from ECS delegate");

    delegate.setUuid("12345");
    delegate.setSequenceNum("1");
    assertThatThrownBy(() -> underTest.handleEcsDelegateRegistration(delegate))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Received invalid token from ECS delegate");
  }

  /**
   * Delegate heartbeat with seqNum and delegateToken
   * Scenario:
   * - Delegate sent {seqNum = 1, delegateToken = "xyz", hostName = "hostname"} for ACCID=1
   * - DelegateSequenceConfig exists {seqNum = 1, delegateToken = "xyz", hostName = "hostname", ACCID=1}
   * - Delegate exists {ACCID=1, hostname = hostname_1}
   * This existing delegate should be updated
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testHandleEcsDelegateRegistration_with_valid_seqNum_token() {
    DelegateSequenceConfig config = aDelegateSequenceBuilder()
                                        .withDelegateToken("token")
                                        .withSequenceNum(1)
                                        .withAccountId(ACCOUNT_ID)
                                        .withHostName("hostName")
                                        .build();

    doReturn(config).when(underTest).getDelegateSequenceConfig(any(), any(), any());

    Delegate delegate = Delegate.builder().delegateType("ECS").delegateRandomToken("token").sequenceNum("1").build();

    Delegate existingDelegate =
        Delegate.builder().delegateType("ECS").uuid("12345").delegateRandomToken("token").sequenceNum("1").build();

    doReturn(existingDelegate).doReturn(delegate).when(underTest).getDelegateUsingSequenceNum(any(), any(), any());

    doAnswer(returnsFirstArg()).doAnswer(returnsSecondArg()).when(underTest).upsertDelegateOperation(any(), any());

    delegate = underTest.handleEcsDelegateRegistration(delegate);
    assertThat(delegate).isNotNull();
    assertThat(delegate.getUuid()).isEqualTo("12345");

    verify(underTest).handleECSRegistrationUsingSeqNumAndToken(any());
    verify(underTest, never()).handleECSRegistrationUsingID(any());
    verify(underTest, never()).registerDelegateWithNewSequenceGeneration(any());
  }

  /**
   * Delegate heartbeat with seqNum and delegateToken
   * Scenario:
   * - Delegate sent {seqNum = 1, delegateToken = "xyz", hostName = "hostname"} for ACCID=1
   * - DelegateSequenceConfig exists {seqNum = 1, delegateToken = "xyz", hostName = "hostname", ACCID=1}
   * - No Delegate exists with {ACCID=1, hostname = hostname_1}
   * So new delegate record with hostname hostname_1 will be created.
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testHandleEcsDelegateRegistration_with_valid_seqNum_token_2() {
    DelegateSequenceConfig config = aDelegateSequenceBuilder()
                                        .withDelegateToken("token")
                                        .withSequenceNum(1)
                                        .withAccountId(ACCOUNT_ID)
                                        .withHostName("hostName")
                                        .build();

    doReturn(config).when(underTest).getDelegateSequenceConfig(any(), any(), any());

    Delegate delegate = Delegate.builder().delegateType("ECS").delegateRandomToken("token").sequenceNum("1").build();

    doReturn(null).when(underTest).getDelegateUsingSequenceNum(any(), any(), any());

    doNothing().when(underTest).updateDelegateWithConfigFromGroup(any());

    doAnswer(returnsSecondArg()).when(underTest).upsertDelegateOperation(any(), any());

    delegate = underTest.handleEcsDelegateRegistration(delegate);
    assertThat(delegate).isNotNull();
    assertThat(delegate.getUuid()).isEqualTo(null);
    assertThat(delegate.getHostName()).isEqualTo("hostName_1");

    ArgumentCaptor<Delegate> captor = ArgumentCaptor.forClass(Delegate.class);
    verify(underTest).upsertDelegateOperation(captor.capture(), any());
    assertThat(captor.getValue()).isNull();

    verify(underTest).handleECSRegistrationUsingSeqNumAndToken(any());
    verify(underTest, never()).handleECSRegistrationUsingID(any());
    verify(underTest, never()).registerDelegateWithNewSequenceGeneration(any());
  }

  /**
   * Delegate heartbeat with seqNum and delegateToken and null UUID
   * Scenario:
   * - Delegate sent null UUID anf {seqNum = 1, delegateToken = "xyz", hostName = "hostname"} for ACCID=1
   * - It will take path "handleECSRegistrationUsingSeqNumAndToken", but it returns null
   *   (not able to register delegate, may be due to some exception)
   * - Test it takes path registerDelegateWithNewSequenceGeneration() in this case
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testHandleEcsDelegateRegistration_with_valid_seqNum_token_3() {
    DelegateSequenceConfig config = aDelegateSequenceBuilder()
                                        .withDelegateToken("token")
                                        .withSequenceNum(1)
                                        .withAccountId(ACCOUNT_ID)
                                        .withHostName("hostName")
                                        .build();

    lenient().doReturn(config).when(underTest).getDelegateSequenceConfig(any(), any(), any());
    lenient().doReturn(false).when(underTest).checkForValidTokenIfPresent(any());
    Delegate delegate = Delegate.builder().delegateType("ECS").delegateRandomToken("token").sequenceNum("1").build();

    doReturn(null).when(underTest).handleECSRegistrationUsingSeqNumAndToken(any());
    doReturn(delegate).when(underTest).registerDelegateWithNewSequenceGeneration(any());

    underTest.handleEcsDelegateRegistration(delegate);
    verify(underTest).handleECSRegistrationUsingSeqNumAndToken(any());
    verify(underTest, never()).handleECSRegistrationUsingID(any());
    verify(underTest).registerDelegateWithNewSequenceGeneration(any());
  }

  /**
   * Expected
   *  - One of the DelegateSequenceConfigs in DB is stale (not refreshed in last 100 secs)
   *  - Get that 1 and associate with new delegat being registered.
   *  - Delete any existing delegate associated with that config
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetInactiveDelegateSequenceConfigToReplace() {
    List<DelegateSequenceConfig> existingDelegateSequenceConfigs = getExistingDelegateSequenceConfigs();

    Delegate delegate = Delegate.builder().delegateType("ECS").hostName("hostname").build();

    doReturn(Delegate.builder().uuid("12345").tags(Arrays.asList("tag1", "tag2")).build())
        .doReturn(null)
        .when(underTest)
        .getDelegateUsingSequenceNum(any(), any(), any());

    mockWingsPersistanceForUpdateCall();
    doReturn(null).when(underTest).delete(any(), any());

    DelegateSequenceConfig config =
        underTest.getInactiveDelegateSequenceConfigToReplace(delegate, existingDelegateSequenceConfigs);
    assertThat(config).isNotNull();
    assertThat(config.getSequenceNum().intValue()).isEqualTo(1);

    assertThat(delegate.getTags()).isNotNull();
    assertThat(delegate.getTags()).hasSize(2);
    assertThat(delegate.getTags().contains("tag1")).isTrue();
    assertThat(delegate.getTags().contains("tag2")).isTrue();
    assertThat(delegate.getHostName()).isEqualTo("hostname_1");

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(underTest).delete(any(), captor.capture());
    assertThat(captor.getValue()).isEqualTo("12345");
  }

  /**
   * SeqNum and token sent by delegate matches DelegateSequenceConfig
   * - Test for NPEs
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSeqNumAndTokenMatchesConfig() throws Exception {
    Delegate delegate = Delegate.builder().delegateType("ECS").delegateRandomToken("token").sequenceNum("1").build();
    DelegateSequenceConfig config = aDelegateSequenceBuilder()
                                        .withDelegateToken("token")
                                        .withSequenceNum(1)
                                        .withAccountId(ACCOUNT_ID)
                                        .withHostName("hostName")
                                        .build();
    assertThat(underTest.seqNumAndTokenMatchesConfig(delegate, config)).isTrue();

    config.setDelegateToken("abc");
    assertThat(underTest.seqNumAndTokenMatchesConfig(delegate, config)).isFalse();

    config.setDelegateToken(null);
    assertThat(underTest.seqNumAndTokenMatchesConfig(delegate, config)).isFalse();

    config.setDelegateToken(StringUtils.EMPTY);
    assertThat(underTest.seqNumAndTokenMatchesConfig(delegate, config)).isFalse();
  }

  /**
   * if existing delegateSequenceConfig records are,
   * {accId=1, seqNum=0, token="abc", hostname="host"},
   * {accId=1, seqNum=1, token="abc", hostname="host"}
   * {accId=1, seqNum=2, token="abc", hostname="host"}
   * New record should be {accId=1, seqNum=3, token="abc", hostname="host"}
   *
   * if existing delegateSequenceConfig records are,
   * {accId=1, seqNum=0, token="abc", hostname="host"},
   * {accId=1, seqNum=1, token="abc", hostname="host"}
   * {accId=1, seqNum=3, token="abc", hostname="host"}
   * New record should be {accId=1, seqNum=2, token="abc", hostname="host"}
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testAddNewDelegateSequenceConfigRecord() {
    mockWingsPersistanceForUpdateCall();

    List<DelegateSequenceConfig> existingDelegateSequenceConfigs = getExistingDelegateSequenceConfigs();
    doReturn(existingDelegateSequenceConfigs).when(query).asList();

    Delegate delegate =
        Delegate.builder().hostName("hostname").accountId(ACCOUNT_ID).delegateRandomToken("token").build();

    // existing sequenceConfigs are {.. seqNum = 0 / 1 / 2}, so 3 should picked as new
    DelegateSequenceConfig config = underTest.addNewDelegateSequenceConfigRecord(delegate);
    assertThat(config).isNotNull();
    assertThat(config.getSequenceNum().intValue()).isEqualTo(3);
    assertThat(config.getHostName()).isEqualTo("hostname");
    assertThat(config.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(delegate.getSequenceNum()).isEqualTo("3");

    // existing sequenceConfigs are {.. seqNum = 0 / 1 / 3}, so 2 should picked as new
    existingDelegateSequenceConfigs.get(2).setSequenceNum(3);
    delegate.setSequenceNum(null);
    config = underTest.addNewDelegateSequenceConfigRecord(delegate);
    assertThat(config).isNotNull();
    assertThat(config.getSequenceNum().intValue()).isEqualTo(2);
    assertThat(config.getHostName()).isEqualTo("hostname");
    assertThat(config.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(delegate.getSequenceNum()).isEqualTo("2");
  }

  /**
   *  Test 2 scenarios
   *  - Stale DelegateSequenceConfig exists, that  is taken
   *  and we do not create new DelegateSequenceconfig record
   *  - No stale config, we take path to create a new record for DelegateSequenceConfig
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testRegisterDelegateWithNewSequenceGeneration() {
    mockWingsPersistanceForUpdateCall();

    // Case 1: using seqNum from stale DelegateSequenceConfig
    doReturn(getExistingDelegateSequenceConfigs()).when(underTest).getDelegateSequenceConfigs(any());

    doReturn(aDelegateSequenceBuilder().build())
        .doReturn(null)
        .when(underTest)
        .getInactiveDelegateSequenceConfigToReplace(any(), any());

    doAnswer(returnsSecondArg()).when(underTest).upsertDelegateOperation(any(), any());

    doReturn(aDelegateSequenceBuilder().withSequenceNum(5).build())
        .when(underTest)
        .addNewDelegateSequenceConfigRecord(any());

    doNothing().when(underTest).updateDelegateWithConfigFromGroup(any());

    Delegate delegate = Delegate.builder().accountId(ACCOUNT_ID).hostName("hostname").build();
    delegate = underTest.registerDelegateWithNewSequenceGeneration(delegate);
    assertThat(delegate).isNotNull();
    verify(underTest, never()).addNewDelegateSequenceConfigRecord(any());

    // Case 2: creating new DelegateSequenceConfig record
    doReturn(getExistingDelegateSequenceConfigs()).when(underTest).getDelegateSequenceConfigs(any());

    doAnswer(returnsSecondArg()).when(underTest).upsertDelegateOperation(any(), any());
    delegate = underTest.registerDelegateWithNewSequenceGeneration(delegate);
    assertThat(delegate.getHostName()).isEqualTo("hostname_5");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetDelegateHostNameByRemovingSeqNum() {
    assertThat(underTest.getDelegateHostNameByRemovingSeqNum(
                   Delegate.builder().hostName("hostname_harness__delegate_1").build()))
        .isEqualTo("hostname_harness__delegate");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetDelegateSeqNumFromHostName() {
    assertThat(
        underTest.getDelegateSeqNumFromHostName(Delegate.builder().hostName("hostname_harness__delegate_1").build()))
        .isEqualTo("1");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testUpdateExistingDelegateWithSequenceConfigData() {
    doReturn(aDelegateSequenceBuilder()
                 .withSequenceNum(1)
                 .withHostName("hostname_harness__delegate")
                 .withDelegateToken("token")
                 .build())
        .when(underTest)
        .getDelegateSequenceConfig(any(), any(), any());

    Delegate delegate = Delegate.builder().hostName("hostname_harness__delegate_1").build();
    underTest.updateExistingDelegateWithSequenceConfigData(delegate);
    assertThat(delegate.getSequenceNum()).isEqualTo("1");
    assertThat(delegate.getDelegateRandomToken()).isEqualTo("token");

    ArgumentCaptor<Integer> captorSeqNum = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<String> captorHostName = ArgumentCaptor.forClass(String.class);
    verify(underTest).getDelegateSequenceConfig(any(), captorHostName.capture(), captorSeqNum.capture());
    assertThat(captorHostName.getValue()).isEqualTo("hostname_harness__delegate");
    assertThat(captorSeqNum.getValue().intValue()).isEqualTo(1);
  }

  private void mockWingsPersistanceForUpdateCall() {
    when(persistence.createQuery(DelegateSequenceConfig.class)).thenReturn(query);
    lenient().when(persistence.createQuery(DelegateSequenceConfig.class, excludeAuthority)).thenReturn(query);

    when(persistence.createUpdateOperations(DelegateSequenceConfig.class)).thenReturn(updateOperations);
    when(persistence.update(query, updateOperations)).thenReturn(null);
  }

  private List<DelegateSequenceConfig> getExistingDelegateSequenceConfigs() {
    List<DelegateSequenceConfig> existingDelegateSequenceConfigs = new ArrayList<>();

    existingDelegateSequenceConfigs.add(aDelegateSequenceBuilder()
                                            .withDelegateToken("abc")
                                            .withHostName("hostname")
                                            .withSequenceNum(0)
                                            .withAccountId(ACCOUNT_ID)
                                            .withLastUpdatedAt(System.currentTimeMillis())
                                            .build());

    existingDelegateSequenceConfigs.add(
        aDelegateSequenceBuilder()
            .withDelegateToken("abc")
            .withHostName("hostname")
            .withSequenceNum(1)
            .withAccountId(ACCOUNT_ID)
            .withLastUpdatedAt(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(200))
            .build());

    existingDelegateSequenceConfigs.add(aDelegateSequenceBuilder()
                                            .withDelegateToken("abc")
                                            .withHostName("hostname")
                                            .withSequenceNum(2)
                                            .withAccountId(ACCOUNT_ID)
                                            .withLastUpdatedAt(System.currentTimeMillis())
                                            .build());
    return existingDelegateSequenceConfigs;
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testExistingDelegateForEcs() {
    final DelegateSequenceConfig config = aDelegateSequenceBuilder().withDelegateToken("token").build();
    doReturn(config).when(underTest).getDelegateSequenceConfig(any(), any(), any());
    final Delegate delegate = Delegate.builder()
                                  .uuid("12345")
                                  .delegateType("ECS")
                                  .delegateRandomToken("token")
                                  .sequenceNum("1")
                                  .hostName("HOST_NAME_1")
                                  .build();
    final Query<Delegate> delegateQuery = mock(Query.class, RETURNS_SELF);
    lenient().when(persistence.createQuery(Delegate.class, excludeAuthority)).thenReturn(delegateQuery);
    when(persistence.createQuery(Delegate.class)).thenReturn(delegateQuery);

    doReturn(delegate).when(delegateQuery).get();
    lenient().when(delegateQuery.asList()).thenReturn(Collections.emptyList());
    doAnswer(returnsSecondArg()).when(underTest).upsertDelegateOperation(any(), any());
    final Delegate actual = underTest.handleEcsDelegateRegistration(delegate);
    assertThat(actual).isNotNull();
    assertThat(actual.getUuid()).isEqualTo("12345");
    doReturn(delegate).when(delegateQuery).get();
    // HB update or register call from already existing delegate comes with hostName and sequence num
    String hostName = underTest.getHostNameToBeUsedForECSDelegate(HOST_NAME, "1");
    Delegate existingDelegate = underTest.getExistingDelegate(ACCOUNT_ID, hostName, false, DelegateType.ECS, "");
    assertThat(existingDelegate).isNotNull();
    assertThat(existingDelegate.getUuid()).isEqualTo("12345");
    assertThat(existingDelegate.getHostName()).isEqualTo("HOST_NAME_1");
  }
}
