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
  <img src="https://img.shields.io/badge/Target%20YouTube-v21.30.216-red?style=flat-square&logo=youtube" alt="Target YouTube">
  <img src="https://img.shields.io/badge/Morphe-v2.1.0+-green?style=flat-square" alt="Morphe Compatibility">
</p>

為 YouTube Android (Morphe) 開發的原生補丁，將留言區、Shorts 短片、社群貼文等所有 `@使用者名稱`（Handle）重置並還原為原本的「頻道名稱」（Username / Channel Title）。

---

## 📲 如何在 Morphe Manager 中使用

### 方法一：一鍵自動新增（推薦）

點擊上方按鈕，或在已安裝 Morphe Manager 的 Android 手機上點擊下方連結：

👉 **[新增此來源至 Morphe Manager](https://morphe.software/add-source?github=snowindy0321/return-youtube-username&name=Return%20YouTube%20Username)**

Morphe Manager 將自動開啟並提示新增此補丁來源。

### 方法二：手動新增來源

1. 開啟 **Morphe Manager**。
2. 前往 **設定 (Settings)** -> **來源 (Sources)**。
3. 點擊右上角新增圖示，填入以下資訊：
   - **來源組織 / 倉庫 (Repository)**: `snowindy0321/return-youtube-username`
   - **來源名稱 (Name)**: `Return YouTube Username`
4. 儲存後重新整理來源，即可在修補清單中選取 **Return YouTube Username** 進行修補！

---

## 🌟 核心特色

1. **純獨立補丁包（Pure Standalone MPP，僅 ~18 KB）**：
   - 僅包含 `return-youtube-username` 本身之補丁邏輯與多語言字串，完全不重複打包官方已有的 112+ 項補丁。
   - 與官方 Morphe 補丁完美共存，在 Morphe Manager 中可作為獨立來源與官方來源同時勾選修補，零衝突。

2. **獨立專屬設定選單**：
   - 於 YouTube 內的 Morphe 設定選單中建立專屬第一層選單「**Return YouTube Username**」，完全與官方各項設定解耦分開，清晰易讀。

3. **免 API Key 開箱即用 + 支援官方 API v3**：
   - **零設定模式（預設）**：直接透過 YouTube 公開端點自動解析頻道名稱並解碼 HTML 實體符號，無需申請任何 Google API Key 即可使用！
   - **API v3 模式（選用）**：可於 Morphe 設定中填入個人的 YouTube Data API v3 金鑰，享受更高配額與官方查詢。

3. **雙層智慧快取（Memory + Persistent Disk）**：
   - **記憶體 LRU 快取**：滑動時 0 毫秒極速呈現已解析之名稱。
   - **SharedPreferences 本地快取**：跨 App 重啟依然保留已抓取之頻道名稱，避免重複消耗網路流量。
   - **請求去重（In-Flight Deduplication）**：同頁面重複出現的相同作者代號只會發起一次背景查詢。

4. **完整 Litho 樣式保留（SpannableString）**：
   - 保留原生的作者徽章、高亮色彩、粗體與點擊連結樣式。

5. **多樣化顯示格式與細部開關**：
   - **顯示格式**：
     - `0`: 僅顯示頻道名稱（例如：`MrBeast`）
     - `1`: 頻道名稱 (代號)（例如：`MrBeast (@mrbeast)`）
     - `2`: 代號 (頻道名稱)（例如：`@mrbeast (MrBeast)`）
   - **細部開關**：
     - 總開關
     - 留言區（含回覆）替換
     - Shorts 短片替換
     - 全域其他代號替換
     - YouTube Data API Key 輸入欄位

6. **完整繁中、簡中、英文三語在地化**：
   - 已內建繁體中文（zh-rTW）、簡體中文（zh-rCN）、英文（values）資源。

---

## 🛠️ 專案結構

```
return-youtube-username/
├── .github/workflows/          # GitHub Actions 自動構建與發布 CI/CD
│   └── release.yml
├── extensions/                 # 注入到 YouTube 內部的執行代碼 (DEX)
│   └── src/main/java/app/morphe/extension/youtube/
│       ├── patches/
│       │   └── ReturnYouTubeUsernamePatch.java    # Litho 文字攔截與樣式置換 Hook
│       └── patches/returnyoutubeusername/
│           ├── ChannelResolver.java              # 頻道名稱解析器 (網路/快取/API)
│           └── DisplayFormat.java                # 格式常數定義
├── patches/                    # Morphe Patcher 補丁定義 (Bytecode Patch)
│   └── src/main/java/app/morphe/patches/youtube/misc/
│       └── returnyoutubeusername/
│           └── ReturnYouTubeUsernamePatch.java   # 補丁宣告、相依關係與設定頁面注入
├── resources/                  # 設定選單文字與字串資源
│   ├── values/                 # 預設英文 strings.xml, arrays.xml
│   ├── values-zh-rTW/          # 繁體中文 strings.xml, arrays.xml
│   └── values-zh-rCN/          # 簡體中文 strings.xml, arrays.xml
├── stubs/                      # 編譯依賴樁（供 javac 編譯時連結）
├── patches-bundle.json         # Morphe Manager 來源定義清單
└── build_patch.py              # 一鍵自動編譯與打包 MPP 腳本
```

---

## 🚀 自行編譯與修補指令

若需要於本機編譯或測試：

1. **編譯打包 MPP**：
   ```bash
   python build_patch.py
   ```

2. **使用 Morphe CLI 修補任何相容版本之 YouTube APK**：
   ```powershell
   java -jar morphe-desktop-2.1.1-all.jar patch `
     --patches=patches-2.1.0-with-return-youtube-username.mpp `
     --out=youtube-patched.apk `
     <原始_youtube_21.30.216.apk>
   ```

---

## 📦 發布流程

日後若有任何代碼修改，只需：
1. 提交修改並建立 Git Tag（例如 `v2.1.1`）。
2. 推送至 GitHub：
   ```bash
   git tag v2.1.1
   git push origin v2.1.1
   ```
3. GitHub Actions 會自動執行編譯、封裝 `.mpp` 補丁包、更新 `patches-bundle.json` 並發布至 GitHub Releases！
