/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.script.ScriptInfo;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.data.validator.EntityIdentifier;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("testStepInfo")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.TestStepInfo")
public class TestStepInfo implements CIStepInfo {
  public static final int DEFAULT_RETRY = 0;
  public static final int DEFAULT_TIMEOUT = 1200;

  @JsonIgnore
  public static final TypeInfo typeInfo =
      TypeInfo.builder()
          .stepInfoType(CIStepInfoType.TEST)
          .stepType(StepType.newBuilder().setType(CIStepInfoType.TEST.name()).build())
          .build();
  @JsonIgnore
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(CIStepInfoType.TEST.name()).setStepCategory(StepCategory.STEP).build();

  @NotNull @EntityIdentifier private String identifier;
  private String name;
  @Min(MIN_RETRY) @Max(MAX_RETRY) private int retry;

  @NotEmpty private String numParallel;
  private List<ScriptInfo> scriptInfos;
  @ApiModelProperty(dataType = INTEGER_CLASSPATH) private ParameterField<Integer> runAsUser;

  @Builder
  @ConstructorProperties({"identifier", "name", "retry", "numParallel", "scriptInfos", "runAsUser"})
  public TestStepInfo(String identifier, String name, Integer retry, String numParallel, List<ScriptInfo> scriptInfos,
      ParameterField<Integer> runAsUser) {
    this.identifier = identifier;
    this.name = name;
    this.retry = Optional.ofNullable(retry).orElse(DEFAULT_RETRY);

    this.numParallel = numParallel;
    this.scriptInfos = scriptInfos;
    this.runAsUser = runAsUser;
  }

  @Override
  public TypeInfo getNonYamlInfo() {
    return typeInfo;
  }

  @Override
  public StepType getStepType() {
    return STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }
}
