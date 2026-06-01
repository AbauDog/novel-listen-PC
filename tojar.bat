@echo off
chcp 65001 >nul
setlocal

echo [1/3] Generating JAR file (including all dependencies)...
call .\gradlew.bat :composeApp:packageUberJarForCurrentOS

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed! Please check the error messages above.
    pause
    exit /b %ERRORLEVEL%
)

echo [2/3] Moving JAR to project root...
:: Search for the generated JAR file
for %%f in (composeApp\build\compose\jars\Novel_R-*.jar) do (
    copy /Y "%%f" "Novel_R.jar"
    echo Created: Novel_R.jar
)

echo [3/3] Done!
echo Hint: You can run the app using "java -jar Novel_R.jar"
echo (Note: Java 17 or newer is required)
echo.
pause
