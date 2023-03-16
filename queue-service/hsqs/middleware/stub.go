// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

//go:build !appdynamics

package middleware

import (
	"context"
	"net/http"

	"github.com/harness/harness-core/queue-service/hsqs/config"
	"github.com/labstack/gommon/log"
)

type ExitCallHandle *uint64

func Init(config *config.Config) error {
	log.Warn("binary was built without AppDynamics support")
	return nil
}

func Terminate() {
	// No-op
}

func StartBTForRequest(r *http.Request) *http.Request {
	return r
}

func EndBTForRequest(r *http.Request) {
	// No-op
}

func ReportBTError(r *http.Request, msg string) {
	// No-op
}

func StartExitCall(ctx context.Context, backendName string) ExitCallHandle {
	return nil
}

func EndExitCall(ec ExitCallHandle) {
	// No-op
}

func AddDatabaseBackend(name, driver string) error {
	return nil
}
