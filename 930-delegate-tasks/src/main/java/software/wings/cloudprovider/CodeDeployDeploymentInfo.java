/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.cloudprovider;

import io.harness.logging.CommandExecutionStatus;

import com.amazonaws.services.ec2.model.Instance;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by anubhaw on 6/23/17.
 */
@Data
@NoArgsConstructor
public class CodeDeployDeploymentInfo {
  private CommandExecutionStatus status;
  private List<Instance> instances;
  private String deploymentId;
}
