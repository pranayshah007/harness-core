/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executorlib.serviceproviders;

import com.google.inject.Singleton;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import software.wings.beans.dto.Log;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.LogSanitizer;
import software.wings.service.impl.ThirdPartyApiCallLog;
import software.wings.verification.CVActivityLog;

@Singleton
public class DelegateLogServiceNoopImpl implements DelegateLogService {
    @Override
    public void save(String accountId, Log logObject) {

    }

    @Override
    public void save(String accountId, ThirdPartyApiCallLog thirdPartyApiCallLog) {

    }

    @Override
    public void save(String accountId, CVActivityLog cvActivityLog) {

    }

    @Override
    public void registerLogSanitizer(LogSanitizer sanitizer) {

    }

    @Override
    public void unregisterLogSanitizer(LogSanitizer sanitizer) {

    }

    @Override
    public void save(String accountId, CVNGLogDTO cvngLogDTO) {

    }
}
