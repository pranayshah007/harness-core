// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package grpcclient

import (
	"fmt"
	"testing"

	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestValidTiClient_Close(t *testing.T) {
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	client, err := NewTiProxyClient(65534, log.Sugar())
	assert.Nil(t, err)
	err = client.CloseConn()
	assert.Nil(t, err)
}

func TestMultipleTiClient_Close(t *testing.T) {
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	client, err := NewTiProxyClient(65534, log.Sugar())
	fmt.Println(client.Client())
	assert.Nil(t, err)
	err = client.CloseConn()
	assert.Nil(t, err)
	err = client.CloseConn()
	fmt.Println(err)
	assert.NotNil(t, err)
	assert.NotNil(t, client.Client())
}
