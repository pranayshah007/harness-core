/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.models.constants;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.ArtifactDetails.ArtifactDetailsKeys;
import io.harness.entities.Instance.InstanceKeys;

import java.time.Duration;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.DX)
public final class InstanceSyncConstants {
  public static final String HARNESS_ACCOUNT_IDENTIFIER = "accountIdentifier";
  public static final String HARNESS_ORG_IDENTIFIER = "orgIdentifier";
  public static final String HARNESS_PROJECT_IDENTIFIER = "projectIdentifier";
  public static final String HARNESS_ENV_ID = "environmentId";
  public static final String INFRASTRUCTURE_MAPPING_ID = "infrastructureMappingId";
  public static final String SERVICE_ID = "serviceId";
  public static final String INFRASTRUCTURE_MAPPING_DETAILS = "infrastructureMappingDetails";
  public static final String NAMESPACE = "namespace";
  public static final String RELEASE_NAME = "releaseName";
  public static final String CONTAINER_SERVICE_NAME = "containerSvcName";
  public static final String CONTAINER_TYPE = "containerType";
  public static final int TIMEOUT_SECONDS = 600;
  public static final int INTERVAL_MINUTES = 10;
  public static final int VALIDATION_TIMEOUT_MINUTES = 2;
  public static final String COUNT = "count";
  public static final String PRIMARY_ARTIFACT_TAG = InstanceKeys.primaryArtifact + "." + ArtifactDetailsKeys.tag;
  public static final String PRIMARY_ARTIFACT_DISPLAY_NAME =
      InstanceKeys.primaryArtifact + "." + ArtifactDetailsKeys.displayName;
  public static final String ID = "_id";
  public static final String INSTANCES = "instances";
  public static final int INSTANCE_LIMIT = 20;
  public static final String buildId = "buildId";
  public static final String ENV_TYPE = "envType";
  public static final String AUTO_SCALED = "AUTO_SCALED";

  // Lock key prefixes and timeouts
  public static final String INSTANCE_SYNC_PREFIX = "INSTANCE_SYNC:";
  public static final Duration INSTANCE_SYNC_LOCK_TIMEOUT = Duration.ofSeconds(200);
  public static final Duration INSTANCE_SYNC_WAIT_TIMEOUT = Duration.ofSeconds(220);

  // Logging keys
  public static final String INSTANCE_SYNC_FLOW_KEY = "instanceSyncFlow";
  public static final String INFRASTRUCTURE_MAPPING_KEY = "infrastructureMappingId";
  public static final String PERPETUAL_TASK_ID_KEY = "perpetualTaskId";
  public static final String DEPLOYMENT_EVENT_ID = "deploymentEventId";
}
