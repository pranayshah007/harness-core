/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filters;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class GenericEntityRefExtractorHelperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String CREDENTIALS_REF = "credentialsRef";

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testAddReference() {
    GenericEntityRefExtractorHelper genericEntityRefExtractorHelper = new GenericEntityRefExtractorHelper();
    TestObjectWithConnectorRef testObjectWithConnectorRef = TestObjectWithConnectorRef.builder().build();
    Map<String, Object> contextMap = Collections.emptyMap();
    Set<EntityDetailProtoDTO> connectorDetailProtoDTOS = genericEntityRefExtractorHelper.addReference(
        testObjectWithConnectorRef, ACCOUNT_ID, ORG_ID, PROJECT_ID, contextMap);
    assertThat(connectorDetailProtoDTOS).hasSize(1);
    assertThat(new ArrayList<>(connectorDetailProtoDTOS).get(0).getIdentifierRef().getIdentifier().getValue())
        .isEqualTo("connectorRef");

    TestObjectWithSecretRef testObjectWithSecretRef =
        TestObjectWithSecretRef.builder().credentialsRef(ParameterField.createValueField(CREDENTIALS_REF)).build();
    Set<EntityDetailProtoDTO> secretDetailProtoDTOS = genericEntityRefExtractorHelper.addReference(
        testObjectWithSecretRef, ACCOUNT_ID, ORG_ID, PROJECT_ID, contextMap);
    assertThat(secretDetailProtoDTOS).hasSize(1);
    assertThat(new ArrayList<>(secretDetailProtoDTOS).get(0).getIdentifierRef().getIdentifier().getValue())
        .isEqualTo(CREDENTIALS_REF);
  }
}
