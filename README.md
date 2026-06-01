# novel-listen  

聽小說(音樂)用
1.可選擇本地端檔案
2.可輸入YouTube URL (單首或清單)預設1G離線記憶
.\\tojar.bat 編譯成jar檔
.\\run.bat 直接執行

編譯成 EXE 的方法
cd E:\\abau\\VSC\\Novel\_R\_PC  
直接執行：

powershell
.\\gradlew.bat packageExe
這會在 composeApp\\build\\compose\\binaries\\main\\exe\\ 下產生 .exe 安裝包。

其他可用指令
指令	用途
.\\gradlew.bat packageExe	打包成 .exe 安裝檔
.\\gradlew.bat packageMsi	打包成 .msi 安裝檔
檔案位置：composeApp\\build\\compose\\binaries\\main\\msi\\Novel\_R-1.0.0.msi
cd e:\\abau\\VSC\\Novel\_R\_PC\\  
.\\gradlew.bat createDistributable	產生不需安裝的可攜式資料夾
檔案位置：composeApp\\build\\compose\\binaries\\main\\app\\Novel\_R  
cd e:\\abau\\VSC\\Novel\_R\_PC\\composeApp\\build\\compose\\binaries\\main\\app\\Novel\_R  
複製到tools目錄
robocopy "e:\\abau\\VSC\\Novel\_R\_PC\\composeApp\\build\\compose\\binaries\\main\\app\\Novel\_R" "e:\\tools\\Novel\_R\_PC" /e /is

