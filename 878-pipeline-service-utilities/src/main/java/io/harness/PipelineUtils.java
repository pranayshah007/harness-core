package io.harness;

import io.harness.ng.core.NGAccess;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineUtils {
  public String getBuildDetailsUrl(NGAccess ngAccess, String pipelineId, String executionId, String ngBaseUrl) {
    String detailsUrl = new StringBuilder(ngBaseUrl)
                            .append(String.format("/account/%s", ngAccess.getAccountIdentifier()))
                            .append(String.format("/ci/orgs/%s", ngAccess.getOrgIdentifier()))
                            .append(String.format("/projects/%s", ngAccess.getProjectIdentifier()))
                            .append(String.format("/pipelines/%s", pipelineId))
                            .append(String.format("/executions/%s", executionId))
                            .append("/pipeline")
                            .toString();
    log.info("DetailsUrl is: [{}]", detailsUrl);
    return detailsUrl;
  }

  public static Set<YamlField> getStagesFieldFromPipeline(YamlField pipelineField) {
    YamlField stagesYamlField = pipelineField.getNode().getField(YAMLFieldNameConstants.STAGES);
    List<YamlNode> yamlNodes = Optional.of(stagesYamlField.getNode().asArray()).orElse(Collections.emptyList());
    Set<YamlField> stageFields = new HashSet<>();
    yamlNodes.forEach(yamlNode -> {
      YamlField stageField = yamlNode.getField("stage");
      YamlField parallelStageField = yamlNode.getField("parallel");
      if (stageField != null) {
        stageFields.add(stageField);
      } else if (parallelStageField != null) {
        stageFields.addAll(Optional.of(parallelStageField.getNode().asArray())
                               .orElse(Collections.emptyList())
                               .stream()
                               .map(el -> el.getField(YAMLFieldNameConstants.STAGE))
                               .filter(Objects::nonNull)
                               .collect(Collectors.toList()));
      }
    });
    return stageFields;
  }
}
