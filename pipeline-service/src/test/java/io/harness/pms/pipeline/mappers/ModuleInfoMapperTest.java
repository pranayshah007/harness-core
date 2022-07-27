/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ModuleInfoMapperTest {
  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetModuleInfo() {
    Map<String, LinkedHashMap<String, Object>> moduleInfo = ModuleInfoMapper.getModuleInfo(null);
    assertThat(moduleInfo).isEmpty();

    moduleInfo = ModuleInfoMapper.getModuleInfo(new HashMap<>());
    assertThat(moduleInfo).isEmpty();

    Map<String, Document> moduleInfoDocumentMap = new LinkedHashMap<>();
    moduleInfoDocumentMap.put("cd", null);
    moduleInfo = ModuleInfoMapper.getModuleInfo(moduleInfoDocumentMap);
    assertThat(moduleInfo).hasSize(1);
    assertThat(moduleInfo.get("cd")).isEmpty();

    Document documentMap = new Document();
    moduleInfoDocumentMap.put("cd", documentMap);
    moduleInfo = ModuleInfoMapper.getModuleInfo(moduleInfoDocumentMap);
    assertThat(moduleInfo).hasSize(1);
    assertThat(moduleInfo.get("cd")).isEmpty();

    documentMap.put("serviceId", Arrays.asList("s1, s2"));
    moduleInfo = ModuleInfoMapper.getModuleInfo(moduleInfoDocumentMap);
    assertThat(moduleInfo).hasSize(1);
    assertThat(moduleInfo.get("cd")).hasSize(1);
    assertThat(moduleInfo.get("cd").get("serviceId")).isEqualTo(Arrays.asList("s1, s2"));

    moduleInfoDocumentMap.put("pms", documentMap);
    moduleInfo = ModuleInfoMapper.getModuleInfo(moduleInfoDocumentMap);
    assertThat(moduleInfo).hasSize(2);
    assertThat(moduleInfo.get("cd")).hasSize(1);
    assertThat(moduleInfo.get("pms")).hasSize(1);
  }
}
