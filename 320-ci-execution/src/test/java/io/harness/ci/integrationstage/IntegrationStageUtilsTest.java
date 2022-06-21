/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class IntegrationStageUtilsTest {
  @Test
  @Category(UnitTests.class)
  public void getGitURLTestWithoutGitSuffix() throws Exception {
    String yamlNode =
        "{\"connectorRef\":\"git_3464\",\"repoName\":\"harness-core\",\"build\":{\"type\":\"branch\",\"spec\":{\"branch\":\"develop\",\"__uuid\":\"YtRST1sGTMyuLgNvJYsInw\"},\"__uuid\":\"Sh-Z7OKrQkeeg35DDI8tHQ\"},\"__uuid\":\"Yl_HajezQ4yOIRqE6xWZYQ\"}";
    CodeBase ciCodebase = YamlUtils.read(yamlNode, CodeBase.class);
    GitConnectionType connectionType = GitConnectionType.ACCOUNT;
    String url = "git@github.com:devkimittal";
    String actual = IntegrationStageUtils.getGitURL(ciCodebase, connectionType, url);
    String expected = "git@github.com:devkimittal/harness-core.git";
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Category(UnitTests.class)
  public void getGitURLTestWithGitSuffix() throws Exception {
    String yamlNode =
        "{\"connectorRef\":\"git_3464\",\"repoName\":\"harness-core.git\",\"build\":{\"type\":\"branch\",\"spec\":{\"branch\":\"develop\",\"__uuid\":\"YtRST1sGTMyuLgNvJYsInw\"},\"__uuid\":\"Sh-Z7OKrQkeeg35DDI8tHQ\"},\"__uuid\":\"Yl_HajezQ4yOIRqE6xWZYQ\"}";
    CodeBase ciCodebase = YamlUtils.read(yamlNode, CodeBase.class);
    GitConnectionType connectionType = GitConnectionType.ACCOUNT;
    String url = "git@github.com:devkimittal";
    String actual = IntegrationStageUtils.getGitURL(ciCodebase, connectionType, url);
    String expected = "git@github.com:devkimittal/harness-core.git";
    assertThat(actual).isEqualTo(expected);
  }
}
