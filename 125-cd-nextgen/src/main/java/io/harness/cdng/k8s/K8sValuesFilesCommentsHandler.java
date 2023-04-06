/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class K8sValuesFilesCommentsHandler {
  public List<String> removeComments(List<String> valuesFiles, String manifestType) {
    if (ManifestType.K8Manifest.equals(manifestType) || ManifestType.HelmChart.equals(manifestType)) {
      return removeCommentsFromValuesYamlFiles(valuesFiles);
    }
    /*
        ToDo: handle other manifest types like OpenShift and Kustomize
        else if (ManifestType.OpenshiftTemplate.equals(manifestType)) {}
        else if (ManifestType.Kustomize.equals(manifestType)) {}
     */
    return new ArrayList<>();
  }
  private List<String> removeCommentsFromValuesYamlFiles(List<String> valuesFiles) {
    List<String> modifiedValuesFiles = new ArrayList<>();
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    Yaml yaml = new Yaml(options);
    Map<String, String> map;
    String str;
    for (String values : valuesFiles) {
      if (isNotEmpty(values)) {
        map = yaml.load(values);
        str = isNotEmpty(map) ? yaml.dump(map) : "";
        modifiedValuesFiles.add(str);
      }
    }
    return modifiedValuesFiles;
  }
}
