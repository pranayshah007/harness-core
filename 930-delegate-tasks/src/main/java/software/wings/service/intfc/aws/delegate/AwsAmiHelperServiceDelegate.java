/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployResponse;
import software.wings.service.impl.aws.model.AwsAmiServiceSetupRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceSetupResponse;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbDeployRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbSetupRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbSetupResponse;
import software.wings.service.impl.aws.model.AwsAmiSwitchRoutesRequest;
import software.wings.service.impl.aws.model.AwsAmiSwitchRoutesResponse;
import software.wings.service.impl.aws.model.AwsAmiTrafficShiftAlbSwitchRouteRequest;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public interface AwsAmiHelperServiceDelegate {
  AwsAmiServiceSetupResponse setUpAmiService(AwsAmiServiceSetupRequest request, ExecutionLogCallback logCallback);
  AwsAmiServiceDeployResponse deployAmiService(AwsAmiServiceDeployRequest request, ExecutionLogCallback logCallback);
  AwsAmiSwitchRoutesResponse switchAmiRoutes(AwsAmiSwitchRoutesRequest request, ExecutionLogCallback logCallback);
  AwsAmiSwitchRoutesResponse rollbackSwitchAmiRoutes(
      AwsAmiSwitchRoutesRequest request, ExecutionLogCallback logCallback);

  AwsAmiServiceTrafficShiftAlbSetupResponse setUpAmiServiceTrafficShift(
      AwsAmiServiceTrafficShiftAlbSetupRequest request);
  AwsAmiServiceDeployResponse deployAmiServiceTrafficShift(AwsAmiServiceTrafficShiftAlbDeployRequest request);
  AwsAmiSwitchRoutesResponse switchAmiRoutesTrafficShift(AwsAmiTrafficShiftAlbSwitchRouteRequest request);
  AwsAmiSwitchRoutesResponse rollbackSwitchAmiRoutesTrafficShift(AwsAmiTrafficShiftAlbSwitchRouteRequest request);
}
