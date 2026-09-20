# Gera os assets binarios do projeto ArkZ ARModelViewer:
#   1. Icones do launcher (res/mipmap-*)
#   2. Marcadores de imagem alternativos (tools/generated-markers/*.png)
#
# Uso:  powershell -ExecutionPolicy Bypass -File tools\generate-assets.ps1
# Requer apenas .NET (System.Drawing) - nenhuma dependencia externa.
#
# Observacao: os marcadores que o app ja traz em
# app/src/main/assets/augmented_images/ sao QR codes reais. Este script NAO os
# sobrescreve: ele apenas produz marcadores alternativos (pseudo-QR) caso voce
# queira trocar as imagens de referencia.

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$root   = Split-Path -Parent $PSScriptRoot
$resDir = Join-Path $root 'app\src\main\res'
$altDir = Join-Path $PSScriptRoot 'generated-markers'

New-Item -ItemType Directory -Force -Path $altDir | Out-Null

# ---------------------------------------------------------------------------
# 1) Icones do launcher (quadrado e redondo) em todas as densidades
# ---------------------------------------------------------------------------
function New-LauncherBitmap {
    param([int]$Size, [bool]$Rounded)

    $bmp = New-Object System.Drawing.Bitmap($Size, $Size)
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

    $bg = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 18, 22, 34))
    if ($Rounded) { $g.FillEllipse($bg, 0, 0, $Size, $Size) } else { $g.FillRectangle($bg, 0, 0, $Size, $Size) }

    # Moldura de "marcador" + letras AR, representando o alvo de RA.
    $accent = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 0, 229, 255))
    $inset  = [int]($Size * 0.18)
    $side   = $Size - (2 * $inset)
    $pen    = New-Object System.Drawing.Pen($accent, [single]($Size * 0.07))
    $g.DrawRectangle($pen, $inset, $inset, $side, $side)

    $font = New-Object System.Drawing.Font('Segoe UI', [single]($Size * 0.3), [System.Drawing.FontStyle]::Bold)
    $fmt  = New-Object System.Drawing.StringFormat
    $fmt.Alignment     = [System.Drawing.StringAlignment]::Center
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
    $rect = New-Object System.Drawing.RectangleF(0, 0, $Size, $Size)
    $g.DrawString('AR', $font, $accent, $rect, $fmt)

    $g.Dispose(); $bg.Dispose(); $accent.Dispose(); $pen.Dispose(); $font.Dispose(); $fmt.Dispose()
    return $bmp
}

