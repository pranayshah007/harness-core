/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Service;
import software.wings.service.impl.WorkflowExecutionOptimizationHelper;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.AbstractMultivaluedMap;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowExecutionOptimizationHelperTest extends WingsBaseTest {
  @Inject @InjectMocks private WorkflowExecutionOptimizationHelper optimizationHelper;
  @Inject private HPersistence persistence;
  @Mock private FeatureFlagService featureFlagService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(true).when(featureFlagService).isEnabled(any(), any());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testEnforceAppIdFromChildrenEntities() {
    persistence.save(anEnvironment().uuid("envId1").appId("appId1").accountId("accountId").build());
    persistence.save(Service.builder().uuid("serviceId1").appId("appId2").accountId("accountId").build());
    UriInfo uriInfo = mock(UriInfo.class);

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(WorkflowExecutionKeys.accountId, List.of("accountId"));

    queryParams.put("search[0][field]", List.of(WorkflowExecutionKeys.envIds));
    queryParams.put("search[0][value]", List.of("envId1"));

    queryParams.put("search[1][field]", List.of(WorkflowExecutionKeys.serviceIds));
    queryParams.put("search[1][value]", List.of("serviceId1"));

    doReturn(new AbstractMultivaluedMap(queryParams) {}).when(uriInfo).getQueryParameters();

    PageRequest pageRequest = aPageRequest().withUriInfo(uriInfo).build();

    optimizationHelper.enforceAppIdFromChildrenEntities(pageRequest, "accountId");

    assertThat(pageRequest.getFilters().size()).isEqualTo(1);

    SearchFilter appIdSearchFilter = (SearchFilter) pageRequest.getFilters().get(0);
    assertThat(appIdSearchFilter.getFieldName()).isEqualTo(WorkflowExecutionKeys.appId);
    assertThat(appIdSearchFilter.getFieldValues()).contains("appId1", "appId2");
  }
}
