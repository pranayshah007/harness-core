/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.k8s.response;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.k8s.model.K8sPod;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class K8sScaleResponse implements K8sTaskResponse {
  List<K8sPod> k8sPodList;
}
