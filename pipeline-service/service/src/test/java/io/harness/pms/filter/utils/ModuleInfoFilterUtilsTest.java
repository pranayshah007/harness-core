/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.filter.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public class ModuleInfoFilterUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void processNode() throws IOException {
    String json =
        "{\"ci\":{},\"cd\":{\"identifier\": \"p1\", \"serviceDefinitionTypes\":[[{\"label\":\"Kubernetes\",\"value\":\"Kubernetes\"}]]}}";
    YamlField yamlField = YamlUtils.readTree(json);
    assertThat(yamlField).isNotNull();
    Criteria criteria = new Criteria();
    ModuleInfoFilterUtils.processNode(yamlField.getNode().getCurrJsonNode(), "topKey", criteria);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject).hasSize(2);
    assertThat(criteriaObject.get("topKey.cd.identifier")).isEqualTo("p1");
    assertThat(criteriaObject.containsKey("topKey.cd.serviceDefinitionTypes")).isTrue();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void processNodeEmptyArray() throws IOException {
    String json = "{\"ci\":{},\"cd\":{\"serviceIdentifiers\":[],\"envIdentifiers\":[\"testcluster\"]}}}";
    YamlField yamlField = YamlUtils.readTree(json);
    assertThat(yamlField).isNotNull();
    Criteria criteria = new Criteria();
    ModuleInfoFilterUtils.processNode(yamlField.getNode().getCurrJsonNode(), "topKey", criteria);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject).hasSize(1);
    assertThat(criteriaObject.containsKey("topKey.cd.serviceIdentifiers")).isFalse();
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get("topKey.cd.envIdentifiers")).get("$in")).size()).isEqualTo(1);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get("topKey.cd.envIdentifiers")).get("$in")).get(0))
        .isEqualTo("testcluster");
  }
}