$densities = @{ 'mdpi' = 48; 'hdpi' = 72; 'xhdpi' = 96; 'xxhdpi' = 144; 'xxxhdpi' = 192 }
foreach ($d in $densities.Keys) {
    $dir = Join-Path $resDir "mipmap-$d"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    $square = New-LauncherBitmap -Size $densities[$d] -Rounded $false
    $square.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $square.Dispose()

    $round = New-LauncherBitmap -Size $densities[$d] -Rounded $true
    $round.Save((Join-Path $dir 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $round.Dispose()
}

Write-Host "Icones gerados em: $resDir"

# ---------------------------------------------------------------------------
# 2) Marcadores alternativos (pseudo-QR deterministicos)
#
# O AugmentedImageDatabase do ARCore funciona melhor com imagens de ALTO
# CONTRASTE, muitos detalhes e NAO repetitivas - exatamente o perfil de um QR
# code (modulos nitidos). Aqui geramos um pseudo-QR reproduzivel: matriz de
# modulos claros/escuros sorteada com seed fixa + os 3 finder patterns.
# ---------------------------------------------------------------------------
function New-QrLikeBitmap {
    param([int]$Size, [int]$Modules, [int]$Seed)

    $bmp = New-Object System.Drawing.Bitmap($Size, $Size)
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::White)
    $g.SmoothingMode   = [System.Drawing.Drawing2D.SmoothingMode]::None
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

    $mod    = [int]($Size / $Modules)
    $black  = [System.Drawing.Brushes]::Black
    $origins = @(@(0, 0), @(($Modules - 7), 0), @(0, ($Modules - 7)))

    # Modulos pseudo-aleatorios (fora das areas reservadas aos finder patterns).
    $reserved = New-Object 'bool[,]' $Modules, $Modules
    foreach ($o in $origins) {
        for ($y = -1; $y -le 7; $y++) {
            for ($x = -1; $x -le 7; $x++) {
                $px = $o[0] + $x; $py = $o[1] + $y
                if ($px -ge 0 -and $px -lt $Modules -and $py -ge 0 -and $py -lt $Modules) { $reserved[$px, $py] = $true }
            }
        }
    }
    $rand = New-Object System.Random($Seed)
    for ($y = 0; $y -lt $Modules; $y++) {
        for ($x = 0; $x -lt $Modules; $x++) {
            if ($reserved[$x, $y]) { continue }
            if ($rand.NextDouble() -gt 0.5) { $g.FillRectangle($black, $x * $mod, $y * $mod, $mod, $mod) }
        }
    }

    # Finder patterns: quadrado preto, anel branco e nucleo preto.
    foreach ($o in $origins) {
        $ox = $o[0] * $mod; $oy = $o[1] * $mod
        $g.FillRectangle($black, $ox, $oy, 7 * $mod, 7 * $mod)
        $g.FillRectangle([System.Drawing.Brushes]::White, $ox + $mod, $oy + $mod, 5 * $mod, 5 * $mod)
        $g.FillRectangle($black, $ox + 2 * $mod, $oy + 2 * $mod, 3 * $mod, 3 * $mod)
    }
    $g.Dispose()

    # Nenhum texto sobre a imagem: qualquer texto por cima do marcador altera os
    # detalhes visuais que o ARCore usa para reconhecer a figura. Os rotulos
    # ficam nas folhas de impressao geradas por New-MarkerPrintSheet.
    return $bmp
}

# ---------------------------------------------------------------------------
# 3) Folhas de impressao: QR code limpo + legenda ABAIXO (nunca sobre a imagem)
# ---------------------------------------------------------------------------
function New-MarkerPrintSheet {
    param(
        [string]$SourcePath,
        [string]$Label,
        [string]$OutputPath,
        [int]$MarkerPixels = 900
    )

    $source = [System.Drawing.Image]::FromFile($SourcePath)

    # Margem branca em volta (quiet zone, ajuda a localizar o QR) + faixa
    # inferior reservada para a legenda (que NUNCA fica sobre o QR code).
    $margin = [int]($MarkerPixels * 0.08)
    $sheetWidth = $MarkerPixels + (2 * $margin)
    $captionHeight = [int]($MarkerPixels * 0.30)
    $sheetHeight = $MarkerPixels + (2 * $margin) + $captionHeight

    $sheet = New-Object System.Drawing.Bitmap($sheetWidth, $sheetHeight)
    $g = [System.Drawing.Graphics]::FromImage($sheet)
    $g.Clear([System.Drawing.Color]::White)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.DrawImage($source, $margin, $margin, $MarkerPixels, $MarkerPixels)

    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::Center

    $labelFont = New-Object System.Drawing.Font('Segoe UI', [single]($MarkerPixels * 0.07), [System.Drawing.FontStyle]::Bold)
    $labelRect = New-Object System.Drawing.RectangleF(0, ($margin + $MarkerPixels + 12), $sheetWidth, ($captionHeight * 0.42))
    $g.DrawString($Label, $labelFont, [System.Drawing.Brushes]::Black, $labelRect, $fmt)

    $noteFont = New-Object System.Drawing.Font('Segoe UI', [single]($MarkerPixels * 0.034), [System.Drawing.FontStyle]::Regular)
    $noteRect = New-Object System.Drawing.RectangleF(0, ($margin + $MarkerPixels + 12 + ($captionHeight * 0.44)), $sheetWidth, ($captionHeight * 0.54))
    $g.DrawString(
        'Imprima em 100% da escala com 15 cm de largura e aponte a camera do app para esta folha.',
        $noteFont, [System.Drawing.Brushes]::Black, $noteRect, $fmt
    )

    $g.Dispose()
    $sheet.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $sheet.Dispose()
    $labelFont.Dispose(); $noteFont.Dispose(); $fmt.Dispose(); $source.Dispose()
}

$alt = New-QrLikeBitmap -Size 1024 -Modules 33 -Seed 20260920
$alt.Save((Join-Path $altDir 'marker_a.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$alt.Dispose()

$alt = New-QrLikeBitmap -Size 1024 -Modules 29 -Seed 987654321
$alt.Save((Join-Path $altDir 'marker_b.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$alt.Dispose()

Write-Host "Marcadores alternativos (sem texto) gerados em: $altDir"

# ---------------------------------------------------------------------------
# 4) Folhas de impressao dos marcadores que o app realmente rastreia
#    (app/src/main/assets/augmented_images) com a legenda ABAIXO do QR.
# ---------------------------------------------------------------------------
$printDir = Join-Path $PSScriptRoot 'print'
New-Item -ItemType Directory -Force -Path $printDir | Out-Null

$appMarkers = @(
    @{ File = 'marker_a.png'; Label = 'MARCADOR A' },
    @{ File = 'marker_b.png'; Label = 'MARCADOR B' }
)

foreach ($marker in $appMarkers) {
    $assetPath = Join-Path $root ("app\src\main\assets\augmented_images\" + $marker.File)
    if (-not (Test-Path $assetPath)) {
        Write-Host "Aviso: marcador nao encontrado em $assetPath"
        continue
    }
    $outputPath = Join-Path $printDir ($marker.File -replace '\.png$', '_print.png')
    New-MarkerPrintSheet -SourcePath $assetPath -Label $marker.Label -OutputPath $outputPath
}

Write-Host "Folhas de impressao (QR + legenda abaixo) geradas em: $printDir"
