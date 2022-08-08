/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.artifact;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.context.ContextElementType;
import io.harness.expression.ExpressionEvaluator;

import software.wings.beans.EntityType;
import software.wings.sm.ContextElement;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ServiceArtifactVariableElement implements ContextElement {
  private String uuid;
  private String name;
  private EntityType entityType;
  private String entityId;
  private String serviceId;
  private String artifactVariableName;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.ARTIFACT_VARIABLE;
  }

  public String getArtifactVariableName() {
    return isBlank(artifactVariableName) ? ExpressionEvaluator.DEFAULT_ARTIFACT_VARIABLE_NAME : artifactVariableName;
  }

  @Override
  public ContextElement cloneMin() {
    return this;
  }
}
