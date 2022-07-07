/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filters;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ConnectorRefExtractorHelperTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testAddReference() {
    ConnectorRefExtractorHelper connectorRefExtractorHelper = new ConnectorRefExtractorHelper();
    TestObjectWithConnectorRef testObjectWithConnectorRef =
        TestObjectWithConnectorRef.builder().returnRuntimeInput(true).build();
    String accountId = "acc";
    String orgId = "org";
    String projectId = "proj";
    Map<String, Object> contextMap = Collections.emptyMap();
    Set<EntityDetailProtoDTO> entityDetailProtoDTOS =
        connectorRefExtractorHelper.addReference(testObjectWithConnectorRef, accountId, orgId, projectId, contextMap);
    assertThat(entityDetailProtoDTOS).hasSize(1);
    assertThat(new ArrayList<>(entityDetailProtoDTOS).get(0).getIdentifierRef().getIdentifier().getValue())
        .isEqualTo("<+input>");
  }
}
