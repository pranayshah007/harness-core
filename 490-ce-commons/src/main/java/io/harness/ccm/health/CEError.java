/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.health;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import lombok.Getter;

@OwnedBy(CE)
public enum CEError {
  NO_CLUSTERS_TRACKED_BY_HARNESS_CE("No ECS cluster tracked by Harness for this AWS cloud provider %s."),

  PERPETUAL_TASK_CREATION_FAILURE("Perpetual tasks fail to be created for the cluster %s."),

  DELEGATE_NOT_AVAILABLE("Unable to collect events. The Delegate %s may be disconnected or deleted."),

  DELEGATE_NOT_INSTALLED("Unable to collect events. No installed delegates found."),

  TASK_VALIDATION_FAILED("Validation failed."),

  NO_ELIGIBLE_DELEGATE("No Delegate has all the requisites to access the cluster %s."),

  PERPETUAL_TASK_NOT_ASSIGNED("Initializing the Delegate to collect events. Events will arrive in a few minutes."),

  NO_RECENT_EVENTS_PUBLISHED("The cluster %s has not published events since %s."),

  CREDENTIALS_INCORRECT("Credentials are not correct."),

  K8S_PERMISSIONS_MISSING("This service account does not have all the permissions to collect data."),

  NODES_IS_FORBIDDEN("Nodes is forbidden, cannot list nodes"),

  PVC_PERMISSION_ERROR("Cannot get resource persistentVolumeClaim"),

  METRICS_SERVER_NOT_FOUND("Metrics server is not installed in this Kubernetes Cluster %s. 404 page not found"),

  AWS_ECS_CLUSTER_NOT_FOUND("The ECS cluster %s received error code ClusterNotFoundException from Amazon ECS service."),

  BILLING_REPORTS_MISSING("Billing Reports are not generated/available/accessible."),

  INTERNAL_ERROR("Internal error.");

  @Getter private String message;

  CEError(String message) {
    this.message = message;
  }
}
