/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.graphql.utils.GraphQLConstants.ADD_ACCOUNT_PERMISSION;
import static software.wings.graphql.utils.GraphQLConstants.ADD_APP_PERMISSION;
import static software.wings.graphql.utils.GraphQLConstants.ADD_USER_TO_USERGROUP;
import static software.wings.graphql.utils.GraphQLConstants.APP_ID_ARG;
import static software.wings.graphql.utils.GraphQLConstants.CREATE_API_KEY;
import static software.wings.graphql.utils.GraphQLConstants.CREATE_APPLICATION_API;
import static software.wings.graphql.utils.GraphQLConstants.CREATE_USERGROUP_API;
import static software.wings.graphql.utils.GraphQLConstants.DELETE_API_KEY;
import static software.wings.graphql.utils.GraphQLConstants.DELETE_APPLICATION_API;
import static software.wings.graphql.utils.GraphQLConstants.DELETE_USERGROUP_API;
import static software.wings.graphql.utils.GraphQLConstants.REMOVE_USER_FROM_USERGROUP;
import static software.wings.graphql.utils.GraphQLConstants.UPDATE_API_KEY;
import static software.wings.graphql.utils.GraphQLConstants.UPDATE_USERGROUP_API;
import static software.wings.graphql.utils.GraphQLConstants.UPDATE_USERGROUP_PERMISSIONS_API;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;

import software.wings.beans.Environment;
import software.wings.beans.HttpMethod;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.resources.graphql.TriggeredByType;
import software.wings.security.AuthRuleFilter;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserRestrictionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.UserService;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dataloader.DataLoader;

/**
 * @author rktummala
 */
@Slf4j
@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
@OwnedBy(DX)
public class AuthRuleGraphQL<P, T, B extends PersistentEntity> {
  private static final Set<String> apisToEvictUserPermissionRestrictionCache =
      ImmutableSet.of(CREATE_APPLICATION_API, DELETE_APPLICATION_API, CREATE_USERGROUP_API, DELETE_USERGROUP_API,
          UPDATE_USERGROUP_API, UPDATE_USERGROUP_PERMISSIONS_API, ADD_USER_TO_USERGROUP, REMOVE_USER_FROM_USERGROUP,
          ADD_ACCOUNT_PERMISSION, ADD_APP_PERMISSION, CREATE_API_KEY, UPDATE_API_KEY, DELETE_API_KEY);

  private AuthRuleFilter authRuleFilter;
  private AuthHandler authHandler;
  private AuthService authService;
  private HPersistence persistence;
  @Inject private UserService userService;

  @Inject
  public AuthRuleGraphQL(
      AuthRuleFilter authRuleFilter, AuthHandler authHandler, AuthService authService, HPersistence persistence) {
    this.authRuleFilter = authRuleFilter;
    this.authHandler = authHandler;
    this.authService = authService;
    this.persistence = persistence;
  }

  private AuthRule getAuthRuleAnnotationOfDataFetcher(@NotNull DataFetcher dataFetcher) {
    AuthRule authRule;
    Class<? extends DataFetcher> dataFetcherClass = dataFetcher.getClass();
    String fetchMethod;
    Class<?>[] parameterTypes;

    if (dataFetcher instanceof AbstractConnectionDataFetcher) {
      fetchMethod = "fetchConnection";
      parameterTypes = new Class[] {java.lang.Object.class};
    } else if (dataFetcher instanceof AbstractConnectionV2DataFetcher) {
      fetchMethod = "fetchConnection";
      parameterTypes = new Class[] {List.class, QLPageQueryParameters.class, List.class};
      //      List<F> filters, QLPageQueryParameters pageQueryParameters, List<S> sortCriteria
    } else if (dataFetcher instanceof AbstractBatchDataFetcher) {
      fetchMethod = "load";
      parameterTypes = new Class[] {java.lang.Object.class, DataLoader.class};
    } else if (dataFetcher instanceof BaseMutatorDataFetcher) {
      fetchMethod = "mutateAndFetch";
      parameterTypes = new Class[] {java.lang.Object.class, MutationContext.class};
    } else {
      fetchMethod = "fetch";
      parameterTypes = new Class[] {java.lang.Object.class, String.class};
    }

    try {
      authRule = dataFetcherClass.getDeclaredMethod(fetchMethod, parameterTypes).getAnnotation(AuthRule.class);
    } catch (NoSuchMethodException e) {
      log.error("No fetch() method found in class: " + dataFetcherClass.getSimpleName());
      throw new WingsException(ErrorCode.ACCESS_DENIED);
    }

    return authRule;
  }

