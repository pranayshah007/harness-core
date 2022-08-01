/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filters;

import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.SecretRefData;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class FilterCreatorHelperTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectId";
  private static final String FQN = "first.last";

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testConvertToEntityDetailProtoDTO() {
    EntityDetailProtoDTO entityDetailProtoDTO =
        FilterCreatorHelper.convertToEntityDetailProtoDTO(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FQN,
            ParameterField.createValueField("connectorId"), EntityTypeProtoEnum.CONNECTORS);
    Map<String, String> metadata = new HashMap<>(Collections.singletonMap(PreFlightCheckMetadata.FQN, FQN));
    assertThat(entityDetailProtoDTO.getIdentifierRef().getMetadata()).isEqualTo(metadata);

    assertThat(entityDetailProtoDTO.getIdentifierRef().getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(entityDetailProtoDTO.getIdentifierRef().getOrgIdentifier().getValue()).isEqualTo(ORG_IDENTIFIER);
    assertThat(entityDetailProtoDTO.getIdentifierRef().getProjectIdentifier().getValue()).isEqualTo(PROJECT_IDENTIFIER);
    assertThat(entityDetailProtoDTO.getIdentifierRef().getIdentifier().getValue()).isEqualTo("connectorId");

    assertThatThrownBy(
        ()
            -> FilterCreatorHelper.convertToEntityDetailProtoDTO(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
                FQN, ParameterField.createValueField(""), EntityTypeProtoEnum.CONNECTORS))
        .isInstanceOf(InvalidYamlRuntimeException.class)
        .hasMessage("Connector ref is not present for property: first.last");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testConvertToEntityDetailProtoDTOWithExpression() {
    EntityDetailProtoDTO entityDetailProtoDTO =
        FilterCreatorHelper.convertToEntityDetailProtoDTO(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FQN,
            ParameterField.createExpressionField(true, "connectorId", null, true), EntityTypeProtoEnum.CONNECTORS);
    Map<String, String> metadata = new HashMap<>(Collections.singletonMap(PreFlightCheckMetadata.FQN, FQN));
    metadata.put(PreFlightCheckMetadata.EXPRESSION, "connectorId");

    assertThat(entityDetailProtoDTO.getIdentifierRef().getMetadata()).isEqualTo(metadata);

    assertThat(entityDetailProtoDTO.getIdentifierRef().getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(entityDetailProtoDTO.getIdentifierRef().getOrgIdentifier().getValue()).isEqualTo(ORG_IDENTIFIER);
    assertThat(entityDetailProtoDTO.getIdentifierRef().getProjectIdentifier().getValue()).isEqualTo(PROJECT_IDENTIFIER);
    assertThat(entityDetailProtoDTO.getIdentifierRef().getIdentifier().getValue()).isEqualTo("connectorId");

    assertThatThrownBy(
        ()
            -> FilterCreatorHelper.convertToEntityDetailProtoDTO(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
                FQN, ParameterField.createExpressionField(true, "", null, true), EntityTypeProtoEnum.CONNECTORS))
        .isInstanceOf(InvalidYamlRuntimeException.class)
        .hasMessage("Connector ref is not present for property: first.last");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEmptySecretRef() {
    SecretRefData secretRefData = new SecretRefData("");
    assertThatThrownBy(
        ()
            -> FilterCreatorHelper.convertSecretToEntityDetailProtoDTO(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
                PROJECT_IDENTIFIER, FQN, ParameterField.<SecretRefData>builder().value(secretRefData).build()))
        .hasMessage("No value for secret provided at first.last");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCheckIfNameIsJexlKeyword() throws IOException {
    String notJexl = "name: notJexl\n"
        + "type: String\n"
        + "value: notJexl";
    String jexl = "name: or\n"
        + "type: String\n"
        + "value: notJexl";

    YamlField notJexlYamlField = YamlUtils.readTree(notJexl);
    FilterCreatorHelper.checkIfNameIsJexlKeyword(notJexlYamlField.getNode());

    YamlField jexlYamlField = YamlUtils.readTree(jexl);
    assertThatThrownBy(() -> FilterCreatorHelper.checkIfNameIsJexlKeyword(jexlYamlField.getNode()))
        .isInstanceOf(InvalidYamlRuntimeException.class)
        .hasMessage("Variable name or is a jexl reserved keyword");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCheckIfVariableNamesAreValid() throws IOException {
    String variables = "- name: notJexl\n"
        + "  type: String\n"
        + "  value: notJexl\n"
        + "- name: or\n"
        + "  type: String\n"
        + "  value: notJexl\n";
    YamlField variablesYamlField = YamlUtils.readTree(variables);
    assertThatThrownBy(() -> FilterCreatorHelper.checkIfVariableNamesAreValid(variablesYamlField))
        .isInstanceOf(InvalidYamlRuntimeException.class)
        .hasMessage("Variable name or is a jexl reserved keyword");
  }
}
