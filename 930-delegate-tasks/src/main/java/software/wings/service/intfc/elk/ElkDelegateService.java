/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.elk;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.ElkConfig;
import software.wings.beans.TaskType;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.service.impl.elk.ElkIndexTemplate;
import software.wings.service.impl.elk.ElkLogFetchRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

/**
 * Created by rsingh on 08/01/17.
 */
@OwnedBy(HarnessTeam.CV)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public interface ElkDelegateService {
  @DelegateTaskType(TaskType.ELK_CONFIGURATION_VALIDATE_TASK)
  boolean validateConfig(@NotNull ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails);

  @DelegateTaskType(TaskType.ELK_GET_HOST_RECORDS)
  Object search(@NotNull ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails,
      ElkLogFetchRequest logFetchRequest, ThirdPartyApiCallLog apiCallLog, int maxRecords) throws IOException;

  @DelegateTaskType(TaskType.ELK_COLLECT_INDICES)
  Map<String, ElkIndexTemplate> getIndices(ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails);

  @DelegateTaskType(TaskType.ELK_GET_LOG_SAMPLE)
  Object getLogSample(
      ElkConfig elkConfig, String index, boolean shouldSort, List<EncryptedDataDetail> encryptedDataDetails);

  @DelegateTaskType(TaskType.KIBANA_GET_VERSION)
  String getVersion(ElkConfig elkConfig, List<EncryptedDataDetail> encryptedDataDetails) throws IOException;
}
