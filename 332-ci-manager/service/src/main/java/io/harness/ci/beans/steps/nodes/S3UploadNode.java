/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.nodes;

import static io.harness.annotations.dev.HarnessTeam.CI;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.UploadToS3StepInfo;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("S3Upload")
@TypeAlias("S3UploadNode")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.nodes.S3UploadNode")

public class S3UploadNode extends CIAbstractStepNode {
  @JsonProperty("type") @NotNull S3UploadNode.StepType type = S3UploadNode.StepType.S3Upload;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  UploadToS3StepInfo uploadToS3StepInfo;
  @Override
  public String getType() {
    return CIStepInfoType.UPLOAD_S3.getDisplayName();
  }

  @Override
  public StepSpecType getStepSpecType() {
    return uploadToS3StepInfo;
  }

  enum StepType {
    S3Upload(CIStepInfoType.UPLOAD_S3.getDisplayName());
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
