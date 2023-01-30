/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executorlib.response;

import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.taskagent.client.delegate.DelegateCoreClient;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ResponseSender {
    private final DelegateCoreClient delegateCoreClient;

    public void sendResponse(final String accountId, final String taskId, final DelegateTaskResponse taskResponse) {
        delegateCoreClient.taskResponse(accountId, taskId, taskResponse);
    }}
