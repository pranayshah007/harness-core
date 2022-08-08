/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.distribution.constraint;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConsumerContextUtilsTest {
  private static final String RELEASE_ENTITY_ID = generateUuid();
  private static final Consumer VALID_CONSUMER =
      Consumer.builder()
          .id(new ConsumerId("consumerId"))
          .permits(1)
          .state(Consumer.State.ACTIVE)
          .context(ImmutableMap.of("releaseEntityType", "STAGE", "releaseEntityId", RELEASE_ENTITY_ID))
          .build();

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyHasContext() {
    assertThat(ConsumerContextUtils.hasContext(VALID_CONSUMER)).isTrue();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyHasContextInvalid() {
    assertThat(ConsumerContextUtils.hasContext(Consumer.builder().build())).isFalse();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetReleaseEntityTypeReturnNull() {
    assertThat(
        ConsumerContextUtils.getReleaseEntityType(Consumer.builder().context(singletonMap("aKey", "aValue")).build()))
        .isNull();
    assertThat(ConsumerContextUtils.getReleaseEntityType(Consumer.builder().context(Collections.emptyMap()).build()))
        .isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetReleaseEntityIdReturnNull() {
    assertThat(
        ConsumerContextUtils.getReleaseEntityId(Consumer.builder().context(singletonMap("aKey", "aValue")).build()))
        .isNull();
    assertThat(ConsumerContextUtils.getReleaseEntityId(Consumer.builder().context(Collections.emptyMap()).build()))
        .isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetReleaseEntityTypeNullSafe() {
    assertThat(ConsumerContextUtils.getReleaseEntityType(Consumer.builder().build())).isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetReleaseEntityIdNullSafe() {
    assertThat(ConsumerContextUtils.getReleaseEntityId(Consumer.builder().build())).isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetReleaseEntityType() {
    assertThat(ConsumerContextUtils.getReleaseEntityType(VALID_CONSUMER)).isEqualTo("STAGE");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetReleaseEntityId() {
    assertThat(ConsumerContextUtils.getReleaseEntityId(VALID_CONSUMER)).isEqualTo(RELEASE_ENTITY_ID);
  }
}
