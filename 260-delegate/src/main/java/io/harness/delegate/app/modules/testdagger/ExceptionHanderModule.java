/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.testdagger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;
import io.harness.delegate.app.modules.ExceptionKey;
import io.harness.delegate.exceptionhandler.handler.AmazonClientExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.AmazonServiceExceptionHandler;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;

@Module
public abstract class ExceptionHanderModule {
  @Binds
  @IntoMap
  @ExceptionKey(AmazonServiceException.class)
  abstract ExceptionHandler bindAmazonServiceException(AmazonServiceExceptionHandler amazonServiceExceptionHandler);

  @Binds
  @IntoMap
  @ExceptionKey(AmazonClientException.class)
  abstract ExceptionHandler bindAmazonClientException(AmazonClientExceptionHandler amazonClientExceptionHandler);
}
