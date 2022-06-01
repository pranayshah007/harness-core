/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.template;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

@Singleton
public class TemplateFacade {
  private static final String TEMPLATE_KEY = "template";

  @Inject private TemplateResourceClient templateResourceClient;

  public String resolveYaml(ProjectParams projectParams, String yaml) {
    TemplateMergeResponseDTO templateMergeResponseDTO =
        NGRestUtils.getResponse(templateResourceClient.applyTemplatesOnGivenYaml(projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), null, null, null,
            TemplateApplyRequestDTO.builder().originalEntityYaml(yaml).checkForAccess(false).build()));
    String resolvedYaml = templateMergeResponseDTO.getMergedPipelineYaml();
    return getResolvedYamlWithInputTemplateMerged(resolvedYaml, yaml);
  }

  private String getResolvedYamlWithInputTemplateMerged(String resolvedYaml, String inputYaml) {
    Yaml yamlObject = new Yaml();
    Map<String, Object> data = yamlObject.load(resolvedYaml);
    Map<String, Object> monitoredServiceData =
        (Map<String, Object>) data.get(CVNextGenConstants.MONITORED_SERVICE_YAML_ROOT);
    Map<String, Object> inputData = yamlObject.load(inputYaml);
    Map<String, Object> inputMonitoredServiceData =
        (Map<String, Object>) inputData.get(CVNextGenConstants.MONITORED_SERVICE_YAML_ROOT);
    if (inputMonitoredServiceData.containsKey(TEMPLATE_KEY)) {
      monitoredServiceData.put(TEMPLATE_KEY, inputMonitoredServiceData.get(TEMPLATE_KEY));
    }
    return yamlObject.dump(data);
  }
}
