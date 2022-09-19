/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.yaml.gitSync;

import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 2019-08-08.
 */
@Data
@Builder
public class GitWebhookRequestAttributes {
  private String webhookBody;
  private String webhookHeaders;
  @NotEmpty private String branchName;
  private String repositoryFullName;
  @NotEmpty private String gitConnectorId;
  String headCommitId;
  Boolean isPollingBased;
}
