/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.customDeployment;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.visitor.helpers.customDeployment.CustomDeploymentVisitorHelper;
import io.harness.plancreator.customDeployment.CustomDeploymentExecutionConfig;
import io.harness.plancreator.customDeployment.CustomDeploymentInfoConfig;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChild;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("customDeploymentConfig")
@SimpleVisitorHelper(helperClass = CustomDeploymentVisitorHelper.class)
public class CustomDeploymentConfig implements CustomDeploymentInfoConfig, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @NotNull @NotEmpty CustomDeploymentInfraConfig infrastructure; // change to the new infrastructure created
  @NotNull @VariableExpression(skipVariableExpression = true) CustomDeploymentExecutionConfig execution;

  @Override
  public VisitableChildren getChildrenToWalk() {
    List<VisitableChild> children = new ArrayList<>();
    children.add(VisitableChild.builder().value(infrastructure).fieldName("infrastructure").build());
    return VisitableChildren.builder().visitableChildList(children).build();
  }
}
