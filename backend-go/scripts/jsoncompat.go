// Command jsoncompat adjusts generated response models to Jackson's null inclusion defaults.
package main

import (
	"go/format"
	"os"
	"strings"
)

func main() {
	p := "generated.go"
	b, err := os.ReadFile(p)
	if err != nil {
		panic(err)
	}
	s := strings.ReplaceAll(string(b), ",omitempty", "")
	for _, field := range []string{"FirstName", "LastName", "State"} {
		start := strings.Index(s, "type UserResponse struct {")
		end := start + strings.Index(s[start:], "\n}")
		part := s[start:end]
		lines := strings.Split(part, "\n")
		for i, line := range lines {
			words := strings.Fields(line)
			if len(words) >= 2 && words[0] == field {
				lines[i] = strings.Replace(line, words[1], "*"+words[1], 1)
			}
		}
		s = s[:start] + strings.Join(lines, "\n") + s[end:]
	}
	b, err = format.Source([]byte(s))
	if err != nil {
		panic(err)
	}
	if err := os.WriteFile(p, b, 0644); err != nil {
		panic(err)
	}
}
