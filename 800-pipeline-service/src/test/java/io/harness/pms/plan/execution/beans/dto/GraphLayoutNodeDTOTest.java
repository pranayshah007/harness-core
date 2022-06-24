package io.harness.pms.plan.execution.beans.dto;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GraphLayoutNodeDTOTest extends CategoryTest {
  /**
   * If this fails, either update the counter or add an update.set in the linked function
   * { @link PmsExecutionSummaryServiceImpl#cloneGraphLayoutNodeDtoWithModifications graphLayoutNode }
   */
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testIfNewFieldsAreAdded() {
    assertThat(ReflectionUtils.getAllDeclaredAndInheritedFields(GraphLayoutNodeDTO.class).size())
        .isEqualTo(20)
        .withFailMessage("You have added a new field in GraphLayoutNodeDTO. If you are not "
            + "updating it anywhere after creating then please update PmsExecutionSummaryService#cloneGraphLayoutNodeDtoWithModifications");
  }
}