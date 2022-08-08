/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.remote.beans.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.recommendation.ResourceType;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@OwnedBy(CE)
@Data
@Builder(builderClassName = "Builder")
@JsonInclude(NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "K8sRecommendationFilterKeys")
@Schema(name = "K8sRecommendationFilterProperties",
    description = "Properties of the K8sRecommendation Filter defined in Harness")
public class K8sRecommendationFilterPropertiesDTO {
  @Schema(name = "ids", description = "List of ids which will be applied as filter for Recommendations")
  List<String> ids;

  @Schema(name = "names", description = "List of names which will be applied as filter for Recommendations")
  List<String> names;

  @Schema(name = "namespaces", description = "List of namespaces which will be applied as filter for Recommendations")
  List<String> namespaces;

  @Schema(
      name = "clusterNames", description = "List of clusterNames which will be applied as filter for Recommendations")
  List<String> clusterNames;

  @Schema(
      name = "resourceTypes", description = "List of resourceTypes which will be applied as filter for Recommendations")
  List<ResourceType> resourceTypes;
}
