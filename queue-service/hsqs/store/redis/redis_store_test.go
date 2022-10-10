// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package redis

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/go-redis/redismock/v8"
	"github.com/rs/zerolog"
	"github.com/stretchr/testify/assert"
	"os"
	"reflect"
	"testing"
)

func TestNewRedisStore(t *testing.T) {
	rdb, _ := redismock.NewClientMock()
	l := zerolog.New(os.Stderr).With().Timestamp().Logger()
	s := &Store{client: rdb, logger: &l}

	t.Run("TestConstructor", func(t *testing.T) {
		addr := "localhost:6379"
		got := NewRedisStore(addr)
		assert.Equal(t, s.client.Options().Addr, got.client.Options().Addr)
	})
}

type testStruct struct {
	Msg string
}

func TestInvalidTypeError_Error(t *testing.T) {
	type fields struct {
		tp reflect.Type
	}
	tests := []struct {
		name   string
		fields fields
		want   string
	}{
		{
			name:   "Type test",
			fields: fields{tp: reflect.TypeOf(testStruct{})},
			want:   "Invalid type \"redis.testStruct\", must be a pointer type",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			i := &InvalidTypeError{
				tp: tt.fields.tp,
			}
			got := i.Error()
			assert.Equal(t, tt.want, got)
		})
	}
}

func TestGetKey(t *testing.T) {
	type args struct {
		ctx context.Context
		key string
		v   any
	}
	tests := []struct {
		name    string
		mockErr error
		args    args
		want    any
		wantErr bool
	}{
		{
			name: "Get No Error",
			args: args{
				ctx: context.Background(),
				key: "key",
				v:   &testStruct{},
			},
			want: &testStruct{Msg: "abc"},
		},

		{
			name:    "Error in redis set",
			mockErr: errors.New("get key error"),
			args: args{
				ctx: context.Background(),
				key: "key",
				v:   &testStruct{},
			},
			want:    nil,
			wantErr: true,
		},
		{
			name: "Passing Non Pointer type",
			args: args{
				ctx: context.Background(),
				key: "key",
				v:   testStruct{},
			},
			want:    nil,
			wantErr: true,
		},
	}
	rdb, mock := redismock.NewClientMock()
	l := zerolog.New(os.Stderr).With().Timestamp().Logger()
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r := &Store{client: rdb, logger: &l}
			if tt.mockErr != nil {
				mock.ExpectGet(tt.args.key).SetErr(tt.mockErr)
			} else {
				data, _ := json.Marshal(tt.want)
				mock.ExpectGet(tt.args.key).SetVal(string(data))
			}
			err := r.GetKey(tt.args.ctx, tt.args.key, tt.args.v)

			if err != nil {
				assert.Equal(t, tt.wantErr, err != nil)
				return
			}

			if err == nil {
				assert.Equal(t, tt.want, tt.args.v)
				return
			}
		})
	}
}

func TestSetKey(t *testing.T) {

	type args struct {
		ctx context.Context
		key string
		v   any
	}
	tests := []struct {
		name    string
		args    args
		mockErr error
		wantErr bool
	}{
		{
			name: "Set Key - No error",
			args: args{
				ctx: context.Background(),
				key: "key",
				v:   &testStruct{Msg: "abc"},
			},
		},
		{
			name: "Set Key - with error",
			args: args{
				ctx: context.Background(),
				key: "key",
				v:   &testStruct{Msg: "abc"},
			},
			mockErr: errors.New("set key error"),
			wantErr: true,
		},
		{
			name: "Json marshal error",
			args: args{
				ctx: context.Background(),
				key: "key",
				v:   make(chan bool),
			},
			wantErr: true,
		},
	}
	rdb, mock := redismock.NewClientMock()
	l := zerolog.New(os.Stderr).With().Timestamp().Logger()
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r := &Store{client: rdb, logger: &l}
			if tt.mockErr != nil {
				mock.ExpectSet(tt.args.key, tt.args.v, 0).SetErr(tt.mockErr)
			} else {
				data, _ := json.Marshal(tt.args.v)
				mock.ExpectSet(tt.args.key, data, 0).SetVal("OK")
			}
			err := r.SetKey(tt.args.ctx, tt.args.key, tt.args.v)
			l.Info().Msgf("Error %s", err)
			assert.Equal(t, tt.wantErr, err != nil)
		})
	}
}
