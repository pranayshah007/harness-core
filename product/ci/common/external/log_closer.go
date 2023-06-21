// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package external

import (
	"fmt"
	"os"
	"os/signal"
	"sync"
	"syscall"

	"github.com/harness/harness-core/commons/go/lib/logs"
)

var (
	lc   *logCloser
	once sync.Once
)

// Gracefully closes all the open remote log streams in case of SIGTERM
// SIGTERM signal is issued when a pod is deleted.
// Kubernetes waits for upto 30 seconds before terminating the pod

type logCloser struct {
	rls []*logs.RemoteLogger
}

func (lc *logCloser) Add(rl *logs.RemoteLogger) {
	lc.rls = append(lc.rls, rl)
}

// Waits for the SIGTERM signal and closes all the open remote loggers
func (lc *logCloser) Run() {
	ch := make(chan os.Signal, 1)
	signal.Notify(ch, syscall.SIGTERM)

	go func() {
		sig := <-ch
		fmt.Printf("Received signal: %s. Closing all the remote loggers", sig)
		for _, rl := range lc.rls {
			if rl.BaseLogger != nil {
				rl.BaseLogger.Infow(fmt.Sprintf("Received signal to close remote logger: %s", sig))
			}
			if err := rl.Writer.Close(); err != nil {
				fmt.Printf("failed to close remote logger with err: %v", err)
			}
		}
	}()
}

// LogCloser returns log closer to handle graceful shutdown of log stream
// in case of SIGTERM signal
func LogCloser() *logCloser {
	once.Do(func() {
		lc = &logCloser{
			rls: make([]*logs.RemoteLogger, 0),
		}
	})
	return lc
}
