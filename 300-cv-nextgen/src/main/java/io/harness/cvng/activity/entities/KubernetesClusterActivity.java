/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.entities;

import static io.harness.cvng.beans.activity.ActivityType.KUBERNETES;

import io.harness.cvng.beans.activity.ActivityDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata.Action;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata.KubernetesResourceType;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceBuilder;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@FieldNameConstants(innerTypeName = "KubernetesClusterActivityKeys")
@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonTypeName("KUBERNETES")
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KubernetesClusterActivity extends Activity {
  String oldYaml;
  String newYaml;
  String workload;
  String namespace;
  String kind;
  KubernetesResourceType resourceType;
  Action action;
  String reason;
  String message;
  String resourceVersion;
  List<RelatedAppMonitoredService> relatedAppServices;

  @Override
  public ActivityType getType() {
    return KUBERNETES;
  }

  @Override
  public void fromDTO(ActivityDTO activityDTO) {
    throw new UnsupportedOperationException("KubernetesClusterActivity can be transformed only from ChangeEventDTO");
  }

  @Override
  public void fillInVerificationJobInstanceDetails(VerificationJobInstanceBuilder verificationJobInstanceBuilder) {
    throw new UnsupportedOperationException(
        "We are currently not supporting analysis for KubernetesClusterActivity in ChI");
  }

  @Override
  public void validateActivityParams() {}

  @Override
  public boolean deduplicateEvents() {
    return false;
  }

  public static class KubernetesClusterActivityUpdatableEntity
      extends ActivityUpdatableEntity<KubernetesClusterActivity, KubernetesClusterActivity> {
    @Override
    public Class getEntityClass() {
      return KubernetesClusterActivity.class;
    }

    @Override
    public String getEntityKeyLongString(KubernetesClusterActivity activity) {
      return getKeyBuilder(activity)
          .add(activity.getEventTime().toString())
          .add(activity.getResourceType().name())
          .add(activity.getAction().name())
          .toString();
    }

    @Override
    public void setUpdateOperations(
        UpdateOperations<KubernetesClusterActivity> updateOperations, KubernetesClusterActivity activity) {
      setCommonUpdateOperations(updateOperations, activity);
      updateOperations.set(KubernetesClusterActivityKeys.resourceType, activity.getResourceType())
          .set(KubernetesClusterActivityKeys.action, activity.getAction());

      setIfNotNull(updateOperations, KubernetesClusterActivityKeys.kind, activity.getKind());
      setIfNotNull(updateOperations, KubernetesClusterActivityKeys.reason, activity.getReason());
      setIfNotNull(updateOperations, KubernetesClusterActivityKeys.message, activity.getMessage());
      setIfNotNull(updateOperations, KubernetesClusterActivityKeys.oldYaml, activity.getOldYaml());
      setIfNotNull(updateOperations, KubernetesClusterActivityKeys.newYaml, activity.getNewYaml());
      setIfNotNull(updateOperations, KubernetesClusterActivityKeys.namespace, activity.getNamespace());
      setIfNotNull(updateOperations, KubernetesClusterActivityKeys.workload, activity.getWorkload());
      setIfNotNull(updateOperations, KubernetesClusterActivityKeys.resourceVersion, activity.getResourceVersion());
    }

    @Override
    public Query<KubernetesClusterActivity> populateKeyQuery(
        Query<KubernetesClusterActivity> query, KubernetesClusterActivity activity) {
      return super.populateKeyQuery(query, activity)
          .filter(ActivityKeys.eventTime, activity.getEventTime())
          .filter(KubernetesClusterActivityKeys.resourceType, activity.getResourceType())
          .filter(KubernetesClusterActivityKeys.action, activity.getAction())
          .filter(KubernetesClusterActivityKeys.oldYaml, activity.getOldYaml())
          .filter(KubernetesClusterActivityKeys.newYaml, activity.getNewYaml());
    }

    private void setIfNotNull(UpdateOperations<KubernetesClusterActivity> updateOperations, String key, Object value) {
      if (value != null) {
        updateOperations.set(key, value);
      }
    }
  }

  public List<String> getRealatedAppMonitoredServiceIdentifiers() {
    if (relatedAppServices == null) {
      return Collections.emptyList();
    }
    return relatedAppServices.stream()
        .map(RelatedAppMonitoredService::getMonitoredServiceIdentifier)
        .collect(Collectors.toList());
  }

  @FieldNameConstants(innerTypeName = "ServiceEnvironmentKeys")
  @Value
  @Builder
  public static class RelatedAppMonitoredService {
    String monitoredServiceIdentifier;
  }
}
