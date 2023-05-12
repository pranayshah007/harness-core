/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACHYUTH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.ManifestType;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class K8sValuesFilesCommentsHandlerTest extends CategoryTest {
  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void shouldRemoveComments() {
    String commentOnly = "  # comment\r\n";
    String commentInSameLineAndNextLine = "  # defining name  \n"
        + "name: hello  # this is not a good name \r \n"
        + "# just adding some comments for testing \n"
        + "key: <+service.value>\n";
    String hashInValue = "baseurl: \"https://abc.xyz/#/api\" # shouldn't remove the hash in url";
    String nestedValues = "key: value\n"
        + "metadata:\n"
        + "  name: global-route # what is global route\n"
        + "  namespace: default";

    String op1 = "";
    String op2 = "name: hello\n"
        + "key: <+service.value>\n";
    String op3 = "baseurl: https://abc.xyz/#/api\n";
    String op4 = "key: value\n"
        + "metadata:\n"
        + "  name: global-route\n"
        + "  namespace: default\n";

    List<String> valuesFiles = asList(commentOnly, commentInSameLineAndNextLine, hashInValue, nestedValues);

    List<String> renderedValuesFiles =
        K8sValuesFilesCommentsHandler.removeComments(valuesFiles, ManifestType.K8Manifest);
    assertThat(renderedValuesFiles).isNotEmpty();
    assertThat(renderedValuesFiles).containsExactlyInAnyOrder(op1, op2, op3, op4);
  }
}
