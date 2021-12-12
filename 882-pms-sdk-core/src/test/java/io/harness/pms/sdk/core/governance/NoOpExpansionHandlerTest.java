package io.harness.pms.sdk.core.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class NoOpExpansionHandlerTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExpand() {
    NoOpExpansionHandler noOpExpansionHandler = new NoOpExpansionHandler();
    ExpansionResponse expansionResponse = noOpExpansionHandler.expand(new TextNode("what"), null);
    assertThat(expansionResponse.isSuccess()).isTrue();
    assertThat(expansionResponse.getErrorMessage()).isNotNull();
    assertThat(expansionResponse.getKey()).isNotNull();
    assertThat(expansionResponse.getValue().toJson()).isNotNull();
    assertThat(expansionResponse.getPlacement()).isNotNull();
  }
}