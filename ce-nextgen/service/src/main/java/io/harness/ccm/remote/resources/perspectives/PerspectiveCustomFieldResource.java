/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.perspectives;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.utils.BigQueryHelper.UNIFIED_TABLE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.views.dao.ViewCustomFieldDao;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewCustomField;
import io.harness.ccm.views.entities.ViewCustomFunction;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.graphql.CustomFieldExpressionHelper;
import io.harness.ccm.views.graphql.ViewsMetaDataFields;
import io.harness.ccm.views.service.ViewCustomFieldService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.cloud.bigquery.BigQuery;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.validation.Valid;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

@Api("perspective-custom-field")
@Path("/perspective-custom-field")
@Produces("application/json")
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
public class PerspectiveCustomFieldResource {
  private ViewCustomFieldService viewCustomFieldService;
  private CustomFieldExpressionHelper customFieldExpressionHelper;
  private BigQueryService bigQueryService;
  private BigQueryHelper bigQueryHelper;
  private ViewCustomFieldDao customFieldDao;
  private static final String labelsSubQuery = "(SELECT value FROM UNNEST(labels) WHERE KEY='%s')";

  @Inject
  public PerspectiveCustomFieldResource(ViewCustomFieldService viewCustomFieldService,
      CustomFieldExpressionHelper customFieldExpressionHelper, BigQueryService bigQueryService,
      BigQueryHelper bigQueryHelper, ViewCustomFieldDao customFieldDao) {
    this.viewCustomFieldService = viewCustomFieldService;
    this.customFieldExpressionHelper = customFieldExpressionHelper;
    this.bigQueryService = bigQueryService;
    this.bigQueryHelper = bigQueryHelper;
    this.customFieldDao = customFieldDao;
  }

  @POST
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Save customField", nickname = "saveCustomField")
  public RestResponse<ViewCustomField> saveCustomField(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, ViewCustomField viewCustomField) {
    modifyCustomField(viewCustomField);
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
    return new RestResponse<>(viewCustomFieldService.save(viewCustomField, bigQuery, cloudProviderTableName));
  }

  private void modifyCustomField(ViewCustomField viewCustomField) {
    List<ViewField> viewFields = new ArrayList<>();
    String customFieldDisplayFormula = viewCustomField.getDisplayFormula();

    Pattern customFieldPattern = Pattern.compile("CUSTOM.([^ ,)]*)");
    Matcher customFieldPatternMatcher = customFieldPattern.matcher(customFieldDisplayFormula);
    while (customFieldPatternMatcher.find()) {
      String customFieldName = customFieldPatternMatcher.group(1);
      ViewCustomField customFieldDaoByName =
          customFieldDao.findByName(viewCustomField.getAccountId(), viewCustomField.getViewId(), customFieldName);
      customFieldDisplayFormula = customFieldDisplayFormula.replaceAll(
          customFieldPatternMatcher.group(), customFieldDaoByName.getDisplayFormula());
    }
    String userDefinedExpression = customFieldDisplayFormula;

    Pattern labelsPattern = Pattern.compile("LABEL.([^ ,)]*)");
    Matcher labelsPatternMatcher = labelsPattern.matcher(userDefinedExpression);
    while (labelsPatternMatcher.find()) {
      String labelKey = labelsPatternMatcher.group(1);
      viewFields.add(ViewField.builder()
                         .fieldName(labelKey)
                         .fieldId(ViewsMetaDataFields.LABEL_KEY.getFieldName())
                         .identifier(ViewFieldIdentifier.LABEL)
                         .build());
      userDefinedExpression =
          userDefinedExpression.replaceAll(labelsPatternMatcher.group(), String.format(labelsSubQuery, labelKey));
    }

    HashMap<String, ViewField> viewFieldsHashMap = customFieldExpressionHelper.getViewFieldsHashMap();
    for (Map.Entry<String, ViewField> viewFieldEntry : viewFieldsHashMap.entrySet()) {
      String key = viewFieldEntry.getKey();
      if (customFieldDisplayFormula.contains(key)) {
        ViewField viewField = viewFieldEntry.getValue();
        viewFields.add(viewField);
        userDefinedExpression = userDefinedExpression.replaceAll(key, viewField.getFieldId());
      }
    }

    viewCustomField.setViewFields(viewFields);
    viewCustomField.setUserDefinedExpression(userDefinedExpression);
    viewCustomField.setSqlFormula(
        userDefinedExpression.replaceAll(ViewCustomFunction.ONE_OF.getName(), ViewCustomFunction.ONE_OF.getFormula()));
  }

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Get customField", nickname = "getCustomField")
  public RestResponse<ViewCustomField> get(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam("customFieldId") String customFieldId) {
    return new RestResponse<>(viewCustomFieldService.get(customFieldId));
  }

  @POST
  @Timed
  @ExceptionMetered
  @Path("/validate")
  @ApiOperation(value = "Validate customField", nickname = "validateCustomField")
  public RestResponse<String> validate(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, ViewCustomField viewCustomField) {
    modifyCustomField(viewCustomField);
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
    viewCustomFieldService.validate(viewCustomField, bigQuery, cloudProviderTableName);
    return new RestResponse<>("Valid Formula");
  }

  @PUT
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Update customField", nickname = "updateCustomField")
  public RestResponse<ViewCustomField> update(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Valid @RequestBody ViewCustomField viewCustomField) {
    modifyCustomField(viewCustomField);
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
    return new RestResponse<>(viewCustomFieldService.update(viewCustomField, bigQuery, cloudProviderTableName));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Delete customField", nickname = "deleteCustomField")
  public RestResponse<String> delete(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam("customFieldId") String customFieldId, @Valid @RequestBody CEView ceView) {
    viewCustomFieldService.delete(customFieldId, accountId, ceView);
    return new RestResponse<>("Successfully deleted the custom field.");
  }
}
