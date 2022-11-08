/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.json;

import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.DelegateHeartbeatResponseStreaming;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateProfileParams;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskEvent.DelegateTaskEventBuilder;
import io.harness.rule.Owner;

import software.wings.beans.TaskType;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
@Slf4j
public class JsonUtilsTest {
  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  @Parameters(method = "parametersForSerialization")
  public void testManagerToDelegateJson(final Object obj, final Class<?> clazz, final List<String> ignoredFields) {
    final var json = io.harness.serializer.JsonUtils.asJson(obj);
    final var actual = JsonUtils.asObject(json, clazz);

    assertThat(actual).isEqualToIgnoringGivenFields(obj, ignoredFields.toArray(new String[0]));
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  @Parameters(method = "parametersForSerialization")
  public void testDelegateToManagerJson(final Object obj, final Class<?> clazz, final List<String> ignoredFields) {
    final var json = JsonUtils.asJson(obj);
    final var actual = io.harness.serializer.JsonUtils.asObject(json, clazz);

    assertThat(actual).isEqualToIgnoringGivenFields(obj, ignoredFields.toArray(new String[0]));
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testJsonSubtypes() throws JsonProcessingException {
    final DelegateTaskAbortEvent taskEvent = createAbortEvent();

    final var json = io.harness.serializer.JsonUtils.asPrettyJson(taskEvent);
    final var actual = JsonUtils.asObject(json, DelegateTaskEvent.class); // Deserialize as base class

    assertThat(actual).isEqualToIgnoringGivenFields(taskEvent);
    assertThat(actual).isExactlyInstanceOf(DelegateTaskAbortEvent.class);
  }

  private Object[] parametersForSerialization() {
    return new Object[] {
        new Object[] {createTaskEvent(), DelegateTaskEvent.class, Collections.singletonList("taskType")},
        new Object[] {createAbortEvent(), DelegateTaskEvent.class, Collections.emptyList()},
        new Object[] {createHeartbeatResponse(), DelegateHeartbeatResponseStreaming.class, Collections.emptyList()},
        new Object[] {createDelegateParams(), DelegateParams.class, Collections.emptyList()},
        new Object[] {createProfileParams(), DelegateProfileParams.class, Collections.emptyList()},
    };
  }

  @NonNull
  private DelegateParams createDelegateParams() {
    return DelegateParams.builder()
        .accountId("acc")
        .ceEnabled(true)
        .delegateName("del name")
        .tags(List.of("tag1", "tag2"))
        .build();
  }

  @NonNull
  private DelegateProfileParams createProfileParams() {
    return DelegateProfileParams.builder()
        .name("my name")
        .profileId("profile id")
        .profileLastUpdatedAt(1111)
        .scriptContent("/bin/bash echo")
        .build();
  }

  @NonNull
  private DelegateHeartbeatResponseStreaming createHeartbeatResponse() {
    return DelegateHeartbeatResponseStreaming.builder()
        .delegateId("delegateId")
        .responseSentAt(1000)
        .delegateRandomToken("random token")
        .sequenceNumber("sequence")
        .build();
  }

  @NonNull
  private DelegateTaskEvent createTaskEvent() {
    return DelegateTaskEventBuilder.aDelegateTaskEvent()
        .withAccountId("accountId")
        .withDelegateTaskId("taskId")
        .withTaskType(TaskType.SCRIPT.name())
        .withSync(true)
        .build();
  }

  @NonNull
  private DelegateTaskAbortEvent createAbortEvent() {
    return DelegateTaskAbortEvent.Builder.aDelegateTaskAbortEvent()
        .withAccountId("accountId")
        .withDelegateTaskId("taskId")
        .withSync(true)
        .build();
  }
}
