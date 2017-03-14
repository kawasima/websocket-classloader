websocket-classloader
=====================

A ClassLoader loading remote java class via WebSocket.
[![Build Status](https://travis-ci.org/kawasima/websocket-classloader.png?branch=master)](https://travis-ci.org/kawasima/websocket-classloader)

## コンセプト

複数台のマシンで分散処理を行うにあたって、面倒なのはアプリケーションの配布です。例えばたくさんのJUnitのテストを、複数台のマシンで実行したい。ふつうにやろうとすると、テストの含まれるjarファイルと依存するjarを全て配布しなくてはなりませんが、これは結構な手間です。

そんなときに、分散クライアントとなるマシンには必要最低限のクラスを、ランタイムにロードできれば比較的高速に、かつ、いつでも最新のアプリケーションを動かすことができます。

websocket-classloaderは、そんな分散マシン向けのクラスローダーです。クラスロード要求に応じて、CrassProviderサーバに要求を転送し、CrassProviderサーバではクラスのバイナリーを探しだしてレスポンス返します。


## Usage

ClassProviderをJSR―356のコンテナ(undertow, tomcatなど)にデプロイします。

```java
new ClassProvider().start(port);
```

クライアント側は、ClassProviderのアドレスを指定して、WebSocketClassLoaderを作ります。

```java
ClassLoader cl = new WebSocketClassLoader("ws://class-provider-host:port");
Class<?> hogeClass = cl.loadClass("org.example.HogeHoge", true);
```

## アーキテクチャ

                class binary format
            +-----------------------------------------------+
            v                                               |
    +----------------------+  loadClass request     +---------------+
    |   Thin Application   |  (WebSocket)           | ClassProvider |
    | WebSocketClassLoader | ---------------------> |               |
    +----------------------+                        +---------------+


WebSocketClassLoaderを使う側のアプリケーション(クライアントと呼ぶ)から、ClassProvierへWebSocketのコネクションを作成し、クライアントからloadClassが呼ばれたときに、ClassProviderへリクエストを飛ばし、クラスのバイナリフォーマットが帰ってきます。

データのシリアライズにはFressianを使っています。

## License

Apache License 2.0
(c) 2014-2017 Yoshitaka Kawashima

