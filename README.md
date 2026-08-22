# 數獨即時解題（Android）

用 Android MediaProjection 擷取螢幕，使用可拖曳/縮放的 9×9 懸浮框定位數獨棋盤，透過 ML Kit OCR 辨識 1–9，使用本機回溯演算法求解，最後把答案覆蓋在原本空格上。

## 功能
- 螢幕即時擷取（MediaProjection）
- 可拖曳、縮放 9×9 懸浮辨識框
- 手動「辨識」與自動每約 1.4 秒辨識
- ML Kit 本機 OCR（模型隨 APK 打包）
- 自動驗證題目、回溯求解
- 只在原本空白格顯示答案
- 鎖定辨識框後可讓觸控穿透

## 使用方式
1. 安裝 APK。
2. 開啟「懸浮窗權限」。
3. 點「開始螢幕辨識」，允許 Android 的螢幕分享提示。
4. 切到數獨 App，把藍色正方形框拖到完整 9×9 棋盤上，右下角可縮放。
5. 點懸浮控制列的「辨識」；成功後答案會直接顯示在空格。
6. 點「鎖定」可讓棋盤框不攔截觸控；也可開啟「自動」。

## GitHub Actions 建置
Push 到 GitHub 後，Actions 會用 Gradle 9.5.0 自動執行 `assembleDebug`。完成後在該次 workflow 的 Artifacts 下載 `SudokuLiveSolver-debug`。

這個專案不依賴本機 Gradle wrapper；GitHub Actions 會安裝指定版本，手機上只需要把專案 push 到 GitHub。

## 技術需求
- minSdk 26
- compileSdk / targetSdk 36
- AGP 9.3.0
- Gradle 9.5.0
- JDK 17
- ML Kit Text Recognition 16.0.1（bundled）

## 辨識注意事項
- 請讓 9×9 棋盤正面、沒有透視變形。
- 框線盡量貼齊棋盤外框。
- 若遊戲字型非常特殊，ML Kit 可能把數字誤判；程式會先檢查列/欄/九宮格衝突，不會把明顯錯誤的解答畫上去。
- 自動模式會短暫隱藏答案層後重新取樣，以避免自己的答案被 OCR 再次讀入。
