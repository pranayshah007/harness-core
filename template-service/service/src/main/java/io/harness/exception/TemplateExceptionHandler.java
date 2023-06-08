/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.exception.ngexception.NGTemplateArgs;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.template.utils.TemplateUtils;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TemplateExceptionHandler implements ExceptionHandler {
  public static final String TEMPLATE_NOT_FOUND = "Template Not found";
  public static final String ERROR_WHILE_GETTING_BATCH_TEMPLATES = "Error while getting batch templates";
  public static final String INVALID_YAML = "Invalid Yaml";

  public static final String HINT_FOR_TEMPLATE_NOT_FOUND =
      "Please check the scope of Template and ensure that the template is present at the correct location";
  public static final String HINT_FOR_WHILE_GETTING_BATCH_TEMPLATES =
      "Some Intermittent exception occurred while fetching the templates. Please try again later. If problem persists, please contact Harness.";
  public static final String HINT_FOR_INVALID_YAML = "Please ensure that the yaml provided in the path is valid";

  public static final List<String> EXPLANATION_FOR_TEMPLATE_NOT_FOUND =
      Lists.newArrayList("If template is inline then scope of the template referred might be wrong",
          "If template is remote then path to the template might be wrong",
          "If the template is remote then make sure your connector is able to connect to the remote directory.");

  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(NGTemplateException.class).build();
  }

  @Override
  public WingsException handleException(Exception exception) {
    if (exception instanceof NGTemplateException) {
      NGTemplateException ngTemplateException = (NGTemplateException) exception;
      String message = ngTemplateException.getMessage();
      NGTemplateArgs args = ngTemplateException.getTemplateArgs();
      switch (message) {
        case TEMPLATE_NOT_FOUND:
          ScmException ex = TemplateUtils.getScmException(ngTemplateException.getCause());
          if (null != ex) {
            return NestedExceptionUtils.hintWithExplanationsException(HINT_FOR_TEMPLATE_NOT_FOUND, ex,
                "If template is inline then scope of the template referred might be wrong",
                "If template is remote then path to the template might be wrong",
                "If the template is remote then make sure your connector is able to connect to the remote directory.");
          }
          return NestedExceptionUtils.hintWithExplanationsException(HINT_FOR_TEMPLATE_NOT_FOUND,
              new InvalidRequestException(
                  String.format("Template in account [%s] , org [%s], project [%s] with identifier [%s] not found.",
                      args.getAccountId(), args.getOrgId(), args.getProjectId(), args.getTemplateId())),
              "If template is inline then scope of the template referred might be wrong",
              "If template is remote then path to the template might be wrong",
              "If the template is remote then make sure your connector is able to connect to the remote directory.");
        case ERROR_WHILE_GETTING_BATCH_TEMPLATES:
          ScmException ex1 = TemplateUtils.getScmException(ngTemplateException.getCause());
          if (null != ex1) {
            return NestedExceptionUtils.hintWithExplanationsException(HINT_FOR_WHILE_GETTING_BATCH_TEMPLATES, ex1,
                "Some of the templates present in the pipeline could not be fetched");
          }
          return NestedExceptionUtils.hintWithExplanationsException(HINT_FOR_WHILE_GETTING_BATCH_TEMPLATES,
              ngTemplateException.getCause(), "Some of the templates present in the pipeline could not be fetched");

        case INVALID_YAML:
          ScmException ex2 = TemplateUtils.getScmException(ngTemplateException.getCause());
          if (null != ex2) {
            return NestedExceptionUtils.hintWithExplanationsException(HINT_FOR_INVALID_YAML, ex2,
                "Looks like we are having issues parsing the yaml, is template yaml correct?");
          }
          return NestedExceptionUtils.hintWithExplanationsException(HINT_FOR_INVALID_YAML,
              ngTemplateException.getCause(),
              "Looks like we are having issues parsing the yaml, is template yaml correct?");
        default:
          log.warn("Unknown Exception occurred for templates");
          throw new GeneralException("Something went wrong with templates");
      }
    }
    return new InvalidRequestException(ExceptionUtils.getMessage(exception));
  }
}
