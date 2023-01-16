/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.yaml.BaseYaml;

import dev.morphia.annotations.Transient;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by sgurubelli on 10/25/17.
 */
@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "ArtifactSelectionKeys")
public class ArtifactSelection {
  @NotEmpty private String serviceId;
  private String serviceName;
  @NotEmpty private Type type;
  private String artifactStreamId;
  private String artifactSourceName;
  private String artifactFilter;
  private String pipelineId;
  private String pipelineName;
  private String workflowId;
  private String workflowName;
  private boolean regex;
  @Transient private String uiDisplayName;

  public enum Type { ARTIFACT_SOURCE, LAST_COLLECTED, LAST_DEPLOYED, PIPELINE_SOURCE, WEBHOOK_VARIABLE }

  public void setPipelineId(String pipelineId) {
    this.pipelineId = pipelineId;
    this.workflowId = pipelineId;
  }

  public void setPipelineName(String pipelineName) {
    this.pipelineName = pipelineName;
    this.workflowName = pipelineName;
  }

  public String getPipelineId() {
    if (this.pipelineId == null) {
      return this.workflowId;
    }
    return pipelineId;
  }

  public String getPipelineName() {
    if (this.pipelineName == null) {
      return this.workflowName;
    }
    return this.pipelineName;
  }

  public String getWorkflowId() {
    if (workflowId == null) {
      return pipelineId;
    }
    return workflowId;
  }

  public String getWorkflowName() {
    if (workflowName == null) {
      return pipelineName;
    }
    return workflowName;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseYaml {
    String type;
    private String artifactStreamName;
    private boolean regex;
    private String artifactFilter;
    String workflowName;
    String pipelineName;
    String serviceName;

    @lombok.Builder
    public Yaml(String type, String artifactStreamName, String workflowName, String artifactFilter, String serviceName,
        boolean regex, String pipelineName) {
      this.artifactStreamName = artifactStreamName;
      this.workflowName = workflowName;
      this.pipelineName = pipelineName;
      this.artifactFilter = artifactFilter;
      this.type = type;
      this.regex = regex;
      this.serviceName = serviceName;
    }
  }
}
