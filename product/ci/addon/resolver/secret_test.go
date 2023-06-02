// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package resolver

import (
	"fmt"
	"os"
	"reflect"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestResolveSecretInString(t *testing.T) {
	secret1 := "admin"
	secret2 := "password"
	secret3 := "helloworld"
	os.Setenv("HARNESS_account_secret", secret1)
	os.Setenv("HARNESS_org_secret", secret2)
	os.Setenv("HARNESS_project_secret", secret3)
	os.Setenv("HARNESS_account_secret____", secret1)

	tests := []struct {
		name        string
		expr        string
		response    string
		expectedErr bool
	}{
		{
			name:        "account secret",
			expr:        `hello ${ngSecretManager.obtain("account.secret", 1234)}`,
			response:    fmt.Sprintf("hello %s", secret1),
			expectedErr: false,
		},
		{
			name:        "org secret",
			expr:        `hello ${ngSecretManager.obtain("org.secret", 1234)}`,
			response:    fmt.Sprintf("hello %s", secret2),
			expectedErr: false,
		},
		{
			name:        "project secret",
			expr:        `hello ${ngSecretManager.obtain("secret", 1234)}`,
			response:    fmt.Sprintf("hello %s", secret3),
			expectedErr: false,
		},
		{
			name:        "account secret with invalid char",
			expr:        `hello ${ngSecretManager.obtain("account.secret-//#", 1234)}`,
			response:    fmt.Sprintf("hello %s", secret1),
			expectedErr: false,
		},
		{
			name:        "secret invalid",
			expr:        `hello ${ngSecretManager.obtain(".secret", 1234)}`,
			response:    "",
			expectedErr: true,
		},
		{
			name:        "secret invalid without id",
			expr:        `hello ${ngSecretManager.obtain("org.", 1234)}`,
			response:    "",
			expectedErr: true,
		},
		{
			name:        "project secret",
			expr:        `hello ${ngSecretManager.obtain("", 1234)}`,
			response:    "",
			expectedErr: true,
		},
	}

	for _, tc := range tests {
		r, got := ResolveSecretInString(tc.expr)
		if tc.expectedErr == (got == nil) {
			t.Fatalf("%s: expected error: %v, got: %v", tc.name, tc.expectedErr, got)
		}

		assert.Equal(t, r, tc.response)
	}
	os.Unsetenv("HARNESS_account_secret")
	os.Unsetenv("HARNESS_org_secret")
	os.Unsetenv("HARNESS_project_secret")
	os.Unsetenv("HARNESS_account_secret____")
}

func TestResolveSecretInList(t *testing.T) {
	secret1 := "admin"
	secret2 := "password"
	secret3 := "helloworld"
	os.Setenv("HARNESS_account_secret", secret1)
	os.Setenv("HARNESS_org_secret", secret2)
	os.Setenv("HARNESS_project_secret", secret3)

	tests := []struct {
		name        string
		expr        []string
		response    []string
		expectedErr bool
	}{
		{
			name: "account secret",
			expr: []string{
				`hello ${ngSecretManager.obtain("account.secret", 1234)}`,
				`hello ${ngSecretManager.obtain("org.secret", 1234)}`,
				`hello ${ngSecretManager.obtain("secret", 1234)}`,
			},
			response: []string{
				fmt.Sprintf("hello %s", secret1),
				fmt.Sprintf("hello %s", secret2),
				fmt.Sprintf("hello %s", secret3),
			},
			expectedErr: false,
		},
		{
			name:        "secret invalid",
			expr:        []string{`hello ${ngSecretManager.obtain(".secret", 1234)}`},
			response:    nil,
			expectedErr: true,
		},
	}

	for _, tc := range tests {
		r, got := ResolveSecretInList(tc.expr)
		if tc.expectedErr == (got == nil) {
			t.Fatalf("%s: expected error: %v, got: %v", tc.name, tc.expectedErr, got)
		}

		assert.Equal(t, r, tc.response)
	}
	os.Unsetenv("HARNESS_account_secret")
	os.Unsetenv("HARNESS_org_secret")
	os.Unsetenv("HARNESS_project_secret")
}

func TestResolveSecretInMapValues(t *testing.T) {
	secret1 := "admin"
	secret2 := "password"
	secret3 := "helloworld"
	os.Setenv("HARNESS_account_secret", secret1)
	os.Setenv("HARNESS_org_secret", secret2)
	os.Setenv("HARNESS_project_secret", secret3)

	tests := []struct {
		name        string
		expr        map[string]string
		response    map[string]string
		expectedErr bool
	}{
		{
			name: "account secret",
			expr: map[string]string{
				"foo1": `hello ${ngSecretManager.obtain("account.secret", 1234)}`,
				"foo2": `hello ${ngSecretManager.obtain("org.secret", 1234)}`,
				"foo3": `hello ${ngSecretManager.obtain("secret", 1234)}`,
			},
			response: map[string]string{
				"foo1": fmt.Sprintf("hello %s", secret1),
				"foo2": fmt.Sprintf("hello %s", secret2),
				"foo3": fmt.Sprintf("hello %s", secret3),
			},
			expectedErr: false,
		},
		{
			name: "secret invalid",
			expr: map[string]string{
				"foo": `hello ${ngSecretManager.obtain(".secret", 1234)}`},
			response:    nil,
			expectedErr: true,
		},
	}

	for _, tc := range tests {
		r, got := ResolveSecretInMapValues(tc.expr)
		if tc.expectedErr == (got == nil) {
			t.Fatalf("%s: expected error: %v, got: %v", tc.name, tc.expectedErr, got)
		}

		eq := reflect.DeepEqual(r, tc.response)
		assert.Equal(t, eq, true)
	}
	os.Unsetenv("HARNESS_account_secret")
	os.Unsetenv("HARNESS_org_secret")
	os.Unsetenv("HARNESS_project_secret")
}
