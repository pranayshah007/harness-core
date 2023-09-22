/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class FQNHelperTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetIdentifierKeyIfPresent() {
    String fieldYaml = "step:\n"
        + "  type: Http\n"
        + "  name: Http_1\n"
        + "  identifier: Http_1\n"
        + "  spec:\n"
        + "    url: https://www.google.com\n"
        + "    method: GET\n"
        + "  timeout: 30s";
    assertThat(FQNHelper.getIdentifierKeyIfPresent(YamlUtils.readAsJsonNode(fieldYaml)))
        .isEqualTo(YAMLFieldNameConstants.IDENTIFIER);

    fieldYaml = "step:\n"
        + "  type: Http\n"
        + "  name: Http_1\n"
        + "  id: Http_1\n"
        + "  spec:\n"
        + "    url: https://www.google.com\n"
        + "    method: GET\n"
        + "  timeout: 30s";
    assertThat(FQNHelper.getIdentifierKeyIfPresent(YamlUtils.readAsJsonNode(fieldYaml)))
        .isEqualTo(YAMLFieldNameConstants.ID);

    fieldYaml = "parallel:\n"
        + "  - step:\n"
        + "      type: Http\n"
        + "      name: Http_1\n"
        + "      identifier: Http_1\n"
        + "      spec:\n"
        + "        url: https://www.google.com\n"
        + "        method: GET\n"
        + "      timeout: 30s\n"
        + "  - step:\n"
        + "      type: Http\n"
        + "      name: Http_1\n"
        + "      identifier: Http_1\n"
        + "      spec:\n"
        + "        url: https://www.google.com\n"
        + "        method: GET\n"
        + "      timeout: 30s";
    assertThat(FQNHelper.getIdentifierKeyIfPresent(YamlUtils.readAsJsonNode(fieldYaml)))
        .isEqualTo(YAMLFieldNameConstants.PARALLEL);

    fieldYaml = "step:\n"
        + "  type: Http\n"
        + "  name: Http_1\n"
        + "  spec:\n"
        + "    url: https://www.google.com\n"
        + "    method: GET\n"
        + "  timeout: 30s";
    assertThat(FQNHelper.getIdentifierKeyIfPresent(YamlUtils.readAsJsonNode(fieldYaml))).isNull();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetUuidKey() {
    String nodeYaml = "type: Http\n"
        + "id: Http_1\n"
        + "timeout: 10s\n"
        + "spec:\n"
        + "  method: GET\n"
        + "  url: https://www.google.com\n";
    assertThat(FQNHelper.getUuidKey(YamlUtils.readAsJsonNode(nodeYaml))).isEqualTo(YAMLFieldNameConstants.ID);

    nodeYaml = "type: Http\n"
        + "timeout: 10s\n"
        + "name: Http_1\n"
        + "spec:\n"
        + "  method: GET\n"
        + "  url: https://www.google.com\n";
    assertThat(FQNHelper.getUuidKey(YamlUtils.readAsJsonNode(nodeYaml))).isEqualTo(YAMLFieldNameConstants.NAME);

    nodeYaml = "type: Http\n"
        + "identifier: Http_1\n"
        + "timeout: 10s\n"
        + "spec:\n"
        + "  method: GET\n"
        + "  url: https://www.google.com\n";
    assertThat(FQNHelper.getUuidKey(YamlUtils.readAsJsonNode(nodeYaml))).isEqualTo(YAMLFieldNameConstants.IDENTIFIER);

    nodeYaml = "type: Http\n"
        + "timeout: 10s\n"
        + "spec:\n"
        + "  method: GET\n"
        + "  url: https://www.google.com\n";
    assertThat(FQNHelper.getUuidKey(YamlUtils.readAsJsonNode(nodeYaml))).isEmpty();
  }
}
