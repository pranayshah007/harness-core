/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.yaml;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceUseFromStageV2;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@SimpleVisitorHelper(helperClass = NGServiceV2InfoConfigVisitorHelper.class)
@TypeAlias("nGServiceV2InfoConfig")
public class NGServiceV2InfoConfig implements Visitable {
  @JsonProperty("__uuid")
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @NotNull @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) String name;
  @NotNull @EntityIdentifier @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) String description;
  Map<String, String> tags;
  Boolean gitOpsEnabled;
  ServiceUseFromStageV2 useFromStage;

  ServiceDefinition serviceDefinition;

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add("serviceDefinition", serviceDefinition);
    return children;
  }
}
