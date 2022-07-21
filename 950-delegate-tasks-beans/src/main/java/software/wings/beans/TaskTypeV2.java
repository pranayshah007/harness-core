/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskGroup;
import io.harness.delegate.beans.ci.docker.DestroyDockerRequest;
import io.harness.delegate.beans.ci.docker.ExecuteStepDockerRequest;
import io.harness.delegate.beans.ci.docker.SetupDockerRequest;
import io.harness.delegate.beans.ci.docker.ExecuteStepDockerResponse;
import io.harness.delegate.task.TaskParameters;

@OwnedBy(CI)
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public enum TaskTypeV2 {
    CI_DOCKER_INITIALIZE_TASK(TaskGroup.CI, SetupDockerRequest.class, ExecuteStepDockerResponse.class, true),
    CI_DOCKER_EXECUTE_TASK(TaskGroup.CI, ExecuteStepDockerRequest.class, ExecuteStepDockerResponse.class, true),
    CI_DOCKER_CLEANUP_TASK(TaskGroup.CI, DestroyDockerRequest.class, ExecuteStepDockerResponse.class, true);

    private final TaskGroup taskGroup;
    private final String displayName;
    private final Class<? extends TaskParameters> request;
    private final Class<? extends DelegateResponseData> response;
    // We set onlyV2 as true for tasks that are only implemented in the new delegate. Tasks which have an existing
    // implementation in the old delegate will have this parameter set as false.
    private final boolean onlyV2;

    TaskTypeV2(TaskGroup taskGroup, Class<? extends TaskParameters> request,
               Class<? extends DelegateResponseData> response, boolean onlyV2) {
        this.taskGroup = taskGroup;
        this.request = request;
        this.response = response;
        this.onlyV2 = onlyV2;
        this.displayName = null;
    }

    TaskTypeV2(TaskGroup taskGroup, String displayName, Class<? extends TaskParameters> request,
               Class<? extends DelegateResponseData> response, boolean onlyV2) {
        this.taskGroup = taskGroup;
        this.displayName = displayName;
        this.request = request;
        this.onlyV2 = onlyV2;
        this.response = response;
    }

    public TaskGroup getTaskGroup() {
        return this.taskGroup;
    }
    public String getDisplayName() {
        return this.displayName != null ? this.displayName : name();
    }
    public boolean isOnlyV2() {
        return this.onlyV2;
    }

    public Class<? extends TaskParameters> getRequestClass() {
        return this.request;
    }
    public Class<? extends DelegateResponseData> getResponseClass() {
        return this.response;
    }
}