  public DataFetcher instrumentDataFetcher(
      BaseDataFetcher dataFetcher, DataFetchingEnvironment environment, Class<T> returnDataClass) {
    Object contextObj = environment.getContext();

    if (!(contextObj instanceof GraphQLContext)) {
      return dataFetcher;
    }

    GraphQLContext context = (GraphQLContext) contextObj;
    String accountId = context.get("accountId");
    if (isEmpty(accountId)) {
      log.error("No user permission info for the given api key");
      throw new WingsException(ErrorCode.ACCESS_DENIED);
    }

    UserPermissionInfo userPermissionInfo = context.get("permissions");
    if (userPermissionInfo == null) {
      log.error("No user permission info for the given api key");
      throw new WingsException(ErrorCode.ACCESS_DENIED);
    }

    UserRestrictionInfo userRestrictionInfo = context.get("restrictions");

    String httpMethod;
    ResourceType resourceType;
    AuthRule authRule = getAuthRuleAnnotationOfDataFetcher(dataFetcher);
    if (authRule == null) {
      log.error("Missing authRule for the request in class: " + dataFetcher.getClass().getSimpleName());
      throw new WingsException(ErrorCode.ACCESS_DENIED);
    }

    Scope scope = returnDataClass.getAnnotation(Scope.class);
    if (scope == null) {
      log.error("Missing scope for the request in class: " + returnDataClass.getSimpleName());
      throw new WingsException(ErrorCode.ACCESS_DENIED);
    }

    ResourceType[] resourceTypes = scope.value();
    resourceType = isEmpty(resourceTypes) ? null : resourceTypes[0];

    Action action = authRule.action() != null ? authRule.action() : Action.DEFAULT;
    httpMethod = getHttpMethod(action.name());
    PermissionAttribute permissionAttribute =
        authRuleFilter.buildPermissionAttribute(authRule, httpMethod, resourceType);

    List<PermissionAttribute> permissionAttributes = asList(permissionAttribute);
    String appId = (String) dataFetcher.getArgumentValue(environment, APP_ID_ARG);

    TriggeredByType triggeredByType = context.get("triggeredByType");
    String triggeredById = context.get("triggeredById");

    User user = new User();

    if (triggeredByType == TriggeredByType.USER) {
      user = userService.get(triggeredById);
    }

    boolean isAccountLevelPermissions = authRuleFilter.isAccountLevelPermissions(permissionAttributes);
    boolean emptyAppIdsInReq = isEmpty(appId);
    List<String> appIdsFromRequest = emptyAppIdsInReq ? null : asList(appId);
    boolean isScopedToApp = ResourceType.APPLICATION == resourceType;

    if (isEmpty(permissionAttributes) || PermissionType.LOGGED_IN == permissionAttribute.getPermissionType()) {
      UserRequestContext userRequestContext = authRuleFilter.buildUserRequestContext(
          userPermissionInfo, userRestrictionInfo, accountId, emptyAppIdsInReq, isScopedToApp, appIdsFromRequest);
      user.setUserRequestContext(userRequestContext);
      UserThreadLocal.set(user);
      return dataFetcher;
    }

    if (resourceType == null) {
      log.error("Missing resource type in the request");
      throw new WingsException(ErrorCode.ACCESS_DENIED);
    }

    UserRequestContext userRequestContext =
        authRuleFilter.buildUserRequestContext(userPermissionInfo, userRestrictionInfo, permissionAttributes, accountId,
            emptyAppIdsInReq, httpMethod, appIdsFromRequest, false, isAccountLevelPermissions, isScopedToApp);
    user.setUserRequestContext(userRequestContext);
    UserThreadLocal.set(user);
    AccountThreadLocal.set(accountId);

    if (isAccountLevelPermissions) {
      authHandler.authorizeAccountPermission(userRequestContext, permissionAttributes);
    } else {
      String parameterName = getParameterName(permissionAttribute);
      String entityId = (String) dataFetcher.getArgumentValue(environment, parameterName);

      // Handle delete and update methods
      if (httpMethod.equals(HttpMethod.PUT.name()) || httpMethod.equals(HttpMethod.DELETE.name())
          || httpMethod.equals(HttpMethod.POST.name())) {
        authService.authorize(accountId, appIdsFromRequest, entityId, user, permissionAttributes);
      } else if (httpMethod.equals(HttpMethod.GET.name())) {
        // In case of list api, the entityId would be null, we enforce restrictions in WingsMongoPersistence
        if (entityId != null && emptyAppIdsInReq && isScopedToApp) {
          appId = getApplicationId(permissionAttribute, entityId);
          if (isNotEmpty(appId)) {
            appIdsFromRequest = asList(appId);
          } else {
            String msg = "Could not retrieve appId for entityId: " + entityId;
            log.error(msg);
            throw new WingsException(msg);
          }
          // get api
          authService.authorize(accountId, appIdsFromRequest, entityId, user, permissionAttributes);
        }
      }
    }

    return dataFetcher;
  }

