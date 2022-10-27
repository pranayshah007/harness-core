/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf.cfcli;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.ReflectionException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.pcf.cfcli.option.Flag;
import io.harness.pcf.cfcli.option.GlobalOptions;
import io.harness.pcf.cfcli.option.Option;
import io.harness.pcf.cfcli.option.Options;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;

@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfCliCommandBuilder {
  private static final String FULL_COMMAND_TEMPLATE = "${CLI_PATH}${GLOBAL_OPTIONS}${COMMAND}${ARGUMENTS}${OPTIONS}";
  private static final String OPTION_TEMPLATE = "${KEY} ${VALUE}";

  private CfCliCommandBuilder() {}

  public static String buildCommand(CfCliCommand cfCliCommand) {
    validateCommand(cfCliCommand);

    return FULL_COMMAND_TEMPLATE.replace("${CLI_PATH}", cfCliCommand.cliPath)
        .replace("${GLOBAL_OPTIONS}", addSpaceIfRequire(buildGlobalOptions(cfCliCommand.globalOptions)))
        .replace("${COMMAND}", addSpaceIfRequire(getCommandName(cfCliCommand)))
        .replace("${ARGUMENTS}", addSpaceIfRequire(buildArguments(cfCliCommand.arguments)))
        .replace("${OPTIONS}", addSpaceIfRequire(buildOptions(cfCliCommand.options)));
  }

  private static void validateCommand(CfCliCommand cfCliCommand) {
    if (cfCliCommand == null) {
      throw new InvalidArgumentsException("Parameter cfCliCommand cannot be null");
    }

    if (cfCliCommand.cliVersion == null) {
      throw new InvalidArgumentsException("Parameter cliVersion cannot be null");
    }

    if (cfCliCommand.cliPath == null) {
      throw new InvalidArgumentsException("Parameter cliPath cannot be null");
    }

    if (cfCliCommand.commandType == null) {
      throw new InvalidArgumentsException("Parameter commandType cannot be null");
    }
  }

  private static String addSpaceIfRequire(String commandPart) {
    return isNotBlank(commandPart) ? SPACE.concat(commandPart) : commandPart;
  }

  private static String buildGlobalOptions(GlobalOptions globalOptions) {
    if (globalOptions == null) {
      return EMPTY;
    }

    throw new UnsupportedOperationException("Operation still not supported");
  }

  private static String getCommandName(CfCliCommand cfCliCommand) {
    return cfCliCommand.commandType.toString();
  }

  private static String buildArguments(List<String> arguments) {
    if (isEmpty(arguments)) {
      return EMPTY;
    }

    return String.join(SPACE, arguments);
  }

  private static String buildOptions(Options optionsData) {
    if (optionsData == null) {
      return EMPTY;
    }

    Class<? extends Options> clazz = optionsData.getClass();
    Field[] declaredFields = clazz.getDeclaredFields();
    List<String> optionList = new ArrayList<>();
    for (Field field : declaredFields) {
      try {
        field.setAccessible(true);
        Class<?> fieldType = field.getType();

        String optionKey = getOptionAnnotationValue(field);
        if (optionKey != null) {
          Object optionFieldValue = field.get(optionsData);
          if (fieldType == String.class) {
            addOption(optionList, optionKey, (String) optionFieldValue);
          }
          if (fieldType == List.class) {
            addOption(optionList, optionKey, (List<?>) optionFieldValue);
          }
        }

        String flagKey = getFlagAnnotationValue(field);
        if (flagKey != null) {
          Object flagFieldValue = field.get(optionsData);
          if (fieldType == boolean.class) {
            addFlag(optionList, flagKey, (boolean) flagFieldValue);
          }
        }
      } catch (IllegalAccessException e) {
        throw new ReflectionException("Illegal access for field value", e, ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION,
            Level.ERROR, USER, EnumSet.of(FailureType.APPLICATION_ERROR));
      }
    }

    return String.join(SPACE, optionList);
  }

  private static String getOptionAnnotationValue(Field field) {
    if (field == null) {
      return null;
    }

    Option option = field.getAnnotation(Option.class);
    return option != null ? option.value() : null;
  }

  private static String getFlagAnnotationValue(Field field) {
    if (field == null) {
      return null;
    }

    Flag flag = field.getAnnotation(Flag.class);
    return flag != null ? flag.value() : null;
  }

  private static void addOption(List<String> optionList, final String optionKey, final String fieldValue) {
    if (isNotBlank(fieldValue)) {
      optionList.add(buildOption(optionKey, fieldValue));
    }
  }

  private static void addOption(List<String> optionList, final String optionKey, List<?> fieldValue) {
    if (isNotEmpty(fieldValue)) {
      Class<?> listElementType = fieldValue.get(0).getClass();
      if (listElementType != String.class) {
        throw new InvalidArgumentsException(format(
            "Unsupported option list element kind, expected String.class as element type but found: %s, optionKey: %s",
            listElementType, optionKey));
      }
      List<String> fieldValueList = (List<String>) fieldValue;
      List<String> fieldOptionKeyValueList =
          fieldValueList.stream().map(elm -> buildOption(optionKey, elm)).collect(Collectors.toList());
      optionList.add(String.join(SPACE, fieldOptionKeyValueList));
    }
  }

  private static String buildOption(String optionKey, String optionValue) {
    return OPTION_TEMPLATE.replace("${KEY}", optionKey).replace("${VALUE}", optionValue);
  }

  private static void addFlag(List<String> optionList, final String flag, boolean fieldValue) {
    if (fieldValue) {
      optionList.add(flag);
    }
  }
}
