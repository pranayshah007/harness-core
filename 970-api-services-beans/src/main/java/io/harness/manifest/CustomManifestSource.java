/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.manifest;

import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.expression.Expression.DISALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CustomManifestSource implements NestedAnnotationResolver {
  @Expression(ALLOW_SECRETS) @Nullable String script;
  @Expression(DISALLOW_SECRETS) List<String> filePaths;
  String zippedManifestFileId;

  // needed for downloading zip file
  String accountId;
}
