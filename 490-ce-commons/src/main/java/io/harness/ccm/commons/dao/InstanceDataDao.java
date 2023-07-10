/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.batch.InstanceData.InstanceDataKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class InstanceDataDao {
  private static final String SUB_FIELD_FORMAT = "%s.%s";
  private static final String NOT_EQUAL = "$ne";
  private static final String AND = "$and";

  @Inject private HPersistence hPersistence;

  public boolean create(InstanceData instanceData) {
    return hPersistence.save(instanceData) != null;
  }

  public InstanceData get(String instanceId) {
    return hPersistence.createQuery(InstanceData.class, excludeAuthority)
        .filter(InstanceDataKeys.instanceId, instanceId)
        .get();
  }

  public List<InstanceData> fetchInstanceDataForGivenInstances(String accountId, List<String> instanceIds) {
    Query<InstanceData> query = hPersistence.createQuery(InstanceData.class, excludeAuthority)
                                    .field(InstanceDataKeys.accountId)
                                    .equal(accountId)
                                    .field(InstanceDataKeys.instanceId)
                                    .in(instanceIds);
    return fetchInstanceData(query.fetch().iterator());
  }

  public List<InstanceData> fetchInstanceDataForGivenInstances(
      String accountId, String clusterId, List<String> instanceIds) {
    Query<InstanceData> query = hPersistence.createQuery(InstanceData.class, excludeAuthority)
                                    .field(InstanceDataKeys.accountId)
                                    .equal(accountId)
                                    .field(InstanceDataKeys.clusterId)
                                    .equal(clusterId)
                                    .field(InstanceDataKeys.instanceId)
                                    .in(instanceIds);
    return fetchInstanceData(query.fetch().iterator());
  }

  public InstanceData fetchInstanceData(
      String accountId, String clusterId, InstanceType instanceType, String nodePoolName, InstanceState instanceState) {
    return hPersistence.createQuery(InstanceData.class)
        .filter(InstanceDataKeys.accountId, accountId)
        .filter(InstanceDataKeys.clusterId, clusterId)
        .filter(InstanceDataKeys.instanceType, instanceType)
        // Currently, we are only computing recommendation for non-null node_pool_name
        .filter(String.format(SUB_FIELD_FORMAT, InstanceDataKeys.metaData, InstanceMetaDataConstants.NODE_POOL_NAME),
            nodePoolName)
        .filter(InstanceDataKeys.instanceState, instanceState)
        .field(String.format(SUB_FIELD_FORMAT, InstanceDataKeys.metaData, InstanceMetaDataConstants.INSTANCE_FAMILY))
        .notEqual(null)
        .get();
  }

  @SuppressWarnings("unchecked")
  public List<String> fetchDistinctInstanceFamilies(
      String accountId, String clusterId, InstanceType instanceType, String nodePoolName, InstanceState instanceState) {
    BasicDBObject instanceFamiliesQuery = new BasicDBObject();
    List<BasicDBObject> conditions = new ArrayList<>();
    conditions.add(new BasicDBObject(InstanceDataKeys.accountId, accountId));
    conditions.add(new BasicDBObject(InstanceDataKeys.clusterId, clusterId));
    conditions.add(new BasicDBObject(InstanceDataKeys.instanceType, instanceType));
    // Currently, we are only computing recommendation for non-null node_pool_name
    conditions.add(new BasicDBObject(
        String.format(SUB_FIELD_FORMAT, InstanceDataKeys.metaData, InstanceMetaDataConstants.NODE_POOL_NAME),
        nodePoolName));
    conditions.add(new BasicDBObject(InstanceDataKeys.instanceState, instanceState));
    // noinspection ConstantConditions
    conditions.add(new BasicDBObject(
        String.format(SUB_FIELD_FORMAT, InstanceDataKeys.metaData, InstanceMetaDataConstants.INSTANCE_FAMILY),
        new BasicDBObject(NOT_EQUAL, null)));
    instanceFamiliesQuery.put(AND, conditions);
    return hPersistence.getCollection(InstanceData.class)
        .distinct(String.format(SUB_FIELD_FORMAT, InstanceDataKeys.metaData, InstanceMetaDataConstants.INSTANCE_FAMILY),
            instanceFamiliesQuery);
  }

  private List<InstanceData> fetchInstanceData(Iterator<InstanceData> iterator) {
    List<InstanceData> instanceData = new ArrayList<>();
    while (iterator.hasNext()) {
      instanceData.add(iterator.next());
    }
    return instanceData;
  }
}
