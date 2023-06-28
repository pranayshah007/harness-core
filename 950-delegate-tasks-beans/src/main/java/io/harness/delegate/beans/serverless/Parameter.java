/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.serverless;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Parameter {
  private final String parameterKey;
  private final String parameterValue;
  private final Boolean usePreviousValue;
  private final String resolvedValue;
}
