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
</table>

---

## 📋 セットアップガイド

### ステップ 1 — LSPosed でモジュールを有効化

> ⚠️ このモジュールは **LSPosed** フレームワークが必要です。デバイスは root 化と LSPosed がインストールされている必要があります。

1. **LSPosed Manager** を開きナビゲーションバーの **モジュール**のタブを選択します。
2. そこから **HyperIsland** を探し、有効化に切り替えます。
3. モジュールのスコープでおすすめのアプリを確認してください:
    - **ダウンロード通知**: **ダウンロードマネージャー**を確認
    - **ユニバーサルアダプター**: **システム UI** を確認
4. 保存後に右上隅にある**再起動ボタン**をタップし、適用するスコープを再起動 (またはデバイスを再起動) し、フックを有効化します。
---

### ステップ 2 — HyperCeiler でフォーカス通知のホワイトリストを変更

> 💡 Dynamic Island スタイルの通知には、HyperCeiler を通じて付与される「フォーカス通知」の権限が必要です。
> 該当するオプションが見つからない場合は、HyperCeiler を最新のバージョンに更新してください。

1. **HyperCeiler** を開いて**システム UI** または **Xiaomi サービスフレームワーク**の設定を開きます。
2. **フォーカス通知のホワイトリストを削除**を探します。
3. 有効化した後にスコープを再起動してください。

---

## ⚠️ 重要な注意事項

| 項目 | 詳細 |
|------|---------|
| フレームワーク | **LSPosed** と **root 化済み**のデバイスが必要です。 |
| 再起動のタイミング | アプリのインストール/更新後にスコープを再起動してください。設定変更はホットリロードに対応しています。 |
| 通知の互換性 | ユニバーサルアダプターは**標準の Android 通知**のみ処理します。カスタム通知スタイルは対応していません。 |
| ROM の互換性 | **HyperOS 3** でテスト済みです。その他の ROM では互換性の問題が発生する可能性があります。 |

---

## 🔨 ビルド

Flutter がインストールされていることを確認後に以下を実行:

```bash
flutter build apk --target-platform=android-arm64
```

---

## 📄 ライセンス

このプロジェクトは [MIT License](LICENSE) に基づき、オープンソースとして公開しています。Issue や Pull Request を歓迎します。

<div align="center">

Made with ❤️ for HyperOS users

[![Star History](https://img.shields.io/github/stars/1812z/HyperIsland?style=social)](https://github.com/1812z/HyperIsland)

</div>
