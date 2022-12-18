/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.pod;

import io.harness.k8s.model.ImageDetails;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageDetailsWithConnector implements NestedAnnotationResolver {
  @NotNull private ConnectorDetails imageConnectorDetails;
  @NotNull private ImageDetails imageDetails;
}
