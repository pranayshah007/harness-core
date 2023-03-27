/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.repositories;

import io.harness.assessment.settings.beans.entities.Assessment;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class AssessmentRepositoryCustomImpl implements AssessmentRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public Assessment saveOrUpdate(Assessment assessment) {
    //    StatusInfoEntity entity = findByAccountIdAndType(statusInfoEntity);
    //    if (entity == null) {
    return mongoTemplate.save(assessment);
    //    } else {
    //      Criteria criteria = Criteria.where(StatusInfoEntityKeys.id).is(entity.getId());
    //      Query query = new Query(criteria);
    //      Update update = buildUpdateQuery(statusInfoEntity);
    //      FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    //      return mongoTemplate.findAndModify(query, update, options, StatusInfoEntity.class);
  }
}

/*
  private StatusInfoEntity findByAccountIdAndType(StatusInfoEntity statusInfoEntity) {
    Criteria criteria = Criteria.where(StatusInfoEntityKeys.accountIdentifier)
                            .is(statusInfoEntity.getAccountIdentifier())
                            .and(StatusInfoEntityKeys.type)
                            .is(statusInfoEntity.getType());
    return mongoTemplate.findOne(Query.query(criteria), StatusInfoEntity.class);
  }

  private Update buildUpdateQuery(StatusInfoEntity statusInfoEntity) {
    Update update = new Update();
    update.set(StatusInfoEntityKeys.status, statusInfoEntity.getStatus());
    update.set(StatusInfoEntityKeys.reason, statusInfoEntity.getReason());
    if (statusInfoEntity.getLastModifiedAt() == null) {
      update.set(StatusInfoEntityKeys.lastModifiedAt, System.currentTimeMillis());
    } else {
      update.set(StatusInfoEntityKeys.lastModifiedAt, statusInfoEntity.getLastModifiedAt());
    }
    return update;
  }
}
*/
