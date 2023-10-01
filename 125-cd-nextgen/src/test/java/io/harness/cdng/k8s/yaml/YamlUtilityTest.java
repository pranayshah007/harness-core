/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.yaml;

import static io.harness.cdng.k8s.yaml.YamlUtility.REDACTED_BY_HARNESS;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class YamlUtilityTest extends CategoryTest {
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsMultiCases2() {
    InputAndExpectedYaml inputAndExpectedYaml =
        new InputAndExpectedYaml().append("id: \"0\" ").append("# block comment\n");
    assertRemoveComments(inputAndExpectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsMultiCases() {
    InputAndExpectedYaml inputAndExpectedYaml = new InputAndExpectedYaml()
                                                    // clang-format off
          .append("# this is a block comment\n")
          .append("id: \"0\"\n")
          .append("boolean: \"true\" ").append("# inline comment .*\n")
          .append("<+pipeline.variables.test>\n")
          .append("<+pipeline.variables.key>: <+pipeline.variables.value>\n")
          .append("<pipeline.variables.expr>: value ").append("#simple comment with key expression\n")
          .append("items: ").append("# comment for list start\n")
          .append("- itemInline ").append("# inline comment\n")
          .append("- ").append("# item comment\n")
          .append("  itemValue\n")
          .append("- itemPlain\n")
          .append("itemsWithIndent:\n")
          .append("\t- itemInline ").append("# inline comment\n")
          .append("\t- ").append("# item comment\n")
          .append("\t  itemValue\n")
          .append(" key: value      ").append("# inline comment with space indent\n")
          .append("secondKey: value\t\t\t").append("# inline comment with tab indent\n")
          .append("      ").append("# block comment with space indent\n")
          .append("\t\t\t").append("# block comment with tab indent\n");
    // clang-format on
    assertRemoveComments(inputAndExpectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsPrimitiveTypes() {
    InputAndExpectedYaml inputAndExpectedYaml = new InputAndExpectedYaml()
                                                    // clang-format off
          .append("variable: <+service.variables.test>\n")
          .append("simple: \"value \" ").append("# inline comment\n")
          .append("# block comment\n")
          .append("booleanValue: true ").append("# inline comment\n")
          .append("booleanAsString: \"true\" ").append("# inline comment\n")
          .append("booleanAsStringSingle: 'true' ").append("# inline comment\n")
          .append("intValue: 100 ").append("# inline comment\n")
          .append("intAsString: \"100\" ").append("# inline comment\n")
          .append("intAsStringSingle: '100' ").append("# inline comment\n")
          .append("doubleValue: 0.42 ").append("# inline comment\n")
          .append("doubleAsString: \"0.42\" ").append("# inline comment\n")
          .append("doubleAsStringSingle: '0.42' ").append("# inline comment\n")
          .append("stringUrl: https://harness.io/#/api ").append("# inline comment\n")
          .append("stringUrl2: https://harness.io/# ").append("# inline comment\n");
    // clang-format on
    assertRemoveComments(inputAndExpectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsMap() {
    InputAndExpectedYaml inputAndExpectedYaml =
        new InputAndExpectedYaml()
            // clang-format off
          .append("root:\n")
          .append("  key: \"multine line value \ncontinuation of multiline value\" ").append("# inline comment\n")
          .append("  nested: ").append("# inline comment\n")
          .append("  ").append("# block comment\n")
          .append("    key: 3 ").append("# inline comment\n")
          .append("    nested:\n")
          .append("      key: \"value\" ").append("# inline comment\n");
    // clang-format on
    assertRemoveComments(inputAndExpectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsImpostorComment() {
    InputAndExpectedYaml inputAndExpectedYaml =
        new InputAndExpectedYaml()
            // clang-format off
          .append("stringWitImpostor: \"string with # not a comment\" ").append("# the actual comment\n")
          .append("singleWithImpostor: 'single quote with # not a comment'\n")
          .append("nested: ").append("# inline comment\n")
          .append("  multilineWithImpostor: \"multiline with # not a comment\n\t# and this is still not a comment\"\n")
          .append("  multilineSingleQuote: 'single quote with # not a comment\n# still not a comment' ").append("# actual comment\n")
          .append("singleImpostor: \"double quotes with '# single comment' and just ' # before comment\"\n");
    // clang-format on
    assertRemoveComments(inputAndExpectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsAnchorAndNoComments() {
    StringBuilder yaml = new StringBuilder()
                             .append("anchor: &def-anchor\n")
                             .append("\tkey1: value1\n")
                             .append("\tkey2: value2\n")
                             .append("anchorRef:\n")
                             .append("\t<<: *def-anchor\n")
                             .append("\tappendedKey: value3");

    // There shouldn't be any changes as no comments used
    assertThat(YamlUtility.removeComments(yaml.toString())).isEqualTo(yaml.toString());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsCustomerCase1() {
    final String yaml = "app_lane_id: \"<+matrix.lane_ids>\"\n"
        + "\n"
        + "deployment:\n"
        + "  container:\n"
        + "    image:\n"
        + "      tag: <+artifacts.primary.tag>";

    // There shouldn't be any changes as no comments used
    assertThat(YamlUtility.removeComments(yaml)).isEqualTo(yaml);
  }
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsCustomerCase2() {
    final String yaml = "appName: <+pipeline.variables.app_name>\n"
        + "aws_account: <+pipeline.variables.aws_account>\n"
        + "ecr_image: <+pipeline.variables.aws_account>.dkr.ecr.us-east-1.amazonaws.com/<+pipeline.variables.ecr_image_name>:<+pipeline.variables.ecr_image_tag>\n"
        + "containerPort: \"<+pipeline.variables.container_port>\"\n"
        + "subnets: \"subnet-00073337b1e958f59, subnet-031618ba2b17a4bf3, subnet-09976268adc51abbc, subnet-0988944e44cdee657\"\n"
        + "route53_hosted_zone: \"kafka.aws-<+pipeline.variables.target_env>.capgroup.com\"\n"
        + "java_opts: \"<+pipeline.stages.Deploy.spec.execution.steps.Nomos.output.outputVariables.pod_java_opts_ext>\"\n"
        + "pod_replicas: <+pipeline.variables.pod_replicas>\n"
        + "keystore_base64: <+pipeline.stages.Deploy.spec.execution.steps.Nomos.output.outputVariables.keystore_jks_base64_string>\n"
        + "keystore_mount_path: /home/gradle/cert\n"
        + "active_spring_boot_profile: <+pipeline.variables.pod_active_spring_boot_profile>\n"
        + "truststore_location_property: <+pipeline.variables.pod_truststore_location_property>\n"
        + "keystore_location_property: <+pipeline.variables.pod_keystore_location_property>\n"
        + "truststore_password_property: <+pipeline.variables.pod_truststore_password_property>\n"
        + "keystore_password_property: <+pipeline.variables.pod_keystore_password_property>\n"
        + "key_password_property: <+pipeline.variables.pod_key_password_property>\n"
        + "truststore_password: <+pipeline.stages.Deploy.spec.execution.steps.Nomos.output.outputVariables.keystore_jks_secret>\n"
        + "keystore_password: <+pipeline.stages.Deploy.spec.execution.steps.Nomos.output.outputVariables.keystore_jks_secret>\n"
        + "confluent_cloud_keys_base64: \"<+pipeline.stages.Deploy.spec.execution.steps.Nomos.output.outputVariables.confluent_cloud_keys>\"\n"
        + "keystore_cn_name: \"<+pipeline.stages.Deploy.spec.execution.steps.Nomos.output.outputVariables.keystore_cn_name>\"\n"
        + "certificate_arn: \"arn:aws:acm:us-east-1:<+pipeline.variables.aws_account>:certificate/<+pipeline.variables.ingress_tls_certificate_arn>\"\n"
        + "confluent_cloud_apikeys: <+pipeline.variables.cckey_path>\n"
        + "confluent_cloud_okta_client_secret: <+pipeline.variables.confluent_cloud_okta_client_secret>";

    // There shouldn't be any changes as no comments used
    assertThat(YamlUtility.removeComments(yaml)).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsCustomerCase3() {
    final InputAndExpectedYaml inputAndExpectedYaml =
        new InputAndExpectedYaml()
            // clang-format off
          .append("vmr:\n")
          .append("  ").append("# vmr.host: To be stored in Terraform Output\n")
          .append("  host: <+serviceVariables.VMR_HOST>\n")
          .append("  ").append("# namespace, \"${maas_id>-<+maas_instance_id}\"\n")
          .append("  ").append("# username: <+infra.namespace> #maas in non-terraform cloudclusters\n")
          .append("  ").append("# vmr.vpnname\n")
          .append("  vpnname: <+infra.namespace>");
    // clang-format on
    assertRemoveComments(inputAndExpectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsJsonCompatibility() {
    final String json = "{\n"
        + "\"key\": \"value\",\n"
        + "\"list\": [ \"item1\", \"item2\" ],\n"
        + "\"object\": { \"key\": \"value\" }\n"
        + "}";

    // There shouldn't be any changes as no comments used
    assertThat(YamlUtility.removeComments(json)).isEqualTo(json);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsYamlWithJson() {
    final InputAndExpectedYaml inputAndExpectedYaml = new InputAndExpectedYaml()
                                                          // clang-format off
          .append("list: [\"item1\", \"item2\", \"item4\"] ").append("# inline comment\n")
          .append("object: { ").append("# inline comment\n")
          .append("# block comment\n")
          .append("key: value, ").append("# inline comment\n")
          .append("list: [\"item1\"] ").append("# inline comment\n")
          .append("}\n");
    // clang-format on

    assertRemoveComments(inputAndExpectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsWindowsNewLine() {
    final InputAndExpectedYaml inputAndExpectedYaml = new InputAndExpectedYaml()
                                                          // clang-format off
          .append("# block comment\r\n")
          .append("<+pipeline.variables.test> ").append("# inline comment with variable\r\n")
          .append("<+pipeline.variables.objectKey>: ").append("# inline comment with object\r\n")
          .append("\t").append("# simple block comment\r\n")
          .append("\tkey: ").append("# nested comment\r\n")
          .append("\t\titems: [\"item1\"] ").append("# comment for list\r\n")
          .append("\r\n");
    // clang-format on
    assertRemoveComments(inputAndExpectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsQuoted() {
    final InputAndExpectedYaml inputAndExpectedYaml = new InputAndExpectedYaml()
                                                          // clang-format off
          .append("\"double\": \"<+pipeline.variables.test>\"\n")
          .append("'single': '<+variables.test>'\n")
          .append("\"not#comment\": \"value\" ").append("# simple comment\n")
          .append("'single#not#comment': \"value#not comment\"\n");
    // clang-format on

    assertRemoveComments(inputAndExpectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsMultilineString() {
    final InputAndExpectedYaml inputAndExpectedYaml = new InputAndExpectedYaml()
                                                          // clang-format off
            .append("multiline: |- ").append("# inline comment\n")
            .append("  string value # with not a comment\n")
            .append("  another value\n")
            .append("  # comment as part of the value\n")
            .append("  <+variable.test> # not comment <+variable.test>\n")
            .append("# actual comment\n")
            .append("multiline2:|").append("# comment\n")
            .append("    string value with #\n")
            .append("    not # comment comment\n")
            .append("<+variables.test> ").append("# comment\n")
            .append("folded:>").append("# comment\n")
            .append("  string value with # not a comment\n")
            .append("\n")
            .append("  continuation of # not a comment\n")
            .append("key: value ").append("# actual comment\n");
    // clang-format on

    assertRemoveComments(inputAndExpectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsMultilineStringIndentIndicators() {
    final InputAndExpectedYaml inputAndExpectedYaml = new InputAndExpectedYaml()
                                                          .append("indicator: |1 ")
                                                          .append("# comment\n")
                                                          .append(" test string # not a comment\n")
                                                          .append("  test string # not a comment\n")
                                                          .append("stop: true ")
                                                          .append("# a comment\n")
                                                          .append("nested: ")
                                                          .append("# a comment\n")
                                                          .append("  indicator: |3\n")
                                                          .append("     This is a string # not a comment\n")
                                                          .append("        Another string and # not a comment\n")
                                                          .append("     Just one more string and # not a comment\n")
                                                          .append("    Not Part of the string and ")
                                                          .append("# a comment");

    assertRemoveComments(inputAndExpectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveCommentsMultilineNestedTest() {
    final InputAndExpectedYaml inputAndExpectedYaml =
        new InputAndExpectedYaml()
            .append("root:\n")
            .append("\tlevel1:\n")
            .append("\t\tkey:>\n")
            .append("\t\t\tthis is a text value without # comment\n")
            .append("\t\tlevel2:\n")
            .append("\t\t\tliteral: |1\n")
            .append("\t\t\t\tthis is a text value without # comment\n")
            .append("\t\t\t\tthis is a another text value without # comment\n")
            .append("\t\t\tfolded:>2\n")
            .append("\t\t\t  this is a folded text without # comment\n")
            .append("\t\t\t  second line still no # comment\n")
            .append("\t\t\t ")
            .append("# comment\n")
            .append("\t\t\tlevel3:\n")
            .append("\t\t\t\tliteral: |3+\n")
            .append("\t\t\t\t   Text value from values.yaml # no comment\n")
            .append("\t\t\t\t   second line with # no comment\n")
            .append("\t\t\t\t")
            .append("# comment after literal\n")
            .append("\t\t\t\tfolded: >4-")
            .append("# comment folded key\n")
            .append("\t\t\t\t    This is a text value # no comment\n")
            .append("\t\t\t\t   ")
            .append("# comment after folded\n")
            .append("literal:|1-")
            .append("# comment\n")
            .append(" this is a # string and yaml end");

    assertRemoveComments(inputAndExpectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testNegativeCaseSet1() {
    String text = "not an actual yaml";
    String multiline = "This is an multiline text\nWith the second line\nNothing related to yaml";
    String yamlWithMultipleNewLines = "key:   value\n\n\n\n\n\n\n\n\n\n\n\nkey: value\n\n\n\n\n";
    String emptyTextWithNewLines = "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n";

    assertRemoveComments(text, text);
    assertRemoveComments(multiline, multiline);
    assertRemoveComments(yamlWithMultipleNewLines, yamlWithMultipleNewLines);
    assertRemoveComments(emptyTextWithNewLines, emptyTextWithNewLines);
    assertRemoveComments(null, null);
    assertRemoveComments("", "");
  }

  private void assertRemoveComments(InputAndExpectedYaml inputAndExpectedYaml) {
    assertRemoveComments(inputAndExpectedYaml.getInputYaml(), inputAndExpectedYaml.getExpectedYaml());
  }

  private void assertRemoveComments(String inputYaml, String expectedYaml) {
    String finalValue = YamlUtility.removeComments(inputYaml);
    assertThat(finalValue).isEqualTo(expectedYaml);
  }

  private static final class InputAndExpectedYaml {
    private final StringBuilder inputYaml = new StringBuilder();
    private final StringBuilder expectedYaml = new StringBuilder();

    public InputAndExpectedYaml append(String value) {
      if (value.charAt(0) == '#') {
        int lineBreakIndex = value.lastIndexOf('\r');
        if (lineBreakIndex == -1) {
          lineBreakIndex = value.lastIndexOf('\n');
        }

        expectedYaml.append("# ").append(REDACTED_BY_HARNESS);
        if (lineBreakIndex != -1) {
          expectedYaml.append(value.substring(lineBreakIndex));
        }

      } else {
        expectedYaml.append(value);
      }

      inputYaml.append(value);

      return this;
    }

    public String getInputYaml() {
      return inputYaml.toString();
    }

    public String getExpectedYaml() {
      return expectedYaml.toString();
    }
  }
}