## 体感! IntelliJ & JMC

---

## 今日のお品書き

1. IntelliJ Debuggerを使ってみる
2. IntelliJ Profilerを使ってみる   
3. JMCを使ってみる
4. JFRをコマンドラインから使ってみる

---


## 1. IntelliJ Debuggerを使ってみる

1. アプリケーション起動、Javaのオプションとしてagentlibに`jdwp`を指定する
2. IntelliJからdebuggerを起動

---

## 2. IntelliJ Profilerを使ってみる 

1. アプリケーション起動
2. IntelliJからProfilerを起動、アプリケーションのプロセスを指定

---

## 3. JMCを使ってみる

1. アプリケーション起動
2. JMCを起動、アプリケーションのプロセスを指定

---

## 4. JFRをコマンドラインから使ってみる

### アプリケーション起動時に開始する

Javaのオプションに追加

### アプリケーション実行中に開始する

jcmdから起動

---

## 最後に

build.sbtの`jvmDebugOptions`を見ていただくと
Dockerで動いているアプリケーションにIntelliJ, JMCを接続しにいくためのオプションがついております。
認証をナシにしているので本番環境でこのママつかわないでね。

