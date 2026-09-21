$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
Set-Location "D:\AI 智能面试官与求职能力评估系统\src\frontend"
npm run dev
