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

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/app"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/dialog"
	"fyne.io/fyne/v2/widget"
)

const API = "http://[2409:8a55:1511:41f0:f0e7:36ff:feca:df42]:3000"

var token string

type FileItem struct {
	Name string `json:"name"`
	Size int64  `json:"size"`
}

func main() {
	a := app.New()
	w := a.NewWindow("Cloud Disk")
	w.Resize(fyne.NewSize(420, 660))
	showLogin(a, w)
	w.ShowAndRun()
}

func showLogin(a fyne.App, w fyne.Window) {
	user := widget.NewEntry()
	user.SetPlaceHolder("用户名")
	pass := widget.NewEntry()
	pass.SetPlaceHolder("密码")
	pass.Password = true
	status := widget.NewLabel("")

	login := widget.NewButton("登录", func() {
		if user.Text == "" || pass.Text == "" {
			status.SetText("请输入账号密码")
			return
		}
		t, err := authReq("/login", user.Text, pass.Text)
		if err != nil {
			status.SetText(err.Error())
			return
		}
		token = t
		showFiles(a, w)
	})
	reg := widget.NewButton("注册", func() {
		if user.Text == "" || pass.Text == "" {
			status.SetText("请输入账号密码")
			return
		}
		_, err := authReq("/register", user.Text, pass.Text)
		if err != nil {
			status.SetText(err.Error())
			return
		}
		status.SetText("注册成功，请登录")
	})
	title := widget.NewLabelWithStyle("你好", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
	w.SetContent(container.NewPadded(container.NewVBox(title, user, pass, login, reg, status)))
}

func authReq(path, u, p string) (string, error) {
	body, _ := json.Marshal(map[string]string{"username": u, "password": p})
	resp, err := http.Post(API+path, "application/json", bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	var m map[string]any
	json.Unmarshal(b, &m)
	if t, ok := m["token"].(string); ok {
		return t, nil
	}
	if e, ok := m["error"].(string); ok {
		return "", fmt.Errorf("%s", e)
	}
	if msg, ok := m["message"].(string); ok {
		return "", fmt.Errorf("%s", msg)
	}
	return "", fmt.Errorf("HTTP %d", resp.StatusCode)
}

func showFiles(a fyne.App, w fyne.Window) {
	box := container.NewVBox()
	scroll := container.NewVScroll(box)

	refresh := func() {
		files, err := listFiles()
		box.RemoveAll()
		if err != nil {
			box.Add(widget.NewLabel(err.Error()))
		} else {
			for _, f := range files {
				f := f
				box.Add(container.NewBorder(nil, nil,
					widget.NewLabel(human(f.Size)),
					widget.NewButton("下载", func() { download(f.Name, w) }),
					widget.NewLabel(f.Name),
				))
			}
		}
		box.Refresh()
	}

	uploadBtn := widget.NewButton("上传文件", func() {
		dialog.ShowFileOpen(func(rc fyne.URIReadCloser, err error) {
			if err != nil || rc == nil {
				return
			}
			rc.Close()
			uploadFile(rc.URI().Path(), w)
			refresh()
		}, w)
	})

	top := container.NewBorder(nil, nil, nil,
		widget.NewButton("退出", func() {
			token = ""
			showLogin(a, w)
		}),
		widget.NewLabelWithStyle("文件列表", fyne.TextAlignCenter, fyne.TextStyle{Bold: true}),
	)

	w.SetContent(container.NewBorder(
		container.NewVBox(top, uploadBtn), nil, nil, nil, scroll))
	refresh()
}

func listFiles() ([]FileItem, error) {
	req, _ := http.NewRequest("GET", API+"/files", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	var files []FileItem
	json.NewDecoder(resp.Body).Decode(&files)
	return files, nil
}

func uploadFile(path string, w fyne.Window) {
	f, err := os.Open(path)
	if err != nil {
		return
	}
	defer f.Close()
	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)
	part, _ := mw.CreateFormFile("file", filepath.Base(path))
	io.Copy(part, f)
	mw.Close()

	req, _ := http.NewRequest("POST", API+"/upload", &buf)
	req.Header.Set("Content-Type", mw.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		dialog.ShowError(err, w)
		return
	}
	resp.Body.Close()
}

func download(name string, w fyne.Window) {
	req, _ := http.NewRequest("GET", API+"/download/"+url.PathEscape(name), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		dialog.ShowError(err, w)
		return
	}
	if resp.StatusCode != 200 {
		resp.Body.Close()
		dialog.ShowError(fmt.Errorf("下载失败"), w)
		return
	}
	dialog.ShowFileSave(func(wc fyne.URIWriteCloser, err error) {
		if err != nil || wc == nil {
			resp.Body.Close()
			return
		}
		defer wc.Close()
		defer resp.Body.Close()
		io.Copy(wc, resp.Body)
	}, w)
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