  private String getApplicationIdInternal(Class<B> clazz, HPersistence persistence, String entityId) {
    B entity = persistence.createQuery(clazz).filter("_id", entityId).project("appId", true).get();
    if (entity == null) {
      throw new WingsException("Entity with id: " + entityId + " is not found");
    }

    try {
      Method getAppIdMethod = entity.getClass().getMethod("getAppId");
      Object appIdObj = getAppIdMethod.invoke(entity);
      if (appIdObj instanceof String) {
        return (String) appIdObj;
      }
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      return null;
    }

    return null;
  }

  private String getApplicationId(PermissionAttribute permissionAttribute, String entityId) {
    PermissionType permissionType = permissionAttribute.getPermissionType();
    Class entityClass;
    switch (permissionType) {
      case SERVICE:
        entityClass = Service.class;
        break;
      case ENV:
        entityClass = Environment.class;
        break;
      case WORKFLOW:
        entityClass = Workflow.class;
        break;
      case PROVISIONER:
        entityClass = InfrastructureProvisioner.class;
        break;
      case PIPELINE:
        entityClass = Pipeline.class;
        break;
      case DEPLOYMENT:
        entityClass = WorkflowExecution.class;
        break;
      default:
        throw new WingsException("Unsupported permission type");
    }

    return getApplicationIdInternal(entityClass, persistence, entityId);
  }

  private String getParameterName(PermissionAttribute permissionAttribute) {
    if (StringUtils.isNotBlank(permissionAttribute.getParameterName())) {
      return permissionAttribute.getParameterName();
    }

    PermissionType permissionType = permissionAttribute.getPermissionType();
    String fieldName = null;

    if (permissionType == PermissionType.SERVICE) {
      fieldName = "serviceId";
    } else if (permissionType == PermissionType.PROVISIONER) {
      fieldName = "infraProvisionerId";
    } else if (permissionType == PermissionType.ENV) {
      fieldName = "environmentId";
    } else if (permissionType == PermissionType.WORKFLOW) {
      fieldName = "workflowId";
    } else if (permissionType == PermissionType.PIPELINE) {
      fieldName = "pipelineId";
    } else if (permissionType == PermissionType.DEPLOYMENT) {
      fieldName = "workflowId";
    }
    return fieldName;
  }

  private String getHttpMethod(String action) {
    if (Action.READ.name().equals(action)) {
      return HttpMethod.GET.name();
    } else if (Action.UPDATE.name().equals(action)) {
      return HttpMethod.PUT.name();
    } else if (Action.CREATE.name().equals(action)) {
      return HttpMethod.POST.name();
    } else if (Action.DELETE.name().equals(action)) {
      return HttpMethod.DELETE.name();
    }

    return HttpMethod.GET.name();
  }

  public <I, O> void handlePostMutation(MutationContext mutationContext, I parameter, O mutationResult) {
    DataFetchingEnvironment dataFetchingEnvironment = mutationContext.getDataFetchingEnvironment();

    if (apisToEvictUserPermissionRestrictionCache.contains(dataFetchingEnvironment.getField().getName())) {
      log.info("Evicting permission cache for [{}] for accountId [{}]", dataFetchingEnvironment.getField().getName(),
          mutationContext.getAccountId());
      authService.evictUserPermissionAndRestrictionCacheForAccount(mutationContext.getAccountId(), true, true);
    }
  }

  public void unsetAllThreadLocal() {
    AccountThreadLocal.unset();
    UserThreadLocal.unset();
    PrincipalThreadLocal.unset();
  }
}
