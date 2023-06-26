package cache

import "context"

type Cache interface {
	Create(context.Context, string, string, string) error
	Get(context.Context, string) (string, error)
	Ping(context.Context) error
}
