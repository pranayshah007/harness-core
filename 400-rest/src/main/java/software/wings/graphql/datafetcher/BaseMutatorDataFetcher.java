/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher;

import static io.harness.exception.WingsException.ReportTarget.GRAPHQL_API;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.reflection.CodeUtils.isHarnessClass;

import static software.wings.graphql.datafetcher.DataFetcherUtils.EXCEPTION_MSG_DELIMITER;
import static software.wings.graphql.datafetcher.DataFetcherUtils.GENERIC_EXCEPTION_MSG;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.ExceptionLogger;
import io.harness.reflection.ReflectionUtils;
import io.harness.serializer.jackson.HarnessJacksonModule;
import io.harness.utils.RequestField;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import graphql.schema.DataFetchingEnvironment;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.jetbrains.annotations.Nullable;
import org.joor.Reflect;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public abstract class BaseMutatorDataFetcher<P, R> extends BaseDataFetcher {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final LoadingCache<Class<?>, Map<String, Field>> classToFieldsMapCache =
      CacheBuilder.newBuilder().maximumSize(100).build(CacheLoader.from(BaseMutatorDataFetcher::getFieldMapForClass));
  private static final LoadingCache<Class<?>, Boolean> anyClassFieldWithTypeRequestFieldCache =
      CacheBuilder.newBuilder().maximumSize(100).build(
          CacheLoader.from(clazz -> anyClassFieldWithType(clazz, RequestField.class)));

  static {
    MAPPER.registerModule(new HarnessJacksonModule());
  }

  private static Map<String, Field> getFieldMapForClass(Class<?> clazz) {
    return ListUtils.emptyIfNull(ReflectionUtils.getAllDeclaredAndInheritedFields(clazz))
        .stream()
        .collect(Collectors.toMap(Field::getName, Function.identity()));
  }

  private Class<P> parameterClass;
  private Class<R> resultClass;

  public BaseMutatorDataFetcher(Class<P> parameterClass, Class<R> resultClass) {
    this.parameterClass = parameterClass;
    this.resultClass = resultClass;
  }

  protected abstract R mutateAndFetch(P parameter, MutationContext mutationContext);

  @Override
  public R get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {
    return getInternal(dataFetchingEnvironment, 0);
  }

  @Nullable
  private R getInternal(DataFetchingEnvironment dataFetchingEnvironment, int retryCount) throws Exception {
    try {
      log.info("Executing gql {}", this.getClass().getName());
      final P parameters = fetchParameters(parameterClass, dataFetchingEnvironment);
      authRuleInstrumentation.instrumentDataFetcher(this, dataFetchingEnvironment, resultClass);
      final MutationContext mutationContext = MutationContext.builder()
                                                  .accountId(utils.getAccountId(dataFetchingEnvironment))
                                                  .dataFetchingEnvironment(dataFetchingEnvironment)
                                                  .build();
      final R result = mutateAndFetch(parameters, mutationContext);
      handlePostMutation(mutationContext, parameters, result);
      return result;
    } catch (AccessDeniedException ex) {
      if (retryCount == 0) {
        log.error("got access denied", ex);
        authService.evictUserPermissionAndRestrictionCacheForAccount(
            utils.getAccountId(dataFetchingEnvironment), true, true);
        getInternal(dataFetchingEnvironment, retryCount + 1);
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
    return null;
  }

  private P fetchParameters(Class<P> parameterClass, DataFetchingEnvironment dataFetchingEnvironment) {
    final Map<String, Object> inputArguments = detectInputArgument(dataFetchingEnvironment);
    final P parameter = convertToObject(inputArguments, parameterClass);
    if (shouldInstrumentRequestFields()) {
      instrumentRequestFields(inputArguments, parameter);
    }
    return parameter;
  }

  private Map<String, Object> detectInputArgument(DataFetchingEnvironment dataFetchingEnvironment) {
    final Map<String, Object> arguments = dataFetchingEnvironment.getArguments();
    // in case input argument is present and a Map, use that to initialize parameter
    final Object input = arguments.get("input");
    return input instanceof Map ? (Map<String, Object>) input : arguments;
  }

  /**
   * Default behaviour is to instrument when any field in parameter class is of type {@link RequestField}
   */
  protected boolean shouldInstrumentRequestFields() {
    try {
      return anyClassFieldWithTypeRequestFieldCache.get(parameterClass);
    } catch (ExecutionException e) {
      log.error("error while detecting field with type" + RequestField.class.getCanonicalName(), e);
    }
    return false;
  }

  private static boolean anyClassFieldWithType(Class<?> clazz, Class<?> type) {
    final Map<String, Field> fieldMap = getFieldMap(clazz);
    if (fieldMap != null) {
      return fieldMap.values().stream().anyMatch(field -> type.isAssignableFrom(field.getType()));
    }
    return false;
  }

  private static Map<String, Field> getFieldMap(Class<?> clazz) {
    try {
      return classToFieldsMapCache.get(clazz);
    } catch (Exception e) {
      log.error("error while getting fieldmap for class " + clazz.getCanonicalName());
    }
    return null;
  }

  private <M> M convertToObject(Object fromValue, Class<M> klass) {
    return MAPPER.convertValue(fromValue, klass);
  }

  private String getCombinedErrorMessages(WingsException ex) {
    List<ResponseMessage> responseMessages = ExceptionLogger.getResponseMessageList(ex, GRAPHQL_API);
    return responseMessages.stream()
        .map(ResponseMessage::getMessage)
        .collect(Collectors.joining(EXCEPTION_MSG_DELIMITER));
  }

  private void handlePostMutation(MutationContext mutationContext, P parameter, R mutationResult) {
    final DataFetchingEnvironment dataFetchingEnvironment = mutationContext.getDataFetchingEnvironment();
    final String accountId = mutationContext.getAccountId();
    try {
      // this should not fail the overall mutation, so caught the exception
      authRuleInstrumentation.handlePostMutation(mutationContext, parameter, mutationResult);
    } catch (Exception e) {
      log.error(format("Cache eviction failed for mutation api [%s] for accountId [%s]",
                    dataFetchingEnvironment.getField().getName(), accountId),
          e);
    }
  }

  /**
   *  Assuming jackson deserialized objects are trees and not graphs (not even DAGs)
   *  This function marks absent the RequestField type fields as {@link RequestField#absent()}
   *
   */
  @VisibleForTesting
  <T> void instrumentRequestFields(Map<String, Object> jsonAsMap, T deserObject) {
    if (deserObject == null || jsonAsMap == null) {
      return;
    }
    if (deserObject instanceof RequestField) {
      final RequestField<?> requestField = (RequestField<?>) deserObject;
      if (!requestField.isPresent()) {
        return;
      }
      if (requestField.getValue().isPresent()) {
        instrumentRequestFields(jsonAsMap, requestField.getValue().get());
      }
    } else {
      final Reflect deserObjectReflect = Reflect.on(deserObject);
      final Map<String, Reflect> deserObjectFieldMap = deserObjectReflect.fields();

      deserObjectFieldMap.entrySet()
          .stream()
          .filter(entry -> entry.getValue() != null)
          .filter(entry -> !fieldSetInRequest(entry.getKey(), jsonAsMap))
          .filter(entry -> fieldOfType(getField(entry.getKey(), deserObject), RequestField.class))
          .forEach(entry -> {
            // only absent fields of type RequestField are available here. We need to mark all such fields as
            // RequestField.notInitialized()
            final String fieldName = entry.getKey();
            deserObjectReflect.set(fieldName, RequestField.absent());
          });

      deserObjectFieldMap.entrySet()
          .stream()
          .filter(entry -> notNullField(entry.getValue()))
          .filter(entry -> fieldIsHarnessType(getField(entry.getKey(), deserObject)))
          // json object should be of complex type to nest into. This avoids processing enums
          .filter(entry -> jsonIsObjectType(jsonAsMap.get(entry.getKey())))
          .forEach(entry -> {
            final String fieldName = entry.getKey();
            final Object fieldValue = entry.getValue().get();
            instrumentRequestFields((Map<String, Object>) jsonAsMap.get(fieldName), fieldValue);
          });
    }
  }

  private Field getField(String fieldName, Object obj) {
    final Map<String, Field> fieldMap = getFieldMap(obj.getClass());
    if (fieldMap != null) {
      return fieldMap.get(fieldName);
    }
    return null;
  }
  private boolean notNullField(Reflect fieldReflect) {
    return fieldReflect != null && fieldReflect.get() != null;
  }
  private boolean fieldIsHarnessType(Field field) {
    return field != null && field.getType() != null && isHarnessClass(field.getType());
  }
  private boolean fieldSetInRequest(String fieldName, Map<String, Object> jsonAsMap) {
    return jsonAsMap.containsKey(fieldName);
  }
  private <T> boolean fieldOfType(Field field, Class<T> clazz) {
    return field != null && field.getType() != null && clazz.isAssignableFrom(field.getType());
  }
  private boolean jsonIsObjectType(Object jsonGenericObject) {
    return jsonGenericObject instanceof Map;
  }
}
