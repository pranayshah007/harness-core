/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.features.utils;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.SearchFilter.Operator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.validator.InvalidYamlException;

import software.wings.beans.EntityType;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.features.api.Usage;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

public class ServiceUtils {
  private ServiceUtils() {
    throw new AssertionError();
  }

  public static boolean isTemplateLibraryUsed(Service service) {
    return service != null && isNotEmpty(service.getServiceCommands())
        && service.getServiceCommands().stream().anyMatch(sc -> StringUtils.isNotBlank(sc.getTemplateUuid()));
  }

  public static List<Service> getServicesWithTemplateLibrary(List<Service> serviceList) {
    return serviceList.stream().filter(ServiceUtils::isTemplateLibraryUsed).collect(Collectors.toList());
  }

  public static PageRequest<Service> getServicesPageRequest(@NotNull String accountId, List<String> serviceIdList) {
    PageRequestBuilder pageRequestBuilder =
        aPageRequest().withLimit(PageRequest.UNLIMITED).addFilter(Service.ACCOUNT_ID_KEY2, Operator.EQ, accountId);

    if (isNotEmpty(serviceIdList)) {
      pageRequestBuilder.addFilter(Service.ID, Operator.IN, serviceIdList.toArray());
    }

    return (PageRequest<Service>) pageRequestBuilder.build();
  }

  public static Usage toUsage(Service service) {
    return Usage.builder()
        .entityId(service.getUuid())
        .entityName(service.getName())
        .entityType(EntityType.SERVICE.name())
        .property(ServiceKeys.appId, service.getAppId())
        .build();
  }

  @NonNull
  public static YamlField getServiceYamlFieldElseThrow(
      String orgIdentifier, String projectIdentifier, String serviceIdentifier, String importedService) {
    if (EmptyPredicate.isEmpty(importedService)) {
      String errorMessage =
          format("Empty YAML found on Git in branch [%s] for service [%s] under Project[%s], Organization [%s].",
              GitAwareContextHelper.getBranchInRequest(), serviceIdentifier, projectIdentifier, orgIdentifier);
      throw buildInvalidYamlException(errorMessage, importedService);
    }
    YamlField serviceYamlField;
    try {
      serviceYamlField = YamlUtils.readTree(importedService);
    } catch (IOException e) {
      String errorMessage = format("File found on Git in branch [%s] for filepath [%s] is not a YAML.",
          GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
      throw buildInvalidYamlException(errorMessage, importedService);
    }
    YamlField serviceInnerField = serviceYamlField.getNode().getField(YAMLFieldNameConstants.SERVICE);
    if (serviceInnerField == null) {
      String errorMessage = format("File found on Git in branch [%s] for filepath [%s] is not a Service YAML.",
          GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
      throw buildInvalidYamlException(errorMessage, importedService);
    }
    return serviceInnerField;
  }

  public static InvalidYamlException buildInvalidYamlException(String errorMessage, String serviceYaml) {
    YamlSchemaErrorWrapperDTO errorWrapperDTO =
        YamlSchemaErrorWrapperDTO.builder()
            .schemaErrors(
                Collections.singletonList(YamlSchemaErrorDTO.builder().message(errorMessage).fqn("$.service").build()))
            .build();
    return new InvalidYamlException(errorMessage, errorWrapperDTO, serviceYaml);
  }
}
