package io.harness.jira;

import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)

public class JiraClienttest extends CategoryTest {
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void getInstanceDatatTest() {
    JiraInternalConfig config = JiraInternalConfig.builder().jiraUrl("jiraUrl").username("username").build();
    JiraClient jiraClient = new JiraClient(config);

    assertThat(jiraClient.getInstanceData()).isEqualTo("string");
  }
}
