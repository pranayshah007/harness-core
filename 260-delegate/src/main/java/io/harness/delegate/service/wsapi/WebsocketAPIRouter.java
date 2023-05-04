/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.wsapi;

import io.harness.delegate.beans.DelegateWebsocketAPIEvent;
import io.harness.delegate.service.wsapi.handlers.Handler;
import jauter.Router;

public class WebsocketAPIRouter extends Router<DelegateWebsocketAPIEvent.Method, Class<? extends Handler>, WebsocketAPIRouter> {
    @Override
    protected WebsocketAPIRouter getThis() {
        return this;
    }

    @Override
    protected DelegateWebsocketAPIEvent.Method CONNECT() {
        return DelegateWebsocketAPIEvent.Method.OTHER;
    }

    @Override
    protected DelegateWebsocketAPIEvent.Method DELETE() {
        return DelegateWebsocketAPIEvent.Method.DELETE;
    }

    @Override
    protected DelegateWebsocketAPIEvent.Method GET() {
        return DelegateWebsocketAPIEvent.Method.GET;
    }

    @Override
    protected DelegateWebsocketAPIEvent.Method HEAD() {
        return DelegateWebsocketAPIEvent.Method.OTHER;
    }

    @Override
    protected DelegateWebsocketAPIEvent.Method OPTIONS() {
        return DelegateWebsocketAPIEvent.Method.OTHER;
    }

    @Override
    protected DelegateWebsocketAPIEvent.Method PATCH() {
        return DelegateWebsocketAPIEvent.Method.OTHER;
    }

    @Override
    protected DelegateWebsocketAPIEvent.Method POST() {
        return DelegateWebsocketAPIEvent.Method.POST;
    }

    @Override
    protected DelegateWebsocketAPIEvent.Method PUT() {
        return DelegateWebsocketAPIEvent.Method.OTHER;
    }

    @Override
    protected DelegateWebsocketAPIEvent.Method TRACE() {
        return DelegateWebsocketAPIEvent.Method.OTHER;
    }
}
