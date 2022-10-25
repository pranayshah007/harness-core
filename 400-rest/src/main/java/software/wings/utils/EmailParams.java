/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.expression.common.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.expression.common.Expression;

import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@TargetModule(HarnessModule._957_CG_BEANS)
public class EmailParams {
  @Expression(ALLOW_SECRETS) @NonFinal @Setter String subject;
  @Expression(ALLOW_SECRETS) @NonFinal @Setter String toAddress;
  @Expression(ALLOW_SECRETS) @NonFinal @Setter String ccAddress;
  @Expression(ALLOW_SECRETS) @NonFinal @Setter String body;
}
