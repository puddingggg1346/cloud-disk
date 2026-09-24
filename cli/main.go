package main

import (
"bytes"
"encoding/json"
"fmt"
"io"
"mime/multipart"
"net/http"
"net/url"
"os"
"path/filepath"
"strings"
)

const LocalVersion = "1.1"

const DefaultServer = "[2409:8a55:1513:c8d0:f0e7:36ff:feca:df42]:3000"

type Config struct {
Servers []string `json:"servers"`
Current string   `json:"current"`
Token   string   `json:"token"`
}

func cfgPath() string {
h, _ := os.UserHomeDir()
return filepath.Join(h, ".clouddisk.json")
}

func normalize(s string) string {
s = strings.TrimSpace(s)
s = strings.TrimPrefix(s, "http://")
s = strings.TrimPrefix(s, "https://")
return strings.TrimRight(s, "/")
}

func serverURL(addr string) string { return "http://" + addr }

func loadCfg() Config {
c := Config{}
if b, err := os.ReadFile(cfgPath()); err == nil {
json.Unmarshal(b, &c)
}
ensureDefault(&c)
if c.Current == "" {
c.Current = DefaultServer
}
return c
}

func saveCfg(c Config) {
b, _ := json.MarshalIndent(c, "", "  ")
os.WriteFile(cfgPath(), b, 0600)
}

func ensureDefault(c *Config) {
for _, s := range c.Servers {
if s == DefaultServer {
return
}
}
c.Servers = append([]string{DefaultServer}, c.Servers...)
}

func listServers(c Config) {
for _, s := range c.Servers {
mark := "  "
if s == c.Current {
mark = "* "
}
tag := ""
if s == DefaultServer {
tag = " (默认)"
}
fmt.Printf("%s%s%s\n", mark, s, tag)
}
}

func addServer(c *Config, addr string) {
addr = normalize(addr)
if addr == "" {
die("无效地址")
}
found := false
for _, s := range c.Servers {
if s == addr {
found = true
break
}
}
if !found {
c.Servers = append(c.Servers, addr)
}
c.Current = addr
saveCfg(*c)
fmt.Println("当前服务器:", addr)
}

func delServer(c *Config, addr string) {
addr = normalize(addr)
if addr == DefaultServer {
die("默认服务器不可删除")
}
idx := -1
for i, s := range c.Servers {
if s == addr {
idx = i
break
}
}
if idx < 0 {
die("服务器不存在")
}
c.Servers = append(c.Servers[:idx], c.Servers[idx+1:]...)
if c.Current == addr {
c.Current = DefaultServer
}
saveCfg(*c)
fmt.Println("已删除:", addr)
}

func die(msg string) {
fmt.Fprintln(os.Stderr, msg)
os.Exit(1)
}

func apiErr(code int, body []byte) {
var m map[string]any
json.Unmarshal(body, &m)
if e, ok := m["error"].(string); ok {
die(e)
}
if msg, ok := m["message"].(string); ok {
die(msg)
}
die(fmt.Sprintf("HTTP %d", code))
}

func needToken(c Config) {
if c.Token == "" {
die("请先登录")
}
}

func human(b int64) string {
switch {
case b < 1024:
return fmt.Sprintf("%d B", b)
case b < 1048576:
return fmt.Sprintf("%.1f KB", float64(b)/1024)
case b < 1073741824:
return fmt.Sprintf("%.1f MB", float64(b)/1048576)
default:
return fmt.Sprintf("%.2f GB", float64(b)/1073741824)
}
}

func authReq(c Config, method, path string, body io.Reader) []byte {
req, _ := http.NewRequest(method, serverURL(c.Current)+path, body)
req.Header.Set("Authorization", "Bearer "+c.Token)
resp, err := http.DefaultClient.Do(req)
if err != nil {
die(err.Error())
}
defer resp.Body.Close()
b, _ := io.ReadAll(resp.Body)
if resp.StatusCode != 200 {
apiErr(resp.StatusCode, b)
}
return b
}

func usage() {
fmt.Print(`clouddisk - 云盘 CLI

  clouddisk server                    列出服务器
  clouddisk server <ip[:port]>        添加并切换
  clouddisk server add <ip[:port]>    添加并切换
  clouddisk server list               列出服务器
  clouddisk server del <ip[:port]>    删除服务器
  clouddisk register <user> <pass>    注册
  clouddisk login <user> <pass>       登录
  clouddisk logout                    退出
  clouddisk ls                        文件列表
  clouddisk upload <file>             上传
  clouddisk download <name> [outdir]  下载
  clouddisk version                   服务端版本
`)
os.Exit(0)
}

