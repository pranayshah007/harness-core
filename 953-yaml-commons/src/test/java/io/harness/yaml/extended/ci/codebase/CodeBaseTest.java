/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.extended.ci.codebase;

import static io.harness.rule.OwnerRule.ALEKSANDAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;

import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CodeBaseTest extends CategoryTest {
  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldDeserializeCodebase() throws IOException {
    String yaml = "connectorRef: springboot\n"
        + "repoName: abc\n"
        + "build:\n"
        + "  type: branch\n"
        + "  spec:\n"
        + "    branch: main";
    CodeBase actual = YamlPipelineUtils.read(yaml, CodeBase.class);
    assertThat(actual.getConnectorRef().getValue()).isEqualTo("springboot");
    assertThat(actual.getRepoName().getValue()).isEqualTo("abc");
    assertThat(actual.getBuild().getValue().getType()).isEqualTo(BuildType.BRANCH);
    assertThat(((BranchBuildSpec) actual.getBuild().getValue().getSpec()).getBranch().getValue()).isEqualTo("main");
  }
}
