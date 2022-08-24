/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.dl.WingsPersistence;
import software.wings.graphql.directive.DataFetcherDirective.DataFetcherDirectiveAttributes;
import software.wings.service.intfc.AuthService;

import com.google.inject.Inject;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 07/21/2019
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public abstract class BaseDataFetcher implements DataFetcher {
  @Inject protected AuthRuleGraphQL authRuleInstrumentation;
  @Inject protected DataFetcherUtils utils;
  @Inject protected WingsPersistence wingsPersistence;
  @Inject protected AuthService authService;
  protected final Map<String, DataFetcherDirectiveAttributes> parentToContextFieldArgsMap;

  public BaseDataFetcher() {
    parentToContextFieldArgsMap = new HashMap<>();
  }

  public void addDataFetcherDirectiveAttributesForParent(
      String parentTypeName, DataFetcherDirectiveAttributes dataFetcherDirectiveAttributes) {
    parentToContextFieldArgsMap.putIfAbsent(parentTypeName, dataFetcherDirectiveAttributes);
  }

  public Object getArgumentValue(DataFetchingEnvironment dataFetchingEnvironment, String argumentName) {
    Object argumentValue = dataFetchingEnvironment.getArgument(argumentName);
    if (argumentValue == null && parentToContextFieldArgsMap != null) {
      Optional<String> parentFieldNameOptional =
          parentToContextFieldArgsMap.values()
              .stream()
              .filter(dataFetcherDirectiveAttributes -> {
                if (dataFetcherDirectiveAttributes == null) {
                  return false;
                }

                Map<String, String> contextFieldArgsMap = dataFetcherDirectiveAttributes.getContextFieldArgsMap();

                if (contextFieldArgsMap == null) {
                  return false;
                }

                String fieldName = contextFieldArgsMap.get(argumentName);
                if (fieldName == null) {
                  return false;
                }

                return true;
              })
              .map(dataFetcherDirectiveAttributes
                  -> dataFetcherDirectiveAttributes.getContextFieldArgsMap().get(argumentName))
              .findFirst();
      if (!parentFieldNameOptional.isPresent()) {
        return null;
      }
      String parentFieldName = parentFieldNameOptional.get();
      argumentValue = utils.getFieldValue(dataFetchingEnvironment.getSource(), parentFieldName);
    }
    return argumentValue;
  }
}
