/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.template;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.HarnessException;
import io.harness.exception.WingsException;

import software.wings.beans.NameValuePair;
import software.wings.beans.TemplateExpression;
import software.wings.beans.TemplateExpression.Yaml;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.NameValuePairYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.utils.Utils;
import software.wings.utils.YamlUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author rktummala on 10/28/17
 */
@OwnedBy(CDC)
@Singleton
public class TemplateExpressionYamlHandler extends BaseYamlHandler<TemplateExpression.Yaml, TemplateExpression> {
  @Inject YamlHandlerFactory yamlHandlerFactory;

  private TemplateExpression toBean(ChangeContext<Yaml> changeContext) throws HarnessException {
    Yaml yaml = changeContext.getYaml();

    Map<String, Object> properties = new HashMap<>();
    if (yaml.getMetadata() != null) {
      List<NameValuePair> nameValuePairList =
          yaml.getMetadata()
              .stream()
              .map(nvpYaml -> NameValuePair.builder().name(nvpYaml.getName()).value(nvpYaml.getValue()).build())
              .collect(toList());
      properties = Utils.toProperties(nameValuePairList);
    }

    return TemplateExpression.builder()
        .expression(yaml.getExpression())
        .fieldName(yaml.getFieldName())
        .metadata(properties)
        .build();
  }

  @Override
  public Yaml toYaml(TemplateExpression bean, String appId) {
    NameValuePairYamlHandler nameValuePairYamlHandler = yamlHandlerFactory.getYamlHandler(YamlType.NAME_VALUE_PAIR);
    List<NameValuePair.Yaml> nameValuePairYamlList =
        YamlUtils.toNameValuePairYamlList(bean.getMetadata(), appId, nameValuePairYamlHandler);

    return Yaml.Builder.aYaml()
        .withExpression(bean.getExpression())
        .withFieldName(bean.getFieldName())
        .withMetadata(nameValuePairYamlList)
        .build();
  }

  @Override
  public TemplateExpression upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext)
      throws HarnessException {
    return toBean(changeContext);
  }

  @Override
  public Class getYamlClass() {
    return TemplateExpression.Yaml.class;
  }

  @Override
  public TemplateExpression get(String accountId, String yamlFilePath) {
    throw new WingsException(ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION);
  }

  @Override
  public void delete(ChangeContext<Yaml> changeContext) throws HarnessException {
    // Do nothing
  }
}
