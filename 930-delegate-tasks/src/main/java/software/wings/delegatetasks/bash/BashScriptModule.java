/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.bash;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;

public class BashScriptModule extends AbstractModule {
    @Override
    protected void configure() {
        bindEmptyExceptionHandlers();
    }

    private void bindEmptyExceptionHandlers() {
        MapBinder<Class<? extends Exception>, ExceptionHandler> exceptionHandlerMapBinder = MapBinder.newMapBinder(
            binder(), new TypeLiteral<Class<? extends Exception>>() {}, new TypeLiteral<ExceptionHandler>() {});
    }
}
