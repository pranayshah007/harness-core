/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.yaml.schema.client.YamlSchemaClient;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class SchemaGetterFactoryTest {
  @Mock private Map<String, YamlSchemaClient> yamlSchemaClientMapper;
  @Mock YamlSchemaClient yamlSchemaClient;
  @InjectMocks SchemaGetterFactory schemaGetterFactory;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testObtainGetter() {
    assertTrue(schemaGetterFactory.obtainGetter("accountId", ModuleType.PMS) instanceof LocalSchemaGetter);
    assertThatThrownBy(() -> schemaGetterFactory.obtainGetter("accountId", ModuleType.CD))
        .isInstanceOf(IllegalStateException.class);
    doReturn(yamlSchemaClient).when(yamlSchemaClientMapper).get(ModuleType.CD.name().toLowerCase());
    assertTrue(schemaGetterFactory.obtainGetter("accountId", ModuleType.CD) instanceof RemoteSchemaGetter);
  }
}
