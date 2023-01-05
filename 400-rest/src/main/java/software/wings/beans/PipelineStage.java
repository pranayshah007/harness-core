/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.yaml.BaseYamlWithType;

import com.google.common.collect.Lists;
import dev.morphia.annotations.NotSaved;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Created by anubhaw on 11/17/16.
 */
@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TargetModule(HarnessModule._957_CG_BEANS)
public class PipelineStage {
  private String name;
  private boolean parallel;
  private List<PipelineStageElement> pipelineStageElements = new ArrayList<>();
  private transient boolean valid = true;
  private transient String validationMessage;
  private transient boolean looped;
  private transient String loopedVarName;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PipelineStageElement {
    private String uuid;
    private String name;
    private String type;
    private int parallelIndex;
    private Map<String, Object> properties = new HashMap<>();
    private Map<String, String> workflowVariables = new HashMap<>();
    private RuntimeInputsConfig runtimeInputsConfig;

    // Remove this once UI moves away from it
    @NotSaved private boolean disable;
    private String disableAssertion;

    private transient boolean valid = true;
    private transient String validationMessage;

    public boolean checkDisableAssertion() {
      return disableAssertion != null && disableAssertion.equals("true");
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseYamlWithType {
    private String name;
    private String stageName;
    private SkipCondition skipCondition;
    private boolean parallel;
    private String workflowName;
    private List<WorkflowVariable> workflowVariables = Lists.newArrayList();
    private Map<String, Object> properties = new HashMap<>();
    private RuntimeInputsConfig.Yaml runtimeInputs;

    @Builder
    public Yaml(String type, String name, String stageName, boolean parallel, String workflowName,
        List<WorkflowVariable> workflowVariables, Map<String, Object> properties, SkipCondition skipCondition,
        RuntimeInputsConfig.Yaml runtimeInputs) {
      super(type);
      this.name = name;
      this.stageName = stageName;
      this.parallel = parallel;
      this.workflowName = workflowName;
      this.workflowVariables = workflowVariables;
      this.properties = properties;
      this.skipCondition = skipCondition;
      this.runtimeInputs = runtimeInputs;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static class WorkflowVariable extends NameValuePair.AbstractYaml {
    String entityType;

    @Builder
    public WorkflowVariable(
        String entityType, String name, String value, String valueType, List<AllowedValueYaml> allowedValueYamls) {
      super(name, value, valueType, allowedValueYamls);
      this.entityType = entityType;
    }
  }
}
