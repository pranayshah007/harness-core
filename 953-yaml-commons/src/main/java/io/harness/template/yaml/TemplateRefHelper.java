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

  public boolean hasTemplateRef(String pipelineYaml) {
    YamlConfig yamlConfig = new YamlConfig(pipelineYaml);
    Set<FQN> fqnSet = new LinkedHashSet<>(yamlConfig.getFqnToValueMap().keySet());
    for (FQN key : fqnSet) {
      if (key.getFqnList().size() >= 2) {
        List<FQNNode> fqnList = new ArrayList<>(key.getFqnList());
        FQNNode lastNode = fqnList.get(fqnList.size() - 1);
        FQNNode secondLastNode = fqnList.get(fqnList.size() - 2);
        if (lastNode.getKey().equals(TEMPLATE_REF) && secondLastNode.getKey().equals(TEMPLATE)) {
          return true;
        }
      }
    }
    return false;
  }
}
