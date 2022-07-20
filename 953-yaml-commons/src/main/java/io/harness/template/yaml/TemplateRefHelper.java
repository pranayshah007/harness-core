/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.yaml;

import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TemplateRefHelper {
  public final String TEMPLATE_REF = "templateRef";
  public final String TEMPLATE = "template";

  public boolean hasTemplateRef(String yaml) {
    YamlConfig yamlConfig = new YamlConfig(yaml);
    return hasTemplateRef(yamlConfig);
  }

  public boolean hasTemplateRef(YamlConfig yamlConfig) {
    Set<FQN> fqnSet = new LinkedHashSet<>(yamlConfig.getFqnToValueMap().keySet());
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
}
