/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl.infra;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.common.AccessTokenBean;

import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
public interface InfraDownloadService {
  String getDownloadUrlForDelegate(@NotEmpty String version, String env, String accountId);

  String getDownloadUrlForWatcher(@NotEmpty String version, String env, String accountId);

  String getDownloadUrlForDelegate(@NotEmpty String version, String accountId);

  String getDownloadUrlForWatcher(@NotEmpty String version, String accountId);

  AccessTokenBean getStackdriverLoggingToken();

  String getCdnWatcherMetaDataFileUrl();

  String getCdnWatcherBaseUrl();
}
