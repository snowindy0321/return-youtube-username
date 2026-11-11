# Return YouTube Username (RYU) Patch for Morphe

<p align="center">
  <a href="https://morphe.software/add-source?github=snowindy0321/return-youtube-username&name=Return%20YouTube%20Username">
    <img src="https://img.shields.io/badge/Morphe%20Manager-一鍵新增來源-4285F4?style=for-the-badge&logo=android&logoColor=white" alt="一鍵新增來源到 Morphe Manager" height="48">
  </a>
</p>

<p align="center">
  <a href="https://github.com/snowindy0321/return-youtube-username/releases/latest">
    <img src="https://img.shields.io/github/v/release/snowindy0321/return-youtube-username?style=flat-square&color=blue&label=Latest%20Release" alt="Latest Release">
  </a>
  <img src="https://img.shields.io/badge/Tested%20YouTube-v21.30.216-red?style=flat-square&logo=youtube" alt="Tested YouTube">
  <img src="https://img.shields.io/badge/Tested%20Morphe%20Manager-v2.5.0-green?style=flat-square" alt="Tested Morphe Manager">
  <img src="https://img.shields.io/badge/Morphe%20Patches-v2.1.0+-blueviolet?style=flat-square" alt="Morphe Patches Compatibility">
  <img src="https://img.shields.io/badge/Package%20Size-~21%20KB-orange?style=flat-square" alt="Package Size">
</p>

為 YouTube Android (Morphe) 開發的原生輕量補丁，將 YouTube 內的留言區、Shorts 短片、即時直播聊天室及聊天重播中顯示的 `@使用者代號`（Handle），全面還原並替換為「**頻道顯示名稱**」（Display Name / Username）。

---

## 📋 實測支援版本 (Verified & Tested Environments)

本補丁已通過實際設備修補與執行測試，確認在以下官方環境下完美相容、穩定運行：

| 項目 | 實測版本 / 環境 | 備註說明 |
| :--- | :--- | :--- |
| **YouTube 官方 APK** | **`21.30.216`** | 推薦使用官方推薦之 `arm64-v8a` 或 universal APK |
| **Morphe Manager** | **`v2.5.0`** | 官方發布最新版本，內建 Patcher `v2.3.0` |
| **Morphe Patches 官方補丁源** | **`v2.1.0+`** | 可與Morphe官方補丁共存 |
| **Morphe CLI (電腦端桌面版)** | **`v2.1.1`** | 支援命令列離線修補打包 |
| **實測 Android 系統** | **Android 12 (API 31)** 及以上 | 支援 ARM64-v8a 架構裝置 |

---

## 📲 如何在 Morphe Manager 中使用

### 方法一：一鍵自動新增來源（推薦）

點擊上方藍色按鈕，或在已安裝 Morphe Manager 的 Android 手機上直接點擊下方連結：

👉 **[新增此來源至 Morphe Manager](https://morphe.software/add-source?github=snowindy0321/return-youtube-username&name=Return%20YouTube%20Username)**

Morphe Manager 將會自動彈出提示，確認後即可完成新增。

---

### 方法二：手動新增補丁來源

1. 開啟 **Morphe Manager**。
2. 前往 **設定 (Settings)** ➔ **來源 (Sources)**。
3. 點擊右上角新增圖示（＋），填入以下資訊：
   - **來源組織 / 倉庫 (Repository)**: `snowindy0321/return-youtube-username`
   - **來源名稱 (Name)**: `Return YouTube Username`
4. 點擊儲存，並在來源清單中重新整理。
5. 進入修補清單時，在補丁選單中即可看到並勾選 **Return YouTube Username**，與其他官方補丁一同修補打包！

---

## 🌟 特色
- **留言區與回覆**：將所有留言者、樓中樓回覆者的 `@代號` 完整替換為頻道真實名稱。
- **Shorts 短影片**：Shorts 播放介面下方作者名稱自動還原為上傳者頻道名稱。
- **影片標題與說明欄提及**：將影片標題（Title）與說明欄簡介（Description）中出現的 `@頻道代碼`，智慧替換為包含 `@` 前綴的頻道名稱（例如 `ft. @頻道A @頻道B`）。
- **即時直播聊天室（Live Chat）與聊天重播（Chat Replay）**：
  - 完整支援進行中直播的即時訊息串流、存檔直播的聊天重播、超級留言（Super Chat）以及置頂聊天列。
  - 具備 Litho 元件專屬適配處理，在進行中直播推播新訊息時直接呈現頻道名稱。
  - 保留作者專屬身分徽章、會員貼圖、贊助色彩、粗體樣式及點擊互動功能。
- **頻道頁面與提及預覽卡片智慧防誤替換**：在頻道首頁、點擊提及彈出的頻道預覽卡片（Channel Preview Card / Bottom Sheet）及資訊列中，智慧識別並保留官方代號標示（例如頻道標題下方的「`@頻道代碼`」），防止被重複替換為頻道名稱。
  - **YouTube Data API v3 Key（選填）**：預設零設定即可開箱即用；亦可填入個人金鑰享有官方更高配額
---
## 🛠️ 本機編譯指南

本專案支援在本機環境中建置 `.mpp` 獨立補丁包：

### 必備環境
- Python 3.8+
- JDK 17+
- Android SDK (build-tools 34.0.0+ 及 platforms;android-34)

### 建置步驟
```bash
# 複製倉庫
git clone https://github.com/snowindy0321/return-youtube-username.git
cd return-youtube-username

# 執行自動化建置腳本
python build_patch.py
```
建置成功後將於目錄下生成已簽署之 `return-youtube-username.mpp`。

---

## 📄 開源許可與免責聲明

- 本專案採用 [MIT License](LICENSE) 開源授權。
- 本專案與 YouTube 或 Google LLC 無任何關聯，所有商標均屬其原持有者所有。
- 本補丁僅供學習、研究與個人介面體驗改善之用。
