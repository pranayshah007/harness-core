// Code generated by MockGen. DO NOT EDIT.
// Source: server.go

// Package grpc is a generated GoMock package.
package grpc

import (
	gomock "github.com/golang/mock/gomock"
	reflect "reflect"
)

// MockEngineServer is a mock of EngineServer interface.
type MockEngineServer struct {
	ctrl     *gomock.Controller
	recorder *MockEngineServerMockRecorder
}

// MockEngineServerMockRecorder is the mock recorder for MockEngineServer.
type MockEngineServerMockRecorder struct {
	mock *MockEngineServer
}

// NewMockEngineServer creates a new mock instance.
func NewMockEngineServer(ctrl *gomock.Controller) *MockEngineServer {
	mock := &MockEngineServer{ctrl: ctrl}
	mock.recorder = &MockEngineServerMockRecorder{mock}
	return mock
}

// EXPECT returns an object that allows the caller to indicate expected use.
func (m *MockEngineServer) EXPECT() *MockEngineServerMockRecorder {
	return m.recorder
}

// Start mocks base method.
func (m *MockEngineServer) Start() error {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "Start")
	ret0, _ := ret[0].(error)
	return ret0
}

// Start indicates an expected call of Start.
func (mr *MockEngineServerMockRecorder) Start() *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "Start", reflect.TypeOf((*MockEngineServer)(nil).Start))
}

// Stop mocks base method.
func (m *MockEngineServer) Stop() {
	m.ctrl.T.Helper()
	m.ctrl.Call(m, "Stop")
}

// Stop indicates an expected call of Stop.
func (mr *MockEngineServerMockRecorder) Stop() *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "Stop", reflect.TypeOf((*MockEngineServer)(nil).Stop))
}
