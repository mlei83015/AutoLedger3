AutoLedger 自動記帳 Android App - V6

這版新增：
1. 隱私權政策頁面
2. 第一次開啟的新手說明
3. 匯出 CSV
4. 備份 / 還原
5. 清除資料，並加入二次確認：「刪除資料無法再復原」
6. 通知讀取用途說明
7. 支出分類管理
8. 自訂常用項目
9. 錯誤回報 / 自動除錯紀錄
10. 月底預估花費

V6 也強化防重複記帳：
- LINE Pay
- 載具發票
- Google 錢包 / Google Pay
- 銀行刷卡通知
- 其他付款 App

判斷邏輯：
同一筆消費若金額相同，且在短時間內從不同來源進來，App 會交叉比對來源、時間、店家/原始文字，避免一筆消費記成兩筆。
載具比較慢進來時，也會以 72 小時內的同金額付款通知做比對。

操作提醒：
- 點一下首頁紀錄：只查看詳情，避免誤觸修改。
- 長按首頁紀錄：直接進入修改。
- 詳情頁也可以選擇修改或刪除。

更新到 GitHub Actions 打包 APK：
1. 把 AutoLedger_Android_Project_v6_appstore_tools_dedupe.zip 上傳到 GitHub 倉庫。
2. 在 Codespaces Terminal 執行：

cd /workspaces/AutoLedger3

git fetch origin
git reset --hard origin/main

rm -rf app .github gradle build.gradle build.gradle.kts gradle.properties settings.gradle settings.gradle.kts README_使用說明.txt README*.txt AutoLedger tmp_extract

mkdir tmp_extract
unzip -q AutoLedger_Android_Project_v6_appstore_tools_dedupe.zip -d tmp_extract

PROJECT_DIR=$(find tmp_extract -type f \( -name "settings.gradle" -o -name "settings.gradle.kts" \) -exec dirname {} \; | head -n 1)

echo "找到專案資料夾：$PROJECT_DIR"

if [ -z "$PROJECT_DIR" ]; then
  echo "找不到 Android 專案，先停止。"
  find tmp_extract -maxdepth 5 -type d
  exit 1
fi

cp -a "$PROJECT_DIR"/. .

rm -rf tmp_extract AutoLedger_Android_Project_v6_appstore_tools_dedupe.zip .gradle

git add app .github build.gradle* settings.gradle* gradle.properties README* 2>/dev/null || true
git commit -m "Update AutoLedger V6 app store tools and smarter dedupe"
git push

3. 回 GitHub Actions 等綠色勾勾，下載 AutoLedger-debug-apk.zip。
4. 解壓縮後安裝 app-debug.apk。
