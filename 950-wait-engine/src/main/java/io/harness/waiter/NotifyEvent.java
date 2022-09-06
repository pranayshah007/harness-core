/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.waiter;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.queue.Queuable;

import com.google.common.base.MoreObjects;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.ALL)
@Document("notifyQueue")
@Entity(value = "notifyQueue", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "NotifyEventKeys")
public class NotifyEvent extends Queuable {
  @FdUniqueIndex private String waitInstanceId;
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add(NotifyEventKeys.waitInstanceId, waitInstanceId).toString();
  }

  public static final class Builder {
    private String waitInstanceId;
    private String id;
    private Date earliestGet = new Date();
    private int retries;

    private Builder() {}

    public static Builder aNotifyEvent() {
      return new Builder();
    }

    public Builder waitInstanceId(String waitInstanceId) {
      this.waitInstanceId = waitInstanceId;
      return this;
    }

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder earliestGet(Date earliestGet) {
      this.earliestGet = earliestGet == null ? null : (Date) earliestGet.clone();
      return this;
    }

    public Builder retries(int retries) {
      this.retries = retries;
      return this;
    }

    public NotifyEvent build() {
      NotifyEvent notifyEvent = new NotifyEvent();
      notifyEvent.setWaitInstanceId(waitInstanceId);
      notifyEvent.setId(id);
      notifyEvent.setEarliestGet(earliestGet);
      notifyEvent.setRetries(retries);
      return notifyEvent;
    }
  }
}
