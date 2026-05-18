# ============================================================================
# JDK 21 Download and Installation Script for Windows
# ============================================================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "JDK 21 LTS Download Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if running as Administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "⚠️  WARNING: Not running as Administrator." -ForegroundColor Yellow
    Write-Host "   Installation may require admin privileges." -ForegroundColor Yellow
    Write-Host ""
}

# JDK 21 Download URLs (Oracle)
$jdk21LatestUrl = "https://download.oracle.com/java/21/latest/jdk-21_windows-x64_bin.exe"
$jdk21LatestMsi = "https://download.oracle.com/java/21/latest/jdk-21_windows-x64_bin.msi"

# Alternative: Eclipse Temurin (OpenJDK) - No Oracle account required
$temurinUrl = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_windows_hotspot_21.0.5_11.msi"

Write-Host "📦 JDK 21 Download Options:" -ForegroundColor Green
Write-Host ""
Write-Host "1. Oracle JDK 21 (Latest)" -ForegroundColor White
Write-Host "   - Official Oracle distribution" -ForegroundColor Gray
Write-Host "   - Free for development and production use" -ForegroundColor Gray
Write-Host "   - URL: $jdk21LatestUrl" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Eclipse Temurin 21 (OpenJDK)" -ForegroundColor White
Write-Host "   - Open-source, no Oracle account needed" -ForegroundColor Gray
Write-Host "   - Fully compatible with Oracle JDK" -ForegroundColor Gray
Write-Host "   - Recommended for automated downloads" -ForegroundColor Gray
Write-Host ""

$choice = Read-Host "Select download option (1 or 2, or 'M' for manual)"

if ($choice -eq "M" -or $choice -eq "m") {
    Write-Host ""
    Write-Host "📖 Manual Download Instructions:" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "1. Open your browser and go to:" -ForegroundColor White
    Write-Host "   https://www.oracle.com/java/technologies/downloads/#java21-windows" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "2. Scroll to 'Java 21' section" -ForegroundColor White
    Write-Host ""
    Write-Host "3. Under 'Windows', download:" -ForegroundColor White
    Write-Host "   - x64 Installer (jdk-21_windows-x64_bin.exe)" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "4. Run the installer and follow the wizard" -ForegroundColor White
    Write-Host ""
    Write-Host "5. After installation, set JAVA_HOME:" -ForegroundColor White
    Write-Host "   - Default location: C:\Program Files\Java\jdk-21" -ForegroundColor Yellow
    Write-Host ""
    
    # Open browser
    Start-Process "https://www.oracle.com/java/technologies/downloads/#java21-windows"
    
    Write-Host "✅ Browser opened. Download and install JDK 21, then run this script again to verify." -ForegroundColor Green
    exit 0
}

$downloadUrl = ""
$installerName = ""

if ($choice -eq "1") {
    $downloadUrl = $jdk21LatestUrl
    $installerName = "jdk-21_windows-x64_bin.exe"
    Write-Host ""
    Write-Host "📥 Downloading Oracle JDK 21..." -ForegroundColor Cyan
} elseif ($choice -eq "2") {
    $downloadUrl = $temurinUrl
    $installerName = "OpenJDK21U-jdk_x64_windows_hotspot_21.0.5_11.msi"
    Write-Host ""
    Write-Host "📥 Downloading Eclipse Temurin 21..." -ForegroundColor Cyan
} else {
    Write-Host "❌ Invalid choice. Exiting." -ForegroundColor Red
    exit 1
}

# Download location
$downloadPath = Join-Path $env:TEMP $installerName

try {
    Write-Host "   URL: $downloadUrl" -ForegroundColor Gray
    Write-Host "   Destination: $downloadPath" -ForegroundColor Gray
    Write-Host ""
    Write-Host "⏳ Downloading... (this may take a few minutes)" -ForegroundColor Yellow
    
    # Download with progress
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $downloadUrl -OutFile $downloadPath -UseBasicParsing
    $ProgressPreference = 'Continue'
    
    Write-Host "✅ Download complete!" -ForegroundColor Green
    Write-Host ""
    
    # Verify file exists
    if (Test-Path $downloadPath) {
        $fileSize = (Get-Item $downloadPath).Length / 1MB
        Write-Host "📦 Installer downloaded: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Green
        Write-Host ""
        
        # Ask to install
        $install = Read-Host "Do you want to run the installer now? (Y/N)"
        
        if ($install -eq "Y" -or $install -eq "y") {
            Write-Host ""
            Write-Host "🚀 Launching installer..." -ForegroundColor Cyan
            Write-Host "   Follow the installation wizard." -ForegroundColor Gray
            Write-Host "   Recommended: Use default installation path." -ForegroundColor Gray
            Write-Host ""
            
            Start-Process -FilePath $downloadPath -Wait
            
            Write-Host ""
            Write-Host "✅ Installation complete!" -ForegroundColor Green
            Write-Host ""
            
            # Detect installation path
            $possiblePaths = @(
                "C:\Program Files\Java\jdk-21",
                "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot",
                "C:\Program Files\Eclipse Foundation\jdk-21.0.5.11-hotspot"
            )
            
            $javaHome = $null
            foreach ($path in $possiblePaths) {
                if (Test-Path $path) {
                    $javaHome = $path
                    break
                }
            }
            
            if ($javaHome) {
                Write-Host "📍 JDK 21 detected at: $javaHome" -ForegroundColor Green
                Write-Host ""
                Write-Host "🔧 Setting JAVA_HOME environment variable..." -ForegroundColor Cyan
                
                # Set for current session
                $env:JAVA_HOME = $javaHome
                $env:PATH = "$javaHome\bin;$env:PATH"
                
                Write-Host "✅ JAVA_HOME set for current session: $javaHome" -ForegroundColor Green
                Write-Host ""
                Write-Host "⚠️  To make this permanent, run:" -ForegroundColor Yellow
                Write-Host "   [System.Environment]::SetEnvironmentVariable('JAVA_HOME', '$javaHome', 'User')" -ForegroundColor White
                Write-Host ""
                
                # Verify Java version
                Write-Host "🔍 Verifying installation..." -ForegroundColor Cyan
                & "$javaHome\bin\java.exe" -version
                Write-Host ""
                
                Write-Host "✅ JDK 21 is ready to use!" -ForegroundColor Green
                Write-Host ""
                Write-Host "📋 Next steps:" -ForegroundColor Cyan
                Write-Host "   1. Close and reopen your terminal" -ForegroundColor White
                Write-Host "   2. Run: .\gradlew.bat clean build" -ForegroundColor White
                Write-Host ""
            } else {
                Write-Host "⚠️  Could not auto-detect JDK installation path." -ForegroundColor Yellow
                Write-Host "   Please set JAVA_HOME manually to your JDK 21 installation directory." -ForegroundColor Yellow
                Write-Host ""
            }
            
        } else {
            Write-Host ""
            Write-Host "📦 Installer saved at: $downloadPath" -ForegroundColor Cyan
            Write-Host "   Run it manually when ready." -ForegroundColor Gray
            Write-Host ""
        }
        
    } else {
        Write-Host "❌ Download failed. File not found." -ForegroundColor Red
        exit 1
    }
    
} catch {
    Write-Host ""
    Write-Host "❌ Download failed: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "📖 Please download manually from:" -ForegroundColor Yellow
    Write-Host "   https://www.oracle.com/java/technologies/downloads/#java21-windows" -ForegroundColor White
    Write-Host ""
    exit 1
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Script complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
