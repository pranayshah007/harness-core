/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.v2.model;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@OwnedBy(PL)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@FieldNameConstants(innerTypeName = "ResourceSelectorKeys")
@ApiModel(value = "ResourceSelectorV2")
@Schema(name = "ResourceSelectorV2", description = "Used to specify resources in resource group")
public class ResourceSelector {
  @NotNull String resourceType;
  List<String> identifiers;
}
