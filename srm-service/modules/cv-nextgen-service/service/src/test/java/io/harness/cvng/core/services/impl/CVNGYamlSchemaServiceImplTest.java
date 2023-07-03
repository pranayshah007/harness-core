/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.services.api.CVNGYamlSchemaService;
import io.harness.encryption.Scope;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.YamlSdkConfiguration;
import io.harness.yaml.YamlSdkInitHelper;
import io.harness.yaml.schema.beans.PartialSchemaDTO;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CVNGYamlSchemaServiceImplTest extends CvNextGenTestBase {
  @Inject private CVNGYamlSchemaService cvngYamlSchemaService;
  @Inject private Injector injector;

  @Before
  public void before() {
    YamlSdkConfiguration yamlSdkConfiguration = YamlSdkConfiguration.builder()
                                                    .requireSchemaInit(true)
                                                    .requireSnippetInit(false)
                                                    .requireValidatorInit(false)
                                                    .build();
    YamlSdkInitHelper.initialize(injector, yamlSdkConfiguration);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  @Ignore("Skipping for now as this flow is not used")
  public void testGetDeploymentStageYamlSchema() throws IOException {
    List<PartialSchemaDTO> partialSchemaDTO =
        cvngYamlSchemaService.getDeploymentStageYamlSchema(generateUuid(), generateUuid(), Scope.PROJECT);
    // https://harness.atlassian.net/browse/CDNG-7666
    // Currently the schema has details of all the steps that are visible to CVNG module.
    // This is because of the dependency on PMS module. Above ticket is tracking the refactoring of it.
    String partialSchemaJson = Resources.toString(
        CVNGYamlSchemaServiceImplTest.class.getResource("/schema/DeploymentSteps/partialSchemaDTO.json"),
        Charsets.UTF_8);
    // String s = JsonUtils.asPrettyJson(partialSchemaDTO); // s -- string to be pasted in the .json file
    PartialSchemaDTO expectedPartialSchemaDTO = JsonUtils.asObject(partialSchemaJson, PartialSchemaDTO.class);
    assertThat(partialSchemaDTO.get(0)).isEqualTo(expectedPartialSchemaDTO);
  }
}
