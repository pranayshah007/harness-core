package io.harness.freeze.helpers;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.freeze.beans.EntityConfig;
import io.harness.freeze.beans.FilterType;
import io.harness.freeze.beans.FreezeEntityRule;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.mappers.NGFreezeDtoMapper;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
public class FreezeRBACHelper {
  public void checkAccess(
      String accountId, String projectId, String orgId, String yaml, AccessControlClient accessControlClient) {
    FreezeConfig freezeConfig = NGFreezeDtoMapper.toFreezeConfig(yaml);
    if (freezeConfig.getFreezeInfoConfig() == null) {
      return;
    }
    List<FreezeEntityRule> freezeEntityRules = freezeConfig.getFreezeInfoConfig().getRules();
    for (FreezeEntityRule freezeEntityRule : freezeEntityRules) {
      if (freezeEntityRule.getEntityConfigList() == null) {
        continue;
      }
      List<EntityConfig> entityConfigList = freezeEntityRule.getEntityConfigList();
      for (EntityConfig entityConfig : entityConfigList) {
        if (entityConfig.getFilterType().equals(FilterType.EQUALS)) {
          FreezeEntityType entityType = entityConfig.getFreezeEntityType();
          Optional<Pair<String, String>> resourceAndPermission = getResourceTypeAndPermission(entityType);
          if (resourceAndPermission.isPresent()) {
            List<String> referenceList = entityConfig.getEntityReference();
            for (String reference : referenceList) {
              accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
                  Resource.of(resourceAndPermission.get().getKey(), reference), resourceAndPermission.get().getValue());
            }
          }
        }
      }
    }
  }
  public Optional<Pair<String, String>> getResourceTypeAndPermission(FreezeEntityType type) {
    Pair<String, String> result = null;
    switch (type) {
      case SERVICE: {
        result = MutablePair.of("SERVICE", "core_service_view");
        break;
      }
      case PROJECT: {
        result = MutablePair.of("PROJECT", "core_project_view");
        break;
      }
      case ORG: {
        result = MutablePair.of("ORGANIZATION", "core_organization_view");
        break;
      }
      case ENVIRONMENT: {
        result = MutablePair.of("ENVIRONMENT", "core_environment_view");
        break;
      }
      default: {
        break;
      }
    }
    return Optional.ofNullable(result);
  }
}
