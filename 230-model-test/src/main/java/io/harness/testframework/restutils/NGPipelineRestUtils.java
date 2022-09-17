/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testframework.restutils;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PIPELINE_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;

import io.harness.ng.core.dto.ResponseDTO;
import io.harness.testframework.framework.Setup;

import io.restassured.specification.RequestSpecification;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import javax.ws.rs.core.GenericType;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NGPipelineRestUtils {
  public static final String PIPELINE_KEY_PARAM = "{" + PIPELINE_KEY + "}";
  public static final String BASE_PATH = "pipelines";

  private static RequestSpecification getBasicSetup(String managerSetupMethodName) {
    try {
      Method managerSetupMethod = Setup.class.getMethod(managerSetupMethodName);
      RequestSpecification setupResult = (RequestSpecification) managerSetupMethod.invoke(Setup.class);

      return setupResult.log().method().log().uri();
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  public static String createPipeline(String managerSetupMethodName, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineYaml) {
    ResponseDTO<String> pipelineResponse = getBasicSetup(managerSetupMethodName)
                                               .basePath(BASE_PATH)
                                               .queryParam(ACCOUNT_KEY, accountIdentifier)
                                               .queryParam(ORG_KEY, orgIdentifier)
                                               .queryParam(PROJECT_KEY, projectIdentifier)
                                               .body(pipelineYaml)
                                               .post()
                                               .as(new GenericType<ResponseDTO<String>>() {}.getType());
    return pipelineResponse.getData();
  }

  public static Map<String, Object> readPipeline(String managerSetupMethodName, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String pipelineName) {
    ResponseDTO<Map<String, Object>> pipelineResponse =
        getBasicSetup(managerSetupMethodName)
            .basePath(BASE_PATH)
            .queryParam(ACCOUNT_KEY, accountIdentifier)
            .queryParam(ORG_KEY, orgIdentifier)
            .queryParam(PROJECT_KEY, projectIdentifier)
            .pathParams(PIPELINE_KEY, pipelineName)
            .get(PIPELINE_KEY_PARAM)
            .as(new GenericType<ResponseDTO<Map<String, Object>>>() {}.getType());
    return pipelineResponse.getData();
  }

  public static String updatePipeline(String managerSetupMethodName, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineName, String pipelineYaml) {
    ResponseDTO<String> pipelineResponse = getBasicSetup(managerSetupMethodName)
                                               .basePath(BASE_PATH)
                                               .queryParam(ACCOUNT_KEY, accountIdentifier)
                                               .queryParam(ORG_KEY, orgIdentifier)
                                               .queryParam(PROJECT_KEY, projectIdentifier)
                                               .pathParams(PIPELINE_KEY, pipelineName)
                                               .body(pipelineYaml)
                                               .put(PIPELINE_KEY_PARAM)
                                               .as(new GenericType<ResponseDTO<String>>() {}.getType());
    return pipelineResponse.getData();
  }

  public static Boolean deletePipeline(String managerSetupMethodName, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineName) {
    ResponseDTO<Boolean> pipelineResponse = getBasicSetup(managerSetupMethodName)
                                                .basePath(BASE_PATH)
                                                .queryParam(ACCOUNT_KEY, accountIdentifier)
                                                .queryParam(ORG_KEY, orgIdentifier)
                                                .queryParam(PROJECT_KEY, projectIdentifier)
                                                .pathParams(PIPELINE_KEY, pipelineName)
                                                .delete(PIPELINE_KEY_PARAM)
                                                .as(new GenericType<ResponseDTO<Boolean>>() {}.getType());
    return pipelineResponse.getData();
  }
}
