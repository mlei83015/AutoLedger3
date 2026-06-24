AutoLedger V26 使用說明

這版新增：
1. 快速常用項目加入「＋新增」。
2. 「＋新增」永遠固定在快速常用項目的最後。
3. 其他常用項目會依使用次數自動排序。
4. 匯率換算改成真正連網更新。
5. 沒網路時會使用快取匯率；第一次沒有快取時才使用內建參考匯率。
6. 匯率頁會顯示最後更新時間與來源。

安裝方式：
1. 把專案上傳到 GitHub Codespaces。
2. Terminal 執行：gradle :app:assembleDebug
3. APK 會在 app/build/outputs/apk/debug/app-debug.apk

注意：
匯率來源為 open.er-api.com。實際刷卡或換匯仍以銀行、信用卡公司或交易平台入帳匯率為準。
