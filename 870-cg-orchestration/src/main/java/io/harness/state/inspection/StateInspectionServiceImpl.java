/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.state.inspection;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.observer.Subject;
import io.harness.persistence.HPersistence;
import io.harness.state.inspection.StateInspection.StateInspectionKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class StateInspectionServiceImpl implements StateInspectionService {
  @Inject HPersistence persistence;

  @Getter Subject<StateInspectionListener> subject = new Subject<>();

  @Override
  public StateInspection get(String stateExecutionInstanceId) {
    return persistence.createQuery(StateInspection.class)
        .filter(StateInspectionKeys.stateExecutionInstanceId, stateExecutionInstanceId)
        .get();
  }

  @Override
  public List<StateInspection> listUsingSecondary(Collection<String> stateExecutionInstanceIds) {
    if (isEmpty(stateExecutionInstanceIds)) {
      return new ArrayList<>();
    }

    return persistence.createAnalyticsQuery(StateInspection.class, excludeAuthority)
        .field(StateInspectionKeys.stateExecutionInstanceId)
        .in(stateExecutionInstanceIds)
        .asList(new FindOptions().readPreference(ReadPreference.secondaryPreferred()));
  }

  @Override
  public void append(String stateExecutionInstanceId, StateInspectionData data) {
    append(stateExecutionInstanceId, asList(data));
  }

  @Override
  public void append(String stateExecutionInstanceId, List<StateInspectionData> data) {
    final Query<StateInspection> query =
        persistence.createQuery(StateInspection.class)
            .filter(StateInspectionKeys.stateExecutionInstanceId, stateExecutionInstanceId)
            .project(StateInspectionKeys.stateExecutionInstanceId, true);

    final UpdateOperations<StateInspection> updateOperations =
        persistence.createUpdateOperations(StateInspection.class);

    updateOperations.setOnInsert(StateInspectionKeys.stateExecutionInstanceId, stateExecutionInstanceId);

    for (StateInspectionData item : data) {
      updateOperations.set(StateInspectionKeys.data + "." + item.key(), item);
    }

    // TODO: there is a bug in morphia for obtaining the old value with projection. Change this to send notification
    //       only for inserts when this is fixed.
    persistence.upsert(query, updateOperations);

    subject.fireInform(StateInspectionListener::appendedDataFor, stateExecutionInstanceId);
  }
}
