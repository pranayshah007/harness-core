// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Code generated by MockGen. DO NOT EDIT.
// Source: client.go

// Package grpc is a generated GoMock package.
package grpc

import (
	gomock "github.com/golang/mock/gomock"
	service "github.com/wings-software/portal/910-delegate-task-grpc-service/src/main/proto/io/harness/task/service"
	reflect "reflect"
)

// MockTaskServiceClient is a mock of TaskServiceClient interface.
type MockTaskServiceClient struct {
	ctrl     *gomock.Controller
	recorder *MockTaskServiceClientMockRecorder
}

// MockTaskServiceClientMockRecorder is the mock recorder for MockTaskServiceClient.
type MockTaskServiceClientMockRecorder struct {
	mock *MockTaskServiceClient
}

// NewMockTaskServiceClient creates a new mock instance.
func NewMockTaskServiceClient(ctrl *gomock.Controller) *MockTaskServiceClient {
	mock := &MockTaskServiceClient{ctrl: ctrl}
	mock.recorder = &MockTaskServiceClientMockRecorder{mock}
	return mock
}

// EXPECT returns an object that allows the caller to indicate expected use.
func (m *MockTaskServiceClient) EXPECT() *MockTaskServiceClientMockRecorder {
	return m.recorder
}

// CloseConn mocks base method.
func (m *MockTaskServiceClient) CloseConn() error {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "CloseConn")
	ret0, _ := ret[0].(error)
	return ret0
}

// CloseConn indicates an expected call of CloseConn.
func (mr *MockTaskServiceClientMockRecorder) CloseConn() *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "CloseConn", reflect.TypeOf((*MockTaskServiceClient)(nil).CloseConn))
}

// Client mocks base method.
func (m *MockTaskServiceClient) Client() service.TaskServiceClient {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "Client")
	ret0, _ := ret[0].(service.TaskServiceClient)
	return ret0
}

// Client indicates an expected call of Client.
func (mr *MockTaskServiceClientMockRecorder) Client() *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "Client", reflect.TypeOf((*MockTaskServiceClient)(nil).Client))
}
