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

## 4. JFRをコマンドラインから使ってみる

### jcmd

JVMに診断コマンドを送るためのユーティリティ

---

## 4. JFRをコマンドラインから使ってみる

### jcmd

JVMに診断コマンドを送るためのユーティリティ

```
jcmd $PROCESS_ID $COMMAND
```

で使う

---

## 4. JFRをコマンドラインから使ってみる

### jcmd

```
jcmd $PROCESS_ID help
```

で利用可能なコマンドの一覧が得られる
もともとjstackやjmapなどのコマンドがあるが
それを統合したもの

---

## JMCとJFRの関係

### JFR
* Java Flight Recorder 
* アプリケーション実行時のプロファイリングのデータを収集するツール
* オーバーヘッドがほとんどない

---

## JMCとJFRの関係

### JMC
* JDK Mission Control
* Javaアプリケーションのモニタリングツール

---

## JMCとJFRの関係

|JFR|JMC|
|:---|:---|
|プロファイリング収集|プロファイル結果の閲覧|

* 元々有償ツールだったけど
* Java 11からオープンソースになっていてタダで誰でも使える。

---

## 最後に

build.sbtの`jvmDebugOptions`を見ていただくと
Dockerで動いているアプリケーションにIntelliJ, JMCを接続しにいくためのオプションがついております。
認証をナシにしているので本番環境でこのママつかわないでね。

