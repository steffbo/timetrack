package httpx

import (
	"context"

	"golang.org/x/text/language"
)

type localeKey struct{}

func WithLocale(ctx context.Context, acceptLanguage string) context.Context {
	tags, _, err := language.ParseAcceptLanguage(acceptLanguage)
	german := false
	if err == nil && len(tags) > 0 {
		base, _ := tags[0].Base()
		german = base.String() == "de"
	}
	return context.WithValue(ctx, localeKey{}, german)
}
func IsGerman(ctx context.Context) bool { value, _ := ctx.Value(localeKey{}).(bool); return value }
