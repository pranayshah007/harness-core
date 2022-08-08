/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.cluster.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.CONTAINS;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.EXISTS;
import static io.harness.beans.SearchFilter.Operator.GE;
import static io.harness.beans.SearchFilter.Operator.LT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.ccm.cluster.entities.K8sLabelFilter;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.ccm.commons.entities.k8s.K8sWorkload.K8sWorkloadKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Criteria;
import org.mongodb.morphia.query.Query;

@Slf4j
@Singleton
@OwnedBy(CE)
@TargetModule(HarnessModule._490_CE_COMMONS)
public class K8sWorkloadDao {
  private static final String LABEL_FIELD = K8sWorkloadKeys.labels + ".";
  @Inject private HPersistence persistence;

  public void save(K8sWorkload k8sWorkload) {
    persistence.save(k8sWorkload);
  }

  // to get the workloads with at least one of the label(key:value) present
  public List<K8sWorkload> list(String accountId, String clusterId, Map<String, List<String>> labels) {
    if (labels == null) {
      return new ArrayList<>();
    }
    labels = labels.entrySet().stream().collect(Collectors.toMap(e -> encode(e.getKey()), Map.Entry::getValue));

    Query<K8sWorkload> query = persistence.createQuery(K8sWorkload.class, excludeValidate)
                                   .field(K8sWorkloadKeys.accountId)
                                   .equal(accountId)
                                   .field(K8sWorkloadKeys.clusterId)
                                   .equal(clusterId);
    List<Criteria> criteriaList = new ArrayList<>();

    labels.forEach(
        (name, values) -> values.forEach(value -> criteriaList.add(query.criteria(LABEL_FIELD + name).equal(value))));

    query.or(criteriaList.toArray(new Criteria[0]));
    return fetchWorkloads(query.fetch().iterator());
  }

  // to get the workloads with at least one of the label(key:value) present (time filters are applied on last updated
  // at)
  public List<K8sWorkload> list(String accountId, long startTime, long endTime, Map<String, List<String>> labels) {
    if (isEmpty(labels)) {
      return new ArrayList<>();
    }
    labels = labels.entrySet().stream().collect(Collectors.toMap(e -> encode(e.getKey()), Map.Entry::getValue));

    Query<K8sWorkload> query = persistence.createQuery(K8sWorkload.class, excludeValidate)
                                   .field(K8sWorkloadKeys.accountId)
                                   .equal(accountId)
                                   .field(K8sWorkloadKeys.lastUpdatedAt)
                                   .greaterThanOrEq(startTime)
                                   .field(K8sWorkloadKeys.lastUpdatedAt)
                                   .lessThan(endTime);
    List<Criteria> criteriaList = new ArrayList<>();

    labels.forEach(
        (name, values) -> values.forEach(value -> criteriaList.add(query.criteria(LABEL_FIELD + name).equal(value))));

    query.or(criteriaList.toArray(new Criteria[0]));
    return fetchWorkloads(query.fetch().iterator());
  }

  // to get the list of workloads having workload names in the given set and one of the label key equal to label name
  public List<K8sWorkload> list(String accountId, Set<String> workloadNames, String labelName) {
    labelName = encode(labelName);
    Query<K8sWorkload> query = persistence.createQuery(K8sWorkload.class, excludeValidate)
                                   .field(K8sWorkloadKeys.accountId)
                                   .equal(accountId)
                                   .field(K8sWorkloadKeys.name)
                                   .in(workloadNames);
    query.criteria(LABEL_FIELD + labelName).exists();
    return fetchWorkloads(query.fetch().iterator());
  }

  // To get label names
  public List<String> listLabelKeys(K8sLabelFilter labelFilter) {
    PageRequest<K8sWorkload> request = aPageRequest()
                                           .addFilter(K8sWorkloadKeys.accountId, EQ, labelFilter.getAccountId())
                                           .addFilter(K8sWorkloadKeys.lastUpdatedAt, GE, labelFilter.getStartTime())
                                           .addFilter(K8sWorkloadKeys.lastUpdatedAt, LT, labelFilter.getEndTime())
                                           .withLimit(String.valueOf(labelFilter.getLimit()))
                                           .withOffset(String.valueOf(labelFilter.getOffset()))
                                           .build();
    List<K8sWorkload> workloads = fetchWorkloads(persistence.query(K8sWorkload.class, request).iterator());
    Set<String> labelNames = new HashSet<>();
    workloads.forEach(workload -> labelNames.addAll(workload.getLabels().keySet()));
    return new ArrayList<>(labelNames);
  }

  // To get label values for given label name
  public List<String> listLabelValues(K8sLabelFilter labelFilter) {
    PageRequest<K8sWorkload> request = aPageRequest()
                                           .addFilter(K8sWorkloadKeys.accountId, EQ, labelFilter.getAccountId())
                                           .addFilter(K8sWorkloadKeys.lastUpdatedAt, GE, labelFilter.getStartTime())
                                           .addFilter(K8sWorkloadKeys.lastUpdatedAt, LT, labelFilter.getEndTime())
                                           .addFilter(LABEL_FIELD + encode(labelFilter.getLabelName()), EXISTS)
                                           .withLimit(String.valueOf(labelFilter.getLimit()))
                                           .withOffset(String.valueOf(labelFilter.getOffset()))
                                           .build();
    if (!labelFilter.getSearchString().equals("")) {
      request.addFilter(LABEL_FIELD + labelFilter.getLabelName(), CONTAINS, labelFilter.getSearchString());
    }
    List<K8sWorkload> workloads = fetchWorkloads(persistence.query(K8sWorkload.class, request).iterator());
    Set<String> labelNames = new HashSet<>();
    String labelName = labelFilter.getLabelName();
    workloads.forEach(workload -> {
      Map<String, String> labels = workload.getLabels();
      labelNames.add(labels.get(labelName));
    });
    return new ArrayList<>(labelNames);
  }

  // to get the list of workloads having workload names in the given set
  public List<K8sWorkload> list(String accountId, Set<String> workloadNames) {
    Query<K8sWorkload> query = persistence.createQuery(K8sWorkload.class, excludeValidate)
                                   .field(K8sWorkloadKeys.accountId)
                                   .equal(accountId)
                                   .field(K8sWorkloadKeys.name)
                                   .in(workloadNames);
    return fetchWorkloads(query.fetch().iterator());
  }

  public List<K8sWorkload> list(String accountId, String workloadName) {
    Query<K8sWorkload> query = persistence.createQuery(K8sWorkload.class, excludeValidate)
                                   .field(K8sWorkloadKeys.accountId)
                                   .equal(accountId)
                                   .field(K8sWorkloadKeys.name)
                                   .equal(workloadName);
    return fetchWorkloads(query.fetch().iterator());
  }

  public List<K8sWorkload> list(Query<K8sWorkload> query) {
    return fetchWorkloads(query.fetch().iterator());
  }

  private String encode(String decoded) {
    return decoded.replace('.', '~');
  }

  private List<K8sWorkload> fetchWorkloads(Iterator<K8sWorkload> iterator) {
    List<K8sWorkload> workloads = new ArrayList<>();
    while (iterator.hasNext()) {
      workloads.add(iterator.next());
    }
    return workloads;
  }
}
