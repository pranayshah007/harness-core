/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.yaml;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
public class TemplateRefHelper {
  public final String TEMPLATE_REF = "templateRef";
  public final String TEMPLATE = "template";
  public final String CUSTOM_DEPLOYMENT_TEMPLATE = "customDeploymentRef";

  public boolean hasTemplateRefWithCheckDuplicate(String yaml) {
    // This is added to prevent duplicate fields in the yaml. Without this, through api duplicate fields were allowed to
    // save. The below yaml is invalid and should not be allowed to save.
    /*
    pipeline:
      name: pipeline
      orgIdentifier: org
      projectIdentifier: project
      orgIdentifier: org
     */
    JsonNode jsonNode = YamlUtils.readAsJsonNode(yaml);
    return hasTemplateRef(jsonNode);
  }

  public boolean hasTemplateRef(String yaml) {
    YamlConfig yamlConfig = new YamlConfig(yaml);
    return hasTemplateRef(yamlConfig);
  }

  public boolean hasTemplateRef(YamlConfig yamlConfig) {
    return hasTemplateRef(yamlConfig.getYamlMap());
  }

  public boolean hasTemplateRef(JsonNode jsonNode) {
    Map<FQN, Object> fqnObjectMap = FQNMapGenerator.generateFQNMap(jsonNode);
    Set<FQN> fqnSet = new LinkedHashSet<>(fqnObjectMap.keySet());
    for (FQN key : fqnSet) {
      if (key.getFqnList().size() >= 2) {
        List<FQNNode> fqnList = new ArrayList<>(key.getFqnList());
        FQNNode lastNode = fqnList.get(fqnList.size() - 1);
        FQNNode secondLastNode = fqnList.get(fqnList.size() - 2);
        if (TEMPLATE_REF.equals(lastNode.getKey()) && TEMPLATE.equals(secondLastNode.getKey())) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean hasTemplateRefOrCustomDeploymentRef(String yaml) {
    YamlConfig yamlConfig = new YamlConfig(yaml);
    return hasTemplateRefOrCustomDeploymentRef(yamlConfig);
  }

  public boolean hasTemplateRefOrCustomDeploymentRef(YamlConfig yamlConfig) {
    Set<FQN> fqnSet = new LinkedHashSet<>(yamlConfig.getFqnToValueMap().keySet());
    for (FQN key : fqnSet) {
      if (key.getFqnList().size() >= 2) {
        List<FQNNode> fqnList = new ArrayList<>(key.getFqnList());
        FQNNode lastNode = fqnList.get(fqnList.size() - 1);
        FQNNode secondLastNode = fqnList.get(fqnList.size() - 2);
        if (TEMPLATE_REF.equals(lastNode.getKey())
            && (TEMPLATE.equals(secondLastNode.getKey())
                || CUSTOM_DEPLOYMENT_TEMPLATE.equals(secondLastNode.getKey()))) {
          return true;
        }
      }
    }
    return false;
  }
}
