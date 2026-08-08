# Mobile YOLO Screen

Android 手機螢幕即時 YOLO ONNX 辨識，透過 MediaProjection 擷取畫面，使用 ONNX Runtime Mobile 推論，並在其他 App 上層顯示偵測框。

## 使用

1. 用 Android Studio 開啟此資料夾並等待 Gradle Sync。
2. Build > Build APK(s)，安裝產生的 APK。
3. 在 App 內選擇 `.onnx` 模型；需要自訂類別名稱時再選擇每行一個名稱的 `labels.txt`。
4. 允許通知、顯示在其他應用程式上層、螢幕錄製權限。
5. 按「開始螢幕辨識」。通知列的「停止」可結束服務。

## 用 GitHub Actions 從手機建置 APK

每次推送至 `main`／`master` 都會自動執行 `.github/workflows/build-apk.yml`。完成後進入 GitHub 儲存庫的 **Actions**，打開最新一次 `Build Android APK`，在 **Artifacts** 下載 `MobileYoloScreen-debug-apk`，解壓後即可取得可安裝的 `app-debug.apk`。

## 模型格式

- 輸入：`float32`、NCHW、RGB、數值 0–1，例如 `[1,3,640,640]`。
- 支援常見 Ultralytics raw detection 輸出 `[1,4+C,N]` 或 `[1,N,4+C]`。
- 支援已匯出 NMS 的 `[1,N,6]`：`x1,y1,x2,y2,score,class_id`。
- 動態輸入尺寸會使用 640×640；固定輸入會自動讀取尺寸。
- 目前採直接縮放，不做 letterbox。若模型匯出方式不同，需要調整 `YoloEngine.kt` 的解碼器。

## 注意

- Android 不允許擷取 `FLAG_SECURE`、DRM 影片及部分銀行 App 畫面。
- 推論速度取決於手機 SoC、模型尺寸與輸入解析度；手機優先使用 `yolo11n` / `yolov8n`。
- App 只畫辨識框，不會自動觸控或操控其他 App。
