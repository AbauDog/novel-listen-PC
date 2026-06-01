@echo off
echo Starting Novel_R...
java -Dfile.encoding=UTF-8 -Dskiko.renderApi=SOFTWARE -jar Novel_R.jar
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Application crashed or Java is not installed.
    pause
)
