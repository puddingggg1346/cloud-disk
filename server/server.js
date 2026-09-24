const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const cors = require('cors');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const readline = require('readline');

const app = express();
const SECRET = 'secret_key_123';
const UPLOAD_DIR = path.join(__dirname, 'storage');
const DB_FILE = path.join(__dirname, 'users.json');

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR);
if (!fs.existsSync(DB_FILE)) fs.writeFileSync(DB_FILE, '{}');

const getUsers = () => JSON.parse(fs.readFileSync(DB_FILE));
const saveUsers = (data) => fs.writeFileSync(DB_FILE, JSON.stringify(data, null, 2));

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const userDir = path.join(UPLOAD_DIR, req.user.username);
    if (!fs.existsSync(userDir)) fs.mkdirSync(userDir);
    cb(null, userDir);
  },
  filename: (req, file, cb) => cb(null, file.originalname)
});
const upload = multer({ storage });

const auth = (req, res, next) => {
  const token = req.headers.authorization?.split(' ')[1];
  if (!token) return res.status(401).json({ error: '未登录' });
  try {
    req.user = jwt.verify(token, SECRET);
    next();
  } catch {
    res.status(403).json({ error: 'Token无效' });
  }
};

app.post('/register', async (req, res) => {
  const { username, password } = req.body;
  const users = getUsers();
  if (users[username]) return res.status(400).json({ error: '用户已存在' });
  users[username] = await bcrypt.hash(password, 10);
  saveUsers(users);
  res.json({ message: '注册成功' });
});

app.post('/login', async (req, res) => {
  const { username, password } = req.body;
  const users = getUsers();
  if (!users[username] || !(await bcrypt.compare(password, users[username]))) {
    return res.status(400).json({ error: '账号或密码错误' });
  }
  const token = jwt.sign({ username }, SECRET);
  res.json({ token });
});

app.get('/files', auth, (req, res) => {
  const userDir = path.join(UPLOAD_DIR, req.user.username);
  if (!fs.existsSync(userDir)) return res.json([]);
  const list = fs.readdirSync(userDir).map(name => {
    const st = fs.statSync(path.join(userDir, name));
    return { name, size: st.size };
  });
  res.json(list);
});

app.post('/upload', auth, upload.single('file'), (req, res) => {
  res.json({ message: '上传成功' });
});

app.get('/download/:name', auth, (req, res) => {
  const filePath = path.join(UPLOAD_DIR, req.user.username, req.params.name);
  if (!fs.existsSync(filePath)) return res.status(404).json({ error: '文件不存在' });
  res.download(filePath);
});

app.get('/version', (req, res) => {
  const apk = path.join(__dirname, 'public', 'app.apk');
  const size = fs.existsSync(apk) ? fs.statSync(apk).size : 0;
  res.json({ versionCode: 3, versionName: '1.2', url: '/app.apk', size });
});
app.use('/app.apk', express.static(path.join(__dirname, 'public', 'app.apk')));
app.listen(3000, '::', () => console.log('服务端启动: http://localhost:3000'));

// CLI 管理界面
const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
const cliMenu = () => {
  console.log('\n--- 服务端 CLI 管理 ---');
  console.log('1. 用户列表  2. 删除用户  3. 退出 CLI');
  rl.question('选择操作: ', async (opt) => {
    const users = getUsers();
    if (opt === '1') {
      console.log('当前用户:', Object.keys(users));
    } else if (opt === '2') {
      rl.question('输入要删除的用户名: ', (user) => {
        if (users[user]) {
          delete users[user];
          saveUsers(users);
          fs.rmSync(path.join(UPLOAD_DIR, user), { recursive: true, force: true });
          console.log('已删除用户及其文件');
        } else console.log('用户不存在');
        cliMenu();
      });
      return;
    } else if (opt === '3') return rl.close();
    cliMenu();
  });
};
setTimeout(cliMenu, 1000);
