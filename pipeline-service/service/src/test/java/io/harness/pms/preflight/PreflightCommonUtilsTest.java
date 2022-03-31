/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PreflightCommonUtilsTest extends CategoryTest {
  Map<String, Object> fqnToObjectMapMergedYaml = new HashMap<>();

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    String filename = "failure-strategy.yaml";
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    Map<FQN, Object> fqnObjectMap =
        FQNMapGenerator.generateFQNMap(YamlUtils.readTree(yaml).getNode().getCurrJsonNode());
    fqnObjectMap.keySet().forEach(fqn -> fqnToObjectMapMergedYaml.put(fqn.getExpressionFqn(), fqnObjectMap.get(fqn)));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStageName() {
    assertThat(PreflightCommonUtils.getStageName(fqnToObjectMapMergedYaml, null)).isNull();
    assertThat(PreflightCommonUtils.getStageName(fqnToObjectMapMergedYaml, "qaStage")).isEqualTo("qa stage");
  }
}
