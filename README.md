# 數獨即時解題（Android）

用 Android MediaProjection 擷取螢幕，使用可拖曳/縮放的 9×9 懸浮框定位數獨棋盤，透過 ML Kit OCR 辨識 1–9，使用本機回溯演算法求解，最後把答案覆蓋在原本空格上。

## 診斷版新增
- 「測試藍框（不錄屏）」按鈕：直接在前景建立 TYPE_APPLICATION_OVERLAY 測試視窗，與 MediaProjection 完全分離。
- 測試視窗會顯示半透明黑底、亮藍色 9×9 粗框與「懸浮窗測試成功」字樣，8 秒後自動消失。
- 若建立視窗拋出例外，App 會直接顯示錯誤類型與訊息，方便定位 HyperOS/POCO 權限問題。

## 功能
- 螢幕即時擷取（MediaProjection）
- 可拖曳、縮放 9×9 懸浮辨識框
- 手動「辨識」與自動每約 1.4 秒辨識
- ML Kit 本機 OCR（模型隨 APK 打包）
- 自動驗證題目、回溯求解
- 只在原本空白格顯示答案
- 鎖定辨識框後可讓觸控穿透

## GitHub Actions 建置
Push 到 GitHub 後，Actions 會用 Gradle 9.5.0 自動執行 `assembleDebug`。

Diagnostic build marker: 4
