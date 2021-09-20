package io.harness.cvng.activity.entities;

import io.harness.cvng.beans.activity.ActivityDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.activity.KubernetesActivityDTO;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceBuilder;
import io.harness.mongo.index.FdIndex;

import java.time.Instant;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

@Data
@FieldNameConstants(innerTypeName = "KubernetesActivityKeys")
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Deprecated
public class KubernetesActivity extends Activity {
  String namespace;
  String workloadName;
  String kind;
  Set<KubernetesActivityDTO> activities;
  @FdIndex Instant bucketStartTime;

  @Override
  public ActivityType getType() {
    throw new NotImplementedException();
  }

  @Override
  public void fromDTO(ActivityDTO activityDTO) {
    throw new NotImplementedException();
  }

  @Override
  public void fillInVerificationJobInstanceDetails(VerificationJobInstanceBuilder verificationJobInstanceBuilder) {
    verificationJobInstanceBuilder.startTime(getActivityStartTime());
  }

  @Override
  public void validateActivityParams() {}

  @Override
  public String getActivityName() {
    return activities.size() + " kubernetes events";
  }

  @Override
  public boolean deduplicateEvents() {
    return true;
  }

  public static class KubernetesActivityUpdatableEntity
      extends ActivityUpdatableEntity<KubernetesActivity, KubernetesActivity> {
    @Override
    public Class getEntityClass() {
      return KubernetesActivity.class;
    }

    @Override
    public Query<KubernetesActivity> populateKeyQuery(Query<KubernetesActivity> query, KubernetesActivity changeEvent) {
      throw new UnsupportedOperationException("KubernetesActivity events have no unique key");
    }

    @Override
    public void setUpdateOperations(
        UpdateOperations<KubernetesActivity> updateOperations, KubernetesActivity activity) {
      setCommonUpdateOperations(updateOperations, activity);
    }
  }
}
