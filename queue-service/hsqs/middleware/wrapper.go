// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

//go:build appdynamics

package middleware

import (
	appd "appdynamics"
	"context"
	"net/http"
	"os"

	"github.com/harness/harness-core/queue-service/hsqs/config"
	"github.com/labstack/gommon/log"
)

type key int

const (
	BTKey key = iota
)

type ExitCallHandle *appd.ExitcallHandle

func Init(config *config.Config) error {
	hostname, err := os.Hostname()
	if err != nil {
		log.Error("failed to determine hostname")
		hostname = "unknown"
	}

	cfg := appd.Config{
		AppName:       config.AppDynamicsConfig.AppName,
		TierName:      config.AppDynamicsConfig.TierName,
		NodeName:      hostname,
		InitTimeoutMs: 1000,
		Controller: appd.Controller{
			Host:      config.AppDynamicsConfig.ControllerHost,
			Port:      config.AppDynamicsConfig.ControllerPort,
			Account:   config.AppDynamicsConfig.Account,
			AccessKey: config.AppDynamicsConfig.AccessKey,
			UseSSL:    true,
		},
	}
	log.Info("Initializing appDynamics")
	return appd.InitSDK(&cfg)
}

func Terminate() {
	appd.TerminateSDK()
}

func StartBTForRequest(r *http.Request) *http.Request {
	hdr := r.Header.Get(appd.APPD_CORRELATION_HEADER_NAME)
	bt := appd.StartBT(r.URL.Path, hdr)

	ctx := withBusinessTransaction(r.Context(), bt)
	return r.WithContext(ctx)
}

func EndBTForRequest(r *http.Request) {
	bt := businessTransactionFrom(r.Context())
	if bt != nil {
		appd.EndBT(*bt)
	}
}

func ReportBTError(r *http.Request, msg string) {
	bt := businessTransactionFrom(r.Context())
	if bt != nil {
		appd.AddBTError(*bt, appd.APPD_LEVEL_ERROR, msg, true)
	}
}

func StartExitCall(ctx context.Context, backendName string) ExitCallHandle {
	bt := businessTransactionFrom(ctx)
	if bt == nil {
		return nil
	}

	ec := appd.StartExitcall(*bt, backendName)
	return &ec
}

func EndExitCall(ec ExitCallHandle) {
	if ec != nil {
		appd.EndExitcall(*ec)
	}
}

func AddDatabaseBackend(name, driver string) error {
	bp := map[string]string{
		"DATABASE": driver,
	}
	return appd.AddBackend(name, "DB", bp, true)
}

func businessTransactionFrom(ctx context.Context) *appd.BtHandle {
	v := ctx.Value(BTKey)
	if v == nil {
		return nil
	}

	bt, ok := v.(appd.BtHandle)
	if !ok {
		log.Error("business transaction was not a BtHandle")
		return nil
	}
	return &bt
}

func withBusinessTransaction(parent context.Context, bt appd.BtHandle) context.Context {
	return context.WithValue(parent, BTKey, bt)
}
