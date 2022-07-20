package io.harness.waiter;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PmsNotifyEventConsumerRedisTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateEventConsumerRedis() {
    PmsNotifyEventConsumerRedis pmsNotifyEventConsumerRedis = new PmsNotifyEventConsumerRedis(null, null, null, null);
    assertThat(pmsNotifyEventConsumerRedis).isNotEqualTo(null);
  }
}
