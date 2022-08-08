/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.delegatetasks.DelegateCVActivityLogService.Logger;
import software.wings.service.impl.ThirdPartyApiCallLog;

import java.util.Map;
import retrofit2.Call;
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public interface DataCollectionExecutionContext {
  ThirdPartyApiCallLog createApiCallLog();
  void saveThirdPartyApiCallLog(ThirdPartyApiCallLog thirdPartyApiCallLog);
  Logger getActivityLogger();
  <T> T executeRequest(String thirdPartyApiCallTitle, Call<T> request, Map<String, String> patternsToMaskInCallLog);
  <T> T executeRequest(String thirdPartyApiCallTitle, Call<T> request);
}
