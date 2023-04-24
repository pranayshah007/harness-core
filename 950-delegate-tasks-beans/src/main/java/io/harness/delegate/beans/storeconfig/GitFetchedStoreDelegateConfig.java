/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.storeconfig;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode()
@OwnedBy(CDP)
public class GitFetchedStoreDelegateConfig implements StoreDelegateConfig, NestedAnnotationResolver {
  @Expression(ALLOW_SECRETS) List<ConfigFileParameters> configFiles;

  @Override
  public StoreDelegateConfigType getType() {
    return StoreDelegateConfigType.GIT_FETCHED;
  }
}
