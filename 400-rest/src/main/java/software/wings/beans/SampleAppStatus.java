/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import software.wings.beans.entity.SampleAppEntityStatus;
import software.wings.beans.entity.SampleAppEntityStatus.Health;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SampleAppStatus {
  String deploymentType;
  private Health health;
  List<SampleAppEntityStatus> statusList;
}
