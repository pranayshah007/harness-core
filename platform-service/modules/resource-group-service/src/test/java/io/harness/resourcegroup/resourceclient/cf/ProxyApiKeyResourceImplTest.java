/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.proxyapikey;

import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_IDENTIFIER;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES;
import static io.harness.rule.OwnerRule.DMACK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.proxyapikey.ProxyApiKeyClient;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

public class ProxyApiKeyResourceImplTest {
    @Inject @InjectMocks ProxyApiKeyResourceImplTest proxyApiKeyResource;
    @Inject @InjectMocks ProxyApiKeyResourceImpl proxyApiKeyResourceImpl;

    private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
    private static final String ORG_IDENTIFIER = "orgIdentifier";
    private static final String PROJECT_IDENTIFIER = "projectIdentifier";

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    @Owner(developers = DMACK)
    @Category(UnitTests.class)
    public void getType() {
        assertThat(proxyApiKeyResourceImpl.getType()).isEqualTo("FF_PROXYAPIKEY");
    }


    @Test
    @Owner(developers = DMACK)
    @Category(UnitTests.class)
    public void getValidScopeLevels() {
        assertThat(proxyApiKeyResourceImpl.getValidScopeLevels())
                .containsExactlyInAnyOrder(ScopeLevel.PROJECT, ScopeLevel.ORGANIZATION, ScopeLevel.ACCOUNT);
    }

    @Test
    @Owner(developers = DMACK)
    @Category(UnitTests.class)
    public void getEventFrameworkEntityType() {
        assertThat(proxyApiKeyResourceImpl.getEventFrameworkEntityType().get())
                .isEqualTo("FF_PROXYAPIKEY");
    }

    @Test
    @Owner(developers = DMACK)
    @Category(UnitTests.class)
    public void testValidateEmptyResourceList() {
        assertThat(
                proxyApiKeyResourceImpl.validate(new ArrayList<>(), Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER)))
                .isEmpty();
    }
}
