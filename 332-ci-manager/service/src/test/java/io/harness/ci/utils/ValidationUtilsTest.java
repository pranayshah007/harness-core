/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.util.Lists.newArrayList;
import static org.mockito.Mockito.when;

import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.integrationstage.K8InitializeTaskUtils;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class ValidationUtilsTest extends CIExecutionTestBase {
  @Inject ValidationUtils validationUtils;
  @Mock private K8InitializeTaskUtils k8InitializeTaskUtils;

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void validateWindowsK8Stage() {
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder().os(ParameterField.createValueField(OSType.Windows)).build())
            .build();
    ExecutionElementConfig executionElementConfig = getExecutionWrapperConfig();
    when(k8InitializeTaskUtils.getOS(infrastructure)).thenReturn(OSType.Windows);

    validationUtils.validateStage(executionElementConfig, infrastructure);
  }

  private ExecutionElementConfig getExecutionWrapperConfig() {
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getDockerStepElementConfigAsJsonNode()).build());
    return ExecutionElementConfig.builder().steps(steps).build();
  }

  private JsonNode getDockerStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "docker");

    stepElementConfig.put("type", "BuildAndPushDockerRegistry");
    stepElementConfig.put("name", "docker");

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }
}
