/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.NGCommonEntityConstants.FUNCTOR_BASE64_METHOD_NAME;
import static io.harness.NGCommonEntityConstants.FUNCTOR_STRING_METHOD_NAME;
import static io.harness.common.EntityTypeConstants.FILES;
import static io.harness.common.EntityTypeConstants.SECRETS;
import static io.harness.utils.FilePathUtils.FILE_PATH_PATTERN;

import static java.lang.String.format;

import io.harness.beans.FileReference;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

public class ConfigFileFunctor implements SdkFunctor {
  private static final int MAX_CONFIG_FILE_SIZE = 4 * ExpressionEvaluatorUtils.EXPANSION_LIMIT;
  private static final int NUMBER_OF_EXPECTED_ARGS = 2;
  private static final int METHOD_NAME_ARG = 0;
  private static final int FILE_OR_SECRET_REF_ARG = 1;

  @Inject private FileStoreService fileStoreService;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    if (args.length != NUMBER_OF_EXPECTED_ARGS) {
      throw new InvalidArgumentsException(
          format("Invalid number of config file functor arguments: %s", Arrays.asList(args)));
    }

    String methodName = args[METHOD_NAME_ARG];
    String ref = args[FILE_OR_SECRET_REF_ARG];

    String refType = getReferenceType(ref);
    if (FILES.equals(refType)) {
      return getFileContent(ambiance, methodName, ref);
    } else if (SECRETS.equals(refType)) {
      return getSecretFileContent(ambiance, methodName, ref);
    } else {
      throw new InvalidArgumentsException(format("Unsupported config file entity type: %s, ref: %s", refType, ref));
    }
  }

  @VisibleForTesting
  String getReferenceType(String ref) {
    if (EmptyPredicate.isEmpty(ref)) {
      throw new InvalidArgumentsException("File or secret reference cannot be null or empty");
    }

    return FILE_PATH_PATTERN.matcher(ref).find() ? FILES : SECRETS;
  }

  private String getFileContent(Ambiance ambiance, final String methodName, final String ref) {
    if (FUNCTOR_STRING_METHOD_NAME.equals(methodName)) {
      return getFileContentAsString(ambiance, ref);
    } else if (FUNCTOR_BASE64_METHOD_NAME.equals(methodName)) {
      return getFileContentAsBase64(ambiance, ref);
    } else {
      throw new InvalidArgumentsException(
          format("Unsupported config file functor method: %s, ref: %s", methodName, ref));
    }
  }

  private String getFileContentAsString(Ambiance ambiance, final String ref) {
    return fetchFileContent(ambiance, ref);
  }

  private String getFileContentAsBase64(Ambiance ambiance, String ref) {
    return EncodingUtils.encodeBase64(fetchFileContent(ambiance, ref));
  }

  private String fetchFileContent(Ambiance ambiance, final String ref) {
    FileReference fileReference = FileReference.of(ref, AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    Optional<FileStoreNodeDTO> file = fileStoreService.getWithChildrenByPath(fileReference.getAccountIdentifier(),
        fileReference.getOrgIdentifier(), fileReference.getProjectIdentifier(), fileReference.getPath(), true);

    if (file.isEmpty()) {
      throw new InvalidRequestException(format("File not found in local file store, path [%s], scope: [%s]",
          fileReference.getPath(), fileReference.getScope()));
    }

    FileStoreNodeDTO fileStoreNodeDTO = file.get();
    if (!(fileStoreNodeDTO instanceof FileNodeDTO)) {
      throw new InvalidRequestException(format(
          "Config file cannot be folder, path [%s], scope: [%s]", fileReference.getPath(), fileReference.getScope()));
    }

    String content = ((FileNodeDTO) fileStoreNodeDTO).getContent();
    if (content.getBytes(StandardCharsets.UTF_8).length > MAX_CONFIG_FILE_SIZE) {
      throw new InvalidRequestException(format("Too large config file, ref: %s", ref));
    }

    return content;
  }

  private String getSecretFileContent(Ambiance ambiance, final String methodName, final String ref) {
    if (FUNCTOR_STRING_METHOD_NAME.equals(methodName)) {
      return getSecretFileContentAsString(ambiance, ref);
    } else if (FUNCTOR_BASE64_METHOD_NAME.equals(methodName)) {
      return getSecretFileContentAsBase64(ambiance, ref);
    } else {
      throw new InvalidArgumentsException(
          format("Unsupported config secret file functor method: %s, ref: %s", methodName, ref));
    }
  }

  private String getSecretFileContentAsString(Ambiance ambiance, final String ref) {
    return "${ngSecretManager.obtainSecretFileAsString(\"" + ref + "\", " + ambiance.getExpressionFunctorToken() + ")}";
  }

  private String getSecretFileContentAsBase64(Ambiance ambiance, final String ref) {
    return "${ngSecretManager.obtainSecretFileAsBase64(\"" + ref + "\", " + ambiance.getExpressionFunctorToken() + ")}";
  }
}
