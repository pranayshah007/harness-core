/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.cdng.beans.CVNGStepType;
import io.harness.cvng.cdng.services.api.CDStageMetaDataService;
import io.harness.cvng.client.RequestExecutor;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;

public class CDStageMetaDataServiceImplTest extends CvNextGenTestBase {
  @Inject CDStageMetaDataService cdStageMetaDataService;
  @Mock RequestExecutor requestExecutor;

  private ResponseDTO<CDStageMetaDataDTO> responseDTO;

  @Before
  public void setup() throws IllegalAccessException {
    FieldUtils.writeField(cdStageMetaDataService, "requestExecutor", requestExecutor, true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetServiceAndEnvironmentRef_withValid() {
    YamlField yamlField = getVerifyStepYamlField("pipeline/pipeline-with-verify.yaml");
    responseDTO = ResponseDTO.newResponse(
        CDStageMetaDataDTO.builder().serviceRef("serviceIdentifier").environmentRef("envIdentifier").build());
    Mockito.when(requestExecutor.execute(any())).thenReturn(responseDTO);
    ResponseDTO<CDStageMetaDataDTO> result = cdStageMetaDataService.getServiceAndEnvironmentRef(
        yamlField.getNode().getParentNode().getParentNode().getParentNode().getParentNode());
    assertThat(result.getData().getServiceRef()).isEqualTo("serviceIdentifier");
    assertThat(result.getData().getEnvironmentRef()).isEqualTo("envIdentifier");
  }

  @SneakyThrows
  public YamlField getVerifyStepYamlField(String yamlFilePath) {
    return getVerifyStepYamlField(yamlFilePath, "serviceIdentifier", "envIdentifier");
  }

  public YamlField getVerifyStepYamlField(String yamlFilePath, String serviceRef, String envRef) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource(yamlFilePath);
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    yamlContent = yamlContent.replace("$serviceRef", serviceRef);
    yamlContent = yamlContent.replace("$environmentRef", envRef);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    return getVerifyStep(yamlField);
  }

  private YamlField getVerifyStep(YamlField yamlField) {
    if (CVNGStepType.CVNG_VERIFY.getDisplayName().equals(yamlField.getNode().getType())) {
      return yamlField;
    } else {
      for (YamlField child : yamlField.getNode().fields()) {
        YamlField result = getVerifyStep(child);
        if (result != null) {
          return result;
        }
      }

      if (yamlField.getNode().isArray()) {
        for (YamlNode child : yamlField.getNode().asArray()) {
          YamlField result = getVerifyStep(new YamlField(child));
          if (result != null) {
            return result;
          }
        }
      }
      return null;
    }
  }
}
