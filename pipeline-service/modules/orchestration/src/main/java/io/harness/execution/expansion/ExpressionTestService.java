/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.execution.expansion;


import io.harness.execution.ExpressionDetailRequest;
import io.harness.execution.ExpressionDetailResponse;
import io.harness.execution.ExpressionTestRequest;
import io.harness.execution.ExpressionTestResponse;

public interface ExpressionTestService {
    ExpressionDetailResponse getExpressionResponse(String planExecutionId, String expression);
}
