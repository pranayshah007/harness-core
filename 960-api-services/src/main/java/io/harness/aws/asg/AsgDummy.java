/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressListener;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@OwnedBy(CDP)
public class AsgDummy implements ProgressListener {
  public void progressChanged(ProgressEvent progressEvent) {}
}
