/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.utilities;

import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ResourceConstraintUtilityTest extends PmsSdkCoreTestBase {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetResourceConstraintJsonNode() {
    String resourceConstraint = "test";
    JsonNode jsonNode = ResourceConstraintUtility.getResourceConstraintJsonNode(resourceConstraint);
    assertThat(JsonNodeUtils.getString(jsonNode, "name")).isEqualTo("Resource Constraint");
    assertThat(JsonNodeUtils.getString(jsonNode, "timeout")).isEqualTo("1w");
    assertThat(JsonNodeUtils.getString(jsonNode, "type")).isEqualTo("ResourceConstraint");
    assertThat(JsonNodeUtils.getMap(jsonNode, "spec").get("name").asText()).isEqualTo("Queuing");
    assertThat(JsonNodeUtils.getMap(jsonNode, "spec").get("resourceUnit").asText()).isEqualTo(resourceConstraint);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResourceConstraintHasStageScope() {
    JsonNode jsonNode = ResourceConstraintUtility.getResourceConstraintJsonNode("unitTest");
    assertThat(JsonNodeUtils.getMap(jsonNode, "spec").get("holdingScope").asText()).isEqualTo("STAGE");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResourceConstraintHasOnePermits() {
    JsonNode jsonNode = ResourceConstraintUtility.getResourceConstraintJsonNode("unitTest");
    assertThat(JsonNodeUtils.getMap(jsonNode, "spec").get("permits").asInt()).isEqualTo(1);
  }
}
