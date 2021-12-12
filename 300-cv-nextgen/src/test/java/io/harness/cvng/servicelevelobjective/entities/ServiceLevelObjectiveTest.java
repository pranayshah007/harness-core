package io.harness.cvng.servicelevelobjective.entities;

import static io.harness.rule.OwnerRule.KAMAL;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceLevelObjectiveTest {
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCurrentTimeRange() {}
}