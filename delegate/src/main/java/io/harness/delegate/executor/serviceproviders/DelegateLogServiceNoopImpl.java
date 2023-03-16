/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor.serviceproviders;

import io.harness.cvng.beans.cvnglog.CVNGLogDTO;

import software.wings.beans.dto.CVActivityLog;
import software.wings.beans.dto.Log;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.LogSanitizer;

import com.google.inject.Singleton;

@Singleton
public class DelegateLogServiceNoopImpl implements DelegateLogService {
  @Override
  public void save(String accountId, Log logObject) {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }

  @Override
  public void save(String accountId, ThirdPartyApiCallLog thirdPartyApiCallLog) {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }

  @Override
  public void save(String accountId, CVActivityLog cvActivityLog) {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }

  @Override
  public void registerLogSanitizer(LogSanitizer sanitizer) {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }

  @Override
  public void unregisterLogSanitizer(LogSanitizer sanitizer) {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }

  @Override
  public void save(String accountId, CVNGLogDTO cvngLogDTO) {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }
}
