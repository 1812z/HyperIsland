<div align="center">

<img src="https://github.com/user-attachments/assets/dc034ec0-90cf-4371-9ab0-132ca2527b32" width="120" height="120" style="border-radius: 24px;" alt="HyperIsland Icon"/>

# HyperIsland

**LSPosed と HyperOS 3 の環境で Dynamic Island スタイルの進捗通知を表示します**

[![GitHub Release](https://img.shields.io/github/v/release/1812z/HyperIsland?style=flat-square&logo=github&color=black)](https://github.com/1812z/HyperIsland/releases)
[![License](https://img.shields.io/github/license/1812z/HyperIsland?style=flat-square&color=orange)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS3-orange?style=flat-square)](https://hyperos.mi.com)
[![Build](https://img.shields.io/badge/Build-Flutter-02569B?style=flat-square&logo=flutter)](https://flutter.dev)

**[English](README_EN.md)** | **[简体中文](README.md)** | **日本語** | **[Türkçe](README_TR.md)**

</div>

---

## ✨ 機能

<table>
<tr>
<td width="50%">

### 📥 ダウンロードマネージャーを拡張
HyperOS のダウンロードマネージャーの通知をインターセプトし、Dynamic Island のスタイルで表示します。ファイル名と進捗状況の表示と**一時停止、再開、キャンセル**のコントロールも利用可能です。

</td>
<td width="50%">

### 🏝️ Dynamic Island + フォーカス通知
あらゆるアプリからの標準的な Android の通知をインターセプトし、元の操作ボタンを保持したままで Dynamic Island + フォーカス通知のスタイルで表示できます。

</td>
</tr>
<tr>
<td width="50%">

### 🚫 通知のブラックリスト
ブラックリストに登録されたアプリは、ポップアップ通知をトリガーしません。Dynamic Island のインジケーターのみが表示されます (全画面表示時はステータスバーと共に自動で非表示になります)。

</td>
<td width="50%">

### 🔥 ホットリロードに対応
設定の変更は、**再起動なし**で適用されます。アプリのインストールまたは更新後にスコープの再起動のみ必要になります。
</td>
</tr>
<tr>
<td width="50%">

### 🤖 AI 要約
OpenAI 互換プロトコルの大規模モデルに対応し、通知内容を賢く要約して重要な情報を抽出します。

</td>
<td width="50%">

### 🍞 Toast インターセプト
煩わしい Toast 通知をインターセプトし、Dynamic Island 表示へ変換して、より統一された快適な体験を提供します。

</td>
</tr>
</table>

---

## 📖 ドキュメント

> 📌 詳細な使用方法、テンプレートの説明、設定ガイドについてはこちらをご覧ください：**[hyperisland.1812z.top](https://hyperisland.1812z.top)**

---

## 🔨 ビルド

Flutter がインストールされていることを確認後に以下を実行:

```bash
flutter build apk --target-platform=android-arm64
```

---

## Star History

<a href="https://www.star-history.com/?repos=1812z%2FHyperIsland&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/image?repos=1812z/HyperIsland&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/image?repos=1812z/HyperIsland&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/image?repos=1812z/HyperIsland&type=date&legend=top-left" />
 </picture>
</a>

---

## 📄 ライセンス

このプロジェクトは [MIT License](LICENSE) に基づき、オープンソースとして公開しています。Issue や Pull Request を歓迎します。

<div align="center">

Made with ❤️ for HyperOS users

[![Star History](https://img.shields.io/github/stars/1812z/HyperIsland?style=social)](https://github.com/1812z/HyperIsland)

</div>
