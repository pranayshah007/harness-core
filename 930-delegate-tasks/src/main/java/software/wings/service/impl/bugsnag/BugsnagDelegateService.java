/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.bugsnag;

import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.BugsnagConfig;
import software.wings.beans.TaskType;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateTaskType;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;

public interface BugsnagDelegateService {
  @DelegateTaskType(TaskType.BUGSNAG_GET_APP_TASK)
  Set<BugsnagApplication> getOrganizations(
      BugsnagConfig config, List<EncryptedDataDetail> encryptedDataDetails, ThirdPartyApiCallLog apiCallLog);
  @DelegateTaskType(TaskType.BUGSNAG_GET_APP_TASK)
  Set<BugsnagApplication> getProjects(BugsnagConfig config, String orgId,
      List<EncryptedDataDetail> encryptedDataDetails, ThirdPartyApiCallLog apiCallLog);
  @DelegateTaskType(TaskType.BUGSNAG_GET_RECORDS)
  Object search(@NotNull BugsnagConfig config, String accountId, BugsnagSetupTestData bugsnagSetupTestData,
      List<EncryptedDataDetail> encryptedDataDetails, ThirdPartyApiCallLog apiCallLog) throws IOException;
}
