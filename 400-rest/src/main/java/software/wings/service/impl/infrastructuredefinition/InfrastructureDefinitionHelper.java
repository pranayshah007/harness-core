/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.infrastructuredefinition;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.reflection.ReflectionUtils;

import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMapping.InfrastructureMappingKeys;
import software.wings.dl.WingsPersistence;
import software.wings.infra.FieldKeyValMapProvider;
import software.wings.infra.InfraMappingInfrastructureProvider;
import software.wings.infra.InfrastructureDefinition;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;
import org.mongodb.morphia.query.Query;

@Slf4j
// INFO:
// public for the purpose of UpdateNameInAwsInfrastructureMappingMigration. Can remove once this is done.
public class InfrastructureDefinitionHelper {
  @Inject private WingsPersistence wingsPersistence;

  // Todo: This method does not handle maps/Lists etc. very well. Works well with Strings as of now. Enhance this to
  // take care of non String fields.
  public String getNameFromInfraDefinition(InfrastructureDefinition infrastructureDefinition, String serviceId) {
    StringBuilder stringBuilder = new StringBuilder()
                                      .append(infrastructureDefinition.getAppId())
                                      .append(infrastructureDefinition.getEnvId())
                                      .append(serviceId)
                                      .append(infrastructureDefinition.getUuid());
    Map<String, Object> queryMap = getQueryMap(infrastructureDefinition.getInfrastructure());

    if (isNotEmpty(queryMap)) {
      queryMap.entrySet()
          .stream()
          .filter(entry -> entry.getValue() != null)
          .map(entry -> entry.getValue().toString())
          .forEach(stringBuilder::append);
    }
    return DigestUtils.sha1Hex(stringBuilder.toString());
  }

  @NotNull
  Map<String, Object> getQueryMap(InfraMappingInfrastructureProvider infrastructureProvider) {
    Map<String, Object> queryMap = ((FieldKeyValMapProvider) infrastructureProvider).getFieldMapForClass();
    Set<String> uniqueInfraFields = infrastructureProvider.getUserDefinedUniqueInfraFields();
    Map<String, Object> fieldValues = ReflectionUtils.getFieldValues(infrastructureProvider, uniqueInfraFields);
    queryMap.putAll(fieldValues);
    return queryMap;
  }

  InfrastructureMapping existingInfraMapping(InfrastructureDefinition infraDefinition, String serviceId) {
    Query baseQuery = getQuery(infraDefinition, serviceId);
    List<InfrastructureMapping> infrastructureMappings = baseQuery.asList();
    return isEmpty(infrastructureMappings) ? null : infrastructureMappings.get(0);
  }

  // Todo: Enhance this method to also query based on hash like getNameFromInfraDefinition method
  @NotNull
  private Query getQuery(InfrastructureDefinition infraDefinition, String serviceId) {
    Map<String, Object> queryMap = getQueryMap(infraDefinition.getInfrastructure());

    Class<? extends InfrastructureMapping> mappingClass = infraDefinition.getInfrastructure().getMappingClass();
    Query baseQuery = wingsPersistence.createQuery(mappingClass)
                          .filter(InfrastructureMapping.APP_ID_KEY, infraDefinition.getAppId())
                          .filter(InfrastructureMapping.ENV_ID_KEY, infraDefinition.getEnvId())
                          .filter(InfrastructureMappingKeys.serviceId, serviceId)
                          .filter(InfrastructureMappingKeys.infrastructureDefinitionId, infraDefinition.getUuid());
    queryMap.forEach(baseQuery::filter);
    return baseQuery;
  }
}
