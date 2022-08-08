// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

//go:build windows

package exec

import (
	"os"
	"syscall"
	"time"

	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/pkg/errors"
)

var _ ProcessState = &osProcessState{}

//go:generate mockgen -source process_state_win.go -destination process_state_mock.go -package exec ProcessState

//ProcessState is an interface for interacting with the current state of a process at a fairly low level
type ProcessState interface {
	ExitCode() int
	Exited() bool
	Pid() int
	String() string
	Success() bool
	SystemTime() time.Duration
	UserTime() time.Duration
	MaxRss() (int64, error)

	// SysUnix calls Sys and attempts to convert the result into syscall.WaitStatus
	// If not on a Unix system, always returns an error
	SysUnix() (syscall.WaitStatus, error)

	//SysUsageUnit calls SysUsage and returns the processe's rusage.
	//If not on Unix, always returns an error
	SysUsageUnit() (*syscall.Rusage, error)
}

type osProcessState struct {
	*os.ProcessState
}

func (o *osProcessState) SysUnix() (syscall.WaitStatus, error) {
	status, ok := o.Sys().(syscall.WaitStatus)
	if !ok {
		return syscall.WaitStatus{ExitCode: 1}, errors.New("unable to get unit wait status")
	}
	return status, nil
}

func (o *osProcessState) SysUsageUnit() (*syscall.Rusage, error) {
	rusage, ok := o.SysUsage().(*syscall.Rusage)
	if !ok {
		return nil, errors.New("unable to get unix rusage")
	}
	return rusage, nil
}

func (o *osProcessState) MaxRss() (int64, error) {
	return -1, nil
}

//ProcessStateLogFields returns interesting log fields for the given process state
//if it is not nil
func ProcessStateLogFields(ps ProcessState) (fields logs.Fields) {
	if ps == nil {
		return fields
	}

	fields = fields.
		Add("pid", ps.Pid()).
		AddFieldIf(ps.Exited(), "exit_code", ps.ExitCode())

	return fields
}
