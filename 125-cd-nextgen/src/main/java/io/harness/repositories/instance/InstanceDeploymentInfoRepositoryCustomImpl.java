/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.instance;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.instance.InstanceDeploymentInfo;
import io.harness.cdng.instance.InstanceDeploymentInfo.InstanceDeploymentInfoKeys;
import io.harness.cdng.instance.InstanceDeploymentInfoStatus;
import io.harness.data.structure.EmptyPredicate;
import io.harness.entities.ArtifactDetails;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class InstanceDeploymentInfoRepositoryCustomImpl implements InstanceDeploymentInfoRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public UpdateResult updateStatus(ExecutionInfoKey executionInfoKey, InstanceDeploymentInfoStatus status) {
    Criteria criteria = createExecutionCriteria(executionInfoKey);

    Query query = new Query();
    query.addCriteria(criteria);

    Update update = new Update();
    update.set(InstanceDeploymentInfoKeys.status, status);
    return mongoTemplate.updateMulti(query, update, InstanceDeploymentInfoKeys.class);
  }

  @Override
  public UpdateResult updateStatus(
      ExecutionInfoKey executionInfoKey, String host, InstanceDeploymentInfoStatus status) {
    Criteria criteria =
        createExecutionCriteria(executionInfoKey).and(InstanceDeploymentInfoKeys.instanceInfo + ".host").is(host);

    Query query = new Query();
    query.addCriteria(criteria);

    Update update = new Update();
    update.set(InstanceDeploymentInfoKeys.status, status);
    return mongoTemplate.updateMulti(query, update, InstanceDeploymentInfo.class);
  }

  @Override
  public UpdateResult updateArtifactAndStatus(ExecutionInfoKey executionInfoKey, String deploymentIdentifier,
      List<String> hosts, ArtifactDetails artifactDetails, InstanceDeploymentInfoStatus status) {
    Criteria criteria =
        createExecutionCriteria(executionInfoKey).and(InstanceDeploymentInfoKeys.instanceInfo + ".host").in(hosts);

    Query query = new Query();
    query.addCriteria(criteria);

    Update update = new Update();
    update.set(InstanceDeploymentInfoKeys.status, status);
    update.set(InstanceDeploymentInfoKeys.deploymentIdentifier, deploymentIdentifier);
    update.set(InstanceDeploymentInfoKeys.artifactDetails, artifactDetails);
    return mongoTemplate.updateMulti(query, update, InstanceDeploymentInfo.class);
  }

  @Override
  public DeleteResult deleteByExecutionInfoKey(ExecutionInfoKey executionInfoKey) {
    Criteria criteria = createExecutionCriteria(executionInfoKey);

    Query query = new Query();
    query.addCriteria(criteria);

    return mongoTemplate.remove(query, InstanceDeploymentInfo.class);
  }

  @Override
  public List<InstanceDeploymentInfo> listByHosts(ExecutionInfoKey executionInfoKey, List<String> hosts) {
    Criteria criteria =
        createExecutionCriteria(executionInfoKey).and(InstanceDeploymentInfoKeys.instanceInfo + ".host").in(hosts);

    Query query = new Query();
    query.addCriteria(criteria);

    return mongoTemplate.find(query, InstanceDeploymentInfo.class);
  }

  public Criteria createExecutionCriteria(ExecutionInfoKey key) {
    Criteria criteria = new Criteria();
    criteria.and(InstanceDeploymentInfoKeys.accountIdentifier).is(key.getScope().getAccountIdentifier());
    criteria.and(InstanceDeploymentInfoKeys.orgIdentifier).is(key.getScope().getOrgIdentifier());
    criteria.and(InstanceDeploymentInfoKeys.projectIdentifier).is(key.getScope().getProjectIdentifier());
    criteria.and(InstanceDeploymentInfoKeys.envIdentifier).is(key.getEnvIdentifier());
    criteria.and(InstanceDeploymentInfoKeys.serviceIdentifier).is(key.getServiceIdentifier());
    criteria.and(InstanceDeploymentInfoKeys.infraIdentifier).is(key.getInfraIdentifier());
    if (EmptyPredicate.isNotEmpty(key.getDeploymentIdentifier())) {
      criteria.and(InstanceDeploymentInfoKeys.deploymentIdentifier).is(key.getDeploymentIdentifier());
    }
    return criteria;
  }
}
