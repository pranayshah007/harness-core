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
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.observer.Subject;
import io.harness.persistence.HPersistence;
import io.harness.state.inspection.StateInspection.StateInspectionKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class StateInspectionServiceImpl implements StateInspectionService {
  @Inject HPersistence persistence;

  @Getter Subject<StateInspectionListener> subject = new Subject<>();

  private final String K8S_RESOURCES_MANIFESTS = "k8sResources.manifests";
  private final String EXPRESSION_VARIABLE_USAGE = "expressionVariableUsage";

  public void transformToYaml(StateInspection stateInspection) {
    ExpressionVariableUsage v = null;
    try {
      v = (ExpressionVariableUsage) stateInspection.getData().get(EXPRESSION_VARIABLE_USAGE);
    } catch (Exception e) {
      log.info("There is no variable expression usage for stateExecutionInstance {}",
          stateInspection.getStateExecutionInstanceId());
    }
    if (v != null) {
      List<ExpressionVariableUsage.Item> vars = v.getVariables();

      for (ExpressionVariableUsage.Item var : vars) {
        if (var.getExpression().equals(K8S_RESOURCES_MANIFESTS)) {
          vars.remove(var);
          vars.add(ExpressionVariableUsage.Item.builder()
                       .expression(var.getExpression())
                       .value(ManifestHelper.toYamlForLogs(ManifestHelper.processYaml(var.getValue())))
                       .count(var.getCount())
                       .build());
        }
      }
    }
  }

  @Override
  public StateInspection get(String stateExecutionInstanceId) {
    StateInspection stateInspection =
        persistence.createQuery(StateInspection.class)
            .filter(StateInspectionKeys.stateExecutionInstanceId, stateExecutionInstanceId)
            .get();

    transformToYaml(stateInspection);
    return stateInspection;
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
