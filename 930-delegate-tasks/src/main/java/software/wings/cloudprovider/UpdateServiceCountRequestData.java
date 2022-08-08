/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.cloudprovider;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;

import com.amazonaws.services.ecs.model.ServiceEvent;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDP)
@Data
@Builder
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class UpdateServiceCountRequestData {
  private String region;
  private String cluster;
  private String serviceName;
  private List<ServiceEvent> serviceEvents;
  private AwsConfig awsConfig;
  private ExecutionLogCallback executionLogCallback;
  private List<EncryptedDataDetail> encryptedDataDetails;
  private int desiredCount;
  private Integer timeOut;
}
