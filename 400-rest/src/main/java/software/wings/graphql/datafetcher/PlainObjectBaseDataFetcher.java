/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.ReportTarget.GRAPHQL_API;
import static io.harness.exception.WingsException.USER_SRE;

import static software.wings.graphql.datafetcher.DataFetcherUtils.GENERIC_EXCEPTION_MSG;
import static software.wings.graphql.datafetcher.DataFetcherUtils.NEGATIVE_LIMIT_ARG_MSG;
import static software.wings.graphql.datafetcher.DataFetcherUtils.NEGATIVE_OFFSET_ARG_MSG;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.ExceptionLogger;

import software.wings.graphql.directive.DataFetcherDirective.DataFetcherDirectiveAttributes;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLContextedObject;
import software.wings.service.intfc.AuthService;

import com.google.inject.Inject;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.dataloader.DataLoader;
import org.jetbrains.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration;
import org.modelmapper.convention.MatchingStrategies;
import org.modelmapper.internal.objenesis.Objenesis;
import org.modelmapper.internal.objenesis.ObjenesisStd;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public abstract class PlainObjectBaseDataFetcher<T, P> extends BaseDataFetcher {
  public static final String SELECTION_SET_FIELD_NAME = "selectionSet";
  @Inject AuthService authService;

  @Override
  public void addDataFetcherDirectiveAttributesForParent(
      String parentTypeName, DataFetcherDirectiveAttributes dataFetcherDirectiveAttributes) {
    parentToContextFieldArgsMap.putIfAbsent(parentTypeName, dataFetcherDirectiveAttributes);
  }

  protected abstract Object fetchPlainObject(P parameters, String accountId);

  protected CompletionStage<T> fetchWithBatching(P parameters, DataLoader dataLoader) {
    return null;
  }

  @Override
  public final Object get(DataFetchingEnvironment dataFetchingEnvironment) {
    return getInternal(dataFetchingEnvironment, 0);
  }

  @Nullable
  private Object getInternal(DataFetchingEnvironment dataFetchingEnvironment, int retryCount) {
    Type[] typeArguments = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments();

    Class<T> returnClass = (Class<T>) typeArguments[0];
    Class<P> parametersClass = (Class<P>) typeArguments[1];
    Object result = null;
    try {
      final P parameters = fetchParameters(parametersClass, dataFetchingEnvironment);
      authRuleInstrumentation.instrumentDataFetcher(this, dataFetchingEnvironment, returnClass);
      String parentTypeName = dataFetchingEnvironment.getParentType().getName();
      if (isBatchingRequired(parentTypeName)) {
        String dataFetcherName = getDataFetcherName(parentTypeName);
        result = fetchWithBatching(parameters, dataFetchingEnvironment.getDataLoader(dataFetcherName));
      } else {
        result = fetchPlainObject(parameters, getAccountId(dataFetchingEnvironment));
      }
    } catch (AccessDeniedException ex) {
      if (retryCount == 0) {
        log.error("got access denied", ex);
        authService.evictUserPermissionAndRestrictionCacheForAccount(getAccountId(dataFetchingEnvironment), true, true);
        getInternal(dataFetchingEnvironment, 1);
      } else {
        log.error("got access denied in second retry returning", ex);
        throw new InvalidRequestException(getCombinedErrorMessages(ex), ex, ex.getReportTargets());
      }
    } catch (WingsException ex) {
      throw new InvalidRequestException(getCombinedErrorMessages(ex), ex, ex.getReportTargets());
    } catch (Exception ex) {
      throw new InvalidRequestException(GENERIC_EXCEPTION_MSG, ex, USER_SRE);
    } finally {
      authRuleInstrumentation.unsetAllThreadLocal();
    }

    return result;
  }

  private String getAccountId(DataFetchingEnvironment dataFetchingEnvironment) {
    Object contextObj = dataFetchingEnvironment.getContext();

    if (!(contextObj instanceof GraphQLContext)) {
      throw new WingsException("Context not a graphql Context");
    }

    GraphQLContext context = (GraphQLContext) contextObj;
    String accountId = context.get("accountId");
    if (isEmpty(accountId)) {
      throw new WingsException("Cannot extract accountId from environment");
    }

    return accountId;
  }

  private String getCombinedErrorMessages(WingsException ex) {
    List<ResponseMessage> responseMessages = ExceptionLogger.getResponseMessageList(ex, GRAPHQL_API);
    return responseMessages.stream()
        .map(ResponseMessage::getMessage)
        .collect(Collectors.joining(DataFetcherUtils.EXCEPTION_MSG_DELIMITER));
  }

  private static final Objenesis objenesis = new ObjenesisStd(true);

  private P fetchParameters(Class<P> clazz, DataFetchingEnvironment dataFetchingEnvironment) {
    Map<String, Object> map = new HashMap<>(dataFetchingEnvironment.getArguments());
    if (dataFetchingEnvironment.getSource() instanceof QLContextedObject) {
      map.putAll(((QLContextedObject) dataFetchingEnvironment.getSource()).getContext());
    }

    Map<String, String> contextFieldArgsMap =
        utils.getContextFieldArgsMap(parentToContextFieldArgsMap, dataFetchingEnvironment.getParentType().getName());
    if (contextFieldArgsMap != null) {
      contextFieldArgsMap.forEach(
          (key, value) -> map.put(key, utils.getFieldValue(dataFetchingEnvironment.getSource(), value)));
    }
    P parameters = convertToObject(map, clazz);
    if (FieldUtils.getField(clazz, SELECTION_SET_FIELD_NAME, true) != null) {
      try {
        FieldUtils.writeField(parameters, SELECTION_SET_FIELD_NAME, dataFetchingEnvironment.getSelectionSet(), true);
      } catch (IllegalAccessException exception) {
        log.error("This should not happen", exception);
      }
    }

    if (parameters instanceof QLPageQueryParameters) {
      QLPageQueryParameters pageParameters = (QLPageQueryParameters) parameters;
      if (pageParameters.getLimit() < 0) {
        throw new InvalidRequestException(NEGATIVE_LIMIT_ARG_MSG);
      }
      if (pageParameters.getOffset() < 0) {
        throw new InvalidRequestException(NEGATIVE_OFFSET_ARG_MSG);
      }
    }

    return parameters;
  }

  protected P convertToObject(Map<String, Object> map, Class<P> clazz) {
    ModelMapper modelMapper = new ModelMapper();
    modelMapper.getConfiguration()
        .setMatchingStrategy(MatchingStrategies.STANDARD)
        .setFieldMatchingEnabled(true)
        .setFieldAccessLevel(Configuration.AccessLevel.PRIVATE);
    P parameters = objenesis.newInstance(clazz);
    modelMapper.map(map, parameters);
    return parameters;
  }

  private String getDataFetcherName(@NotNull String parentTypeName) {
    return parentToContextFieldArgsMap.get(parentTypeName).getDataFetcherName();
  }

  private boolean isBatchingRequired(@NotNull String parentTypeName) {
    return parentToContextFieldArgsMap.get(parentTypeName).getUseBatch();
  }
}
