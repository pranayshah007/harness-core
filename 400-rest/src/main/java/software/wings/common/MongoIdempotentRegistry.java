/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.common;

import static io.harness.distribution.idempotence.IdempotentRegistry.State.DONE;
import static io.harness.distribution.idempotence.IdempotentRegistry.State.NEW;
import static io.harness.distribution.idempotence.IdempotentRegistry.State.RUNNING;

import static software.wings.beans.Idempotent.SUCCEEDED;
import static software.wings.beans.Idempotent.TENTATIVE;

import static com.mongodb.ErrorCategory.DUPLICATE_KEY;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.distribution.idempotence.IdempotentId;
import io.harness.distribution.idempotence.IdempotentLock;
import io.harness.distribution.idempotence.IdempotentRegistry;
import io.harness.distribution.idempotence.IdempotentResult;
import io.harness.distribution.idempotence.UnableToRegisterIdempotentOperationException;

import software.wings.beans.Idempotent;
import software.wings.beans.Idempotent.IdempotentKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoCommandException;
import com.mongodb.WriteConcern;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.sql.Date;
import java.time.Duration;
import java.time.OffsetDateTime;

@Singleton
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._960_PERSISTENCE)
public class MongoIdempotentRegistry<T extends IdempotentResult> implements IdempotentRegistry<T> {
  public static final FindAndModifyOptions registerOptions =
      new FindAndModifyOptions().returnNew(false).upsert(true).writeConcern(new WriteConcern("majority"));
  public static final FindAndModifyOptions unregisterOptions =
      new FindAndModifyOptions().remove(true).writeConcern(new WriteConcern("majority"));

  @Inject private WingsPersistence wingsPersistence;

  public UpdateOperations<Idempotent> registerUpdateOperation(Duration ttl) {
    return wingsPersistence.createUpdateOperations(Idempotent.class)
        .set(IdempotentKeys.state, TENTATIVE)
        .set(IdempotentKeys.validUntil, Date.from(OffsetDateTime.now().plus(ttl).toInstant()));
  }

  public UpdateOperations<Idempotent> unregisterUpdateOperation() {
    return wingsPersistence.createUpdateOperations(Idempotent.class);
  }

  @Override
  public IdempotentLock create(IdempotentId id) {
    return IdempotentLock.create(id, this);
  }

  @Override
  public IdempotentLock create(IdempotentId id, Duration timeout, Duration pollingInterval, Duration ttl) {
    return IdempotentLock.create(id, this, timeout, pollingInterval, ttl);
  }

  public Query<Idempotent> query(IdempotentId id) {
    return wingsPersistence.createQuery(Idempotent.class)
        .filter(IdempotentKeys.uuid, id.getValue())
        .field(IdempotentKeys.state)
        .notEqual(SUCCEEDED);
  }

  @Override
  public Response register(IdempotentId id, Duration ttl) {
    try {
      // Insert new record in the idempotent collection with a tentative state
      final Idempotent idempotent =
          wingsPersistence.findAndModify(query(id), registerUpdateOperation(ttl), registerOptions);

      // If there was no record from before, we are the first to handle this operation
      if (idempotent == null) {
        return Response.builder().state(NEW).build();
      }
    } catch (MongoCommandException exception) {
      // If we failed with duplicate key - there is already successful operation in the db
      if (ErrorCategory.fromErrorCode(exception.getErrorCode()) == DUPLICATE_KEY) {
        Idempotent idempotent = wingsPersistence.get(Idempotent.class, id.getValue());
        return Response.builder().state(DONE).result((T) idempotent.getResult().get(0)).build();
      }
      throw new UnableToRegisterIdempotentOperationException(exception);
    } catch (RuntimeException exception) {
      throw new UnableToRegisterIdempotentOperationException(exception);
    }

    // If there was already record, but it was not successful, it is still running
    return Response.builder().state(RUNNING).build();
  }

  @Override
  public void unregister(IdempotentId id) {
    // Delete the operation record
    wingsPersistence.findAndModify(query(id), unregisterUpdateOperation(), unregisterOptions);
  }

  @Override
  public <T extends IdempotentResult> void finish(IdempotentId id, T data) {
    Idempotent newIdempotent =
        Idempotent.builder().uuid(id.getValue()).state(SUCCEEDED).result(singletonList(data)).build();
    wingsPersistence.save(newIdempotent);
  }
}
