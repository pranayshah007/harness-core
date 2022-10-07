/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.collect;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.queue.Queuable;

import software.wings.beans.artifact.Artifact;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.util.Date;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Reference;

@StoreIn(DbAliases.HARNESS)
@Entity(value = "collectorQueue2", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class CollectEvent extends Queuable {
  @Reference(idOnly = true) private Artifact artifact;

  public Artifact getArtifact() {
    return artifact;
  }

  public void setArtifact(Artifact artifact) {
    this.artifact = artifact;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CollectEvent that = (CollectEvent) o;
    return Objects.equal(artifact, that.artifact);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(artifact);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("artifact", artifact).toString();
  }

  public static final class Builder {
    private Artifact artifact;
    private String id;
    private Date earliestGet = new Date();
    private int retries;

    private Builder() {}

    public static Builder aCollectEvent() {
      return new Builder();
    }

    public Builder withArtifact(Artifact artifact) {
      this.artifact = artifact;
      return this;
    }

    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    public Builder withEarliestGet(Date earliestGet) {
      this.earliestGet = earliestGet == null ? null : (Date) earliestGet.clone();
      return this;
    }

    public Builder withRetries(int retries) {
      this.retries = retries;
      return this;
    }

    public Builder but() {
      return aCollectEvent().withArtifact(artifact).withId(id).withEarliestGet(earliestGet).withRetries(retries);
    }

    public CollectEvent build() {
      CollectEvent collectEvent = new CollectEvent();
      collectEvent.setArtifact(artifact);
      collectEvent.setId(id);
      collectEvent.setEarliestGet(earliestGet);
      collectEvent.setRetries(retries);
      return collectEvent;
    }
  }
}