func main() {
if len(os.Args) < 2 {
usage()
}
cmd := os.Args[1]
cfg := loadCfg()

switch cmd {
case "server":
if len(os.Args) < 3 {
listServers(cfg)
return
}
switch os.Args[2] {
case "list":
listServers(cfg)
case "add":
if len(os.Args) < 4 {
die("用法: clouddisk server add <ip[:port]>")
}
addServer(&cfg, os.Args[3])
case "del":
if len(os.Args) < 4 {
die("用法: clouddisk server del <ip[:port]>")
}
delServer(&cfg, os.Args[3])
default:
addServer(&cfg, os.Args[2])
}

case "register":
if len(os.Args) < 4 {
die("用法: clouddisk register <user> <pass>")
}
body, _ := json.Marshal(map[string]string{"username": os.Args[2], "password": os.Args[3]})
resp, err := http.Post(serverURL(cfg.Current)+"/register", "application/json", bytes.NewReader(body))
if err != nil {
die(err.Error())
}
defer resp.Body.Close()
b, _ := io.ReadAll(resp.Body)
if resp.StatusCode != 200 {
apiErr(resp.StatusCode, b)
}
fmt.Println("注册成功")

case "login":
if len(os.Args) < 4 {
die("用法: clouddisk login <user> <pass>")
}
body, _ := json.Marshal(map[string]string{"username": os.Args[2], "password": os.Args[3]})
resp, err := http.Post(serverURL(cfg.Current)+"/login", "application/json", bytes.NewReader(body))
if err != nil {
die(err.Error())
}
defer resp.Body.Close()
b, _ := io.ReadAll(resp.Body)
if resp.StatusCode != 200 {
apiErr(resp.StatusCode, b)
}
var r struct {
Token string `json:"token"`
}
json.Unmarshal(b, &r)
cfg.Token = r.Token
saveCfg(cfg)
fmt.Println("登录成功")

case "logout":
cfg.Token = ""
saveCfg(cfg)
fmt.Println("已退出")

case "ls":
needToken(cfg)
b := authReq(cfg, "GET", "/files", nil)
var files []struct {
Name string `json:"name"`
Size int64  `json:"size"`
}
json.Unmarshal(b, &files)
if len(files) == 0 {
fmt.Println("(空)")
return
}
for _, f := range files {
fmt.Printf("%10s  %s\n", human(f.Size), f.Name)
}

case "upload":
needToken(cfg)
if len(os.Args) < 3 {
die("用法: clouddisk upload <file>")
}
f, err := os.Open(os.Args[2])
if err != nil {
die(err.Error())
}
defer f.Close()
var buf bytes.Buffer
w := multipart.NewWriter(&buf)
part, _ := w.CreateFormFile("file", filepath.Base(os.Args[2]))
io.Copy(part, f)
w.Close()
req, _ := http.NewRequest("POST", serverURL(cfg.Current)+"/upload", &buf)
req.Header.Set("Content-Type", w.FormDataContentType())
req.Header.Set("Authorization", "Bearer "+cfg.Token)
resp, err := http.DefaultClient.Do(req)
if err != nil {
die(err.Error())
}
defer resp.Body.Close()
b, _ := io.ReadAll(resp.Body)
if resp.StatusCode != 200 {
apiErr(resp.StatusCode, b)
}
fmt.Println("上传成功")

case "download":
needToken(cfg)
if len(os.Args) < 3 {
die("用法: clouddisk download <name> [outdir]")
}
name := os.Args[2]
outdir := "."
if len(os.Args) >= 4 {
outdir = os.Args[3]
}
req, _ := http.NewRequest("GET", serverURL(cfg.Current)+"/download/"+url.PathEscape(name), nil)
req.Header.Set("Authorization", "Bearer "+cfg.Token)
r, err := http.DefaultClient.Do(req)
if err != nil {
die(err.Error())
}
defer r.Body.Close()
if r.StatusCode != 200 {
b, _ := io.ReadAll(r.Body)
apiErr(r.StatusCode, b)
}
out, err := os.Create(filepath.Join(outdir, filepath.Base(name)))
if err != nil {
die(err.Error())
}
defer out.Close()
n, _ := io.Copy(out, r.Body)
fmt.Printf("已下载 %s (%s)\n", name, human(n))

	case "version":
		fmt.Println(LocalVersion)

default:
usage()
}
}
