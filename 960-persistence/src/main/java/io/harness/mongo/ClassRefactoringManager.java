/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import io.harness.mongo.MorphiaMove.MorphiaMoveKeys;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.HPersistence;
import io.harness.reflection.CodeUtils;

import com.mongodb.MongoCommandException;
import dev.morphia.AdvancedDatastore;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class ClassRefactoringManager {
  public static Map<String, String> movements(Map<String, Class> morphiaInterfaceImplementers) {
    Map<String, String> movements = new HashMap<>();
    for (Map.Entry<String, Class> entry : morphiaInterfaceImplementers.entrySet()) {
      if (entry.getValue() == MorphiaRegistrar.NotFoundClass.class) {
        continue;
      }
      String target = entry.getValue().getName();
      if (entry.getKey().equals(target)) {
        continue;
      }

      movements.put(entry.getKey(), target);
    }
    return movements;
  }

  public static Map<String, String> movementsToMyModule(
      Class location, Map<String, Class> morphiaInterfaceImplementers) {
    Map<String, String> myModuleMovements = movements(morphiaInterfaceImplementers);
    myModuleMovements.entrySet().removeIf(e -> {
      try {
        return !CodeUtils.thirdPartyOrBelongsToModule(CodeUtils.location(location), Class.forName(e.getValue()));
      } catch (ClassNotFoundException exception) {
        log.error("", exception);
      }
      return false;
    });
    return myModuleMovements;
  }

  public static void updateMovedClasses(
      AdvancedDatastore primaryDatastore, Map<String, Class> morphiaInterfaceImplementers) {
    try {
      Map<String, String> movements = movements(morphiaInterfaceImplementers);
      movements.forEach((source, target) -> {
        Query<MorphiaMove> query =
            primaryDatastore.createQuery(MorphiaMove.class).filter(MorphiaMoveKeys.target, target);
        UpdateOperations<MorphiaMove> updateOperations =
            primaryDatastore.createUpdateOperations(MorphiaMove.class).addToSet(MorphiaMoveKeys.sources, source);
        primaryDatastore.findAndModify(query, updateOperations, HPersistence.upsertReturnNewOptions);
      });
    } catch (MongoCommandException exception) {
      if (exception.getErrorCode() == 13) {
        log.warn("The user has read only access.");
        return;
      }
      throw exception;
    }
  }
}
