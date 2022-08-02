/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.rule.Owner;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSYamlSchemaServiceImplTest {
  @Mock private SchemaFetcher schemaFetcher;
  @InjectMocks private PMSYamlSchemaServiceImpl pmsYamlSchemaService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFetchSchemaWithDetailsFromModules() {
    ModuleType moduleType = ModuleType.CD;
    YamlSchemaMetadata yamlSchemaMetadata =
        YamlSchemaMetadata.builder().yamlGroup(YamlGroup.builder().group("step").build()).build();
    doReturn(
        YamlSchemaDetailsWrapper.builder()
            .yamlSchemaWithDetailsList(Collections.singletonList(
                YamlSchemaWithDetails.builder().moduleType(moduleType).yamlSchemaMetadata(yamlSchemaMetadata).build()))
            .build())
        .when(schemaFetcher)
        .fetchSchemaDetail(any(), any());
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList =
        pmsYamlSchemaService.fetchSchemaWithDetailsFromModules("accountId", Collections.singletonList(moduleType));

    assertEquals(yamlSchemaWithDetailsList.get(0).getYamlSchemaMetadata(), yamlSchemaMetadata);
    assertEquals(yamlSchemaWithDetailsList.get(0).getModuleType(), moduleType);
  }
}
