package queue

type Queue interface {
	Publish(string) error
	Claim(string) (string, error)
}

//type Type string
//
//const (
//	ZipType Type = "ZipType"
//)
//
//type Base struct {
//	Prefix   string
//	Type     Type
//	DateTime time.Time
//	Retry    bool
//}
//
//type Event interface {
//	GetPrefix() string
//	GetType() Type
//	GetDateTime() time.Time
//	SetPrefix(p string)
//}
//
//func New(t Type) (Event, error) {
//	return nil, nil
//}
