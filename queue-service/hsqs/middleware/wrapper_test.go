// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

//go:build appdynamics

package middleware

import (
	appd "appdynamics"
	"context"
	"errors"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestBusinessTransactionFrom(t *testing.T) {
	v := appd.BtHandle(123)
	ctx := withBusinessTransaction(context.Background(), v)

	assert.Equal(t, v, *businessTransactionFrom(ctx))

	// Test return on context containing an invalid value
	ctx = context.WithValue(context.Background(), BTKey, errors.New("this is an invalid value"))

	assert.Equal(t, (*appd.BtHandle)(nil), businessTransactionFrom(ctx))
}

func TestDefaulting(t *testing.T) {
	ctx := context.Background()
	assert.Equal(t, (*appd.BtHandle)(nil), businessTransactionFrom(ctx))
}
