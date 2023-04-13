/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;

import software.wings.beans.dto.CVActivityLog;
import software.wings.beans.dto.Log;
import software.wings.beans.dto.ThirdPartyApiCallLog;

import javax.validation.Valid;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public interface DelegateLogService {
  void save(String accountId, @Valid Log logObject);
  void save(String accountId, @Valid ThirdPartyApiCallLog thirdPartyApiCallLog);
  void save(String accountId, CVActivityLog cvActivityLog);
  void registerLogSanitizer(LogSanitizer sanitizer);
  void unregisterLogSanitizer(LogSanitizer sanitizer);
  void save(String accountId, CVNGLogDTO cvngLogDTO);
}
