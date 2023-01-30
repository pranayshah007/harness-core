/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.timeout;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.TimeoutEngineTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.repositories.TimeoutInstanceRepository;
import io.harness.rule.Owner;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTracker;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class TimeoutEngineUnitTest extends TimeoutEngineTestBase {
  @Mock TimeoutInstanceRepository timeoutInstanceRepository;
  @Inject private TimeoutEngine timeoutEngine;
  @Inject private HPersistence hPersistence;

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testRegisterTimeout() {
    TestTimeoutCallback callback = new TestTimeoutCallback();
    TimeoutInstance instance = timeoutEngine.registerTimeout(new AbsoluteTimeoutTracker(1000), callback);
    timeoutEngine.handle(instance);
    assertThat(callback.getTimeoutInstance()).isNotNull();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testRegisterAbsoluteTimeout() {
    TestTimeoutCallback callback = new TestTimeoutCallback();
    TimeoutInstance instance = timeoutEngine.registerAbsoluteTimeout(Duration.ofMillis(1000), callback);
    timeoutEngine.handle(instance);
    assertThat(callback.getTimeoutInstance()).isNotNull();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteTimeout() {
    TestTimeoutCallback callback = new TestTimeoutCallback();
    TimeoutInstance instance = timeoutEngine.registerTimeout(new AbsoluteTimeoutTracker(1000), callback);
    timeoutEngine.deleteTimeout(instance.getUuid());
    TimeoutInstance timeoutInstance = hPersistence.get(TimeoutInstance.class, instance.getUuid());
    assertThat(timeoutInstance).isNull();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteTimeouts() {
    on(timeoutEngine).set("timeoutInstanceRepository", timeoutInstanceRepository);
    List<String> timeoutIds = new LinkedList<>();
    for (int i = 0; i < 601; i++) {
      timeoutIds.add(String.valueOf(i));
    }
    doNothing().when(timeoutInstanceRepository).deleteByUuidIn(any());
    timeoutEngine.deleteTimeouts(timeoutIds);
    verify(timeoutInstanceRepository, times(2)).deleteByUuidIn(any());
  }

  public static class TestTimeoutCallback implements TimeoutCallback {
    private TimeoutInstance timeoutInstance;
    public TimeoutInstance getTimeoutInstance() {
      return timeoutInstance;
    }

    @Override
    public void onTimeout(TimeoutInstance timeoutInstance) {
      this.timeoutInstance = timeoutInstance;
    }
  }
}
