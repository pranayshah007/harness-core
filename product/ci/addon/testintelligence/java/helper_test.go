// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"context"
	"github.com/golang/mock/gomock"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
	"os"
	"testing"
)

const (
	pkgDetectTestdata = "testdata/pkg_detection"
)

func TestDetectJavaPkgs(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	wd, err := os.Getwd()
	if err != nil {
		t.Fatalf("could not get working directory: %s", err)
	}
	wd = wd + pkgDetectTestdata

	oldGetWorkspace := getWorkspace
	defer func() {
		getWorkspace = oldGetWorkspace
	}()
	getWorkspace = func() (string, error) {
		return pkgDetectTestdata, nil
	}

	l, err := DetectPkgs(log.Sugar(), filesystem.NewOSFileSystem(log.Sugar()))
	assert.Contains(t, l, "xyz")
	assert.Contains(t, l, "test1.test1")
	assert.Len(t, l, 2)
	assert.Nil(t, err)
}
