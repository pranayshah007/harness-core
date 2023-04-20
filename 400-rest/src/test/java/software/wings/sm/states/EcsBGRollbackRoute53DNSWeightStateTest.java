/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.sm.ExecutionContextImpl;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class EcsBGRollbackRoute53DNSWeightStateTest extends WingsBaseTest {
  @Mock private EcsStateHelper ecsStateHelper;
  @InjectMocks
  @Spy
  private final EcsBGRollbackRoute53DNSWeightState state = new EcsBGRollbackRoute53DNSWeightState("stateName");

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecute() throws InterruptedException {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doReturn(null).when(state).executeInternal(any(), anyBoolean());
    state.execute(mockContext);
    verify(state).executeInternal(any(), anyBoolean());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTimeoutMillis() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doReturn(10).when(ecsStateHelper).getEcsStateTimeoutFromContext(any(), anyBoolean());
    assertThat(state.getTimeoutMillis(mockContext)).isEqualTo(10);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteWingsExceptionThrown() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doThrow(new InvalidRequestException("test"))
        .when((EcsBGUpdateRoute53DNSWeightState) state)
        .executeInternal(mockContext, true);
    state.execute(mockContext);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInvalidRequestException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doThrow(new NullPointerException())
        .when((EcsBGUpdateRoute53DNSWeightState) state)
        .executeInternal(mockContext, true);
    state.execute(mockContext);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetRecordTTL() {
    state.setRecordTTL(3);
    assertThat(state.getRecordTTL()).isEqualTo(3);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testIsDownsizeOldService() {
    state.setDownsizeOldService(true);
    assertThat(state.isDownsizeOldService()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetOldServiceDNSWeight() {
    state.setOldServiceDNSWeight(1);
    assertThat(state.getOldServiceDNSWeight()).isEqualTo(1);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetNewServiceDNSWeight() {
    state.setNewServiceDNSWeight(1);
    assertThat(state.getNewServiceDNSWeight()).isEqualTo(1);
  }
}
