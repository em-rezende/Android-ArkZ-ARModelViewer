# Gera a imagem de "Social preview" do repositorio (1280x640 PNG).
#
# Uso:  powershell -ExecutionPolicy Bypass -File tools\generate-social-preview.ps1
# Saida: docs\social-preview.png
#
# O GitHub NAO permite definir essa imagem pela API: depois de gerar, envie o PNG
# em Settings -> Social preview -> Upload an image.
#
# Requer apenas .NET (System.Drawing). O texto e escrito sem acentos de proposito:
# o PowerShell 5.1 le arquivos .ps1 sem BOM como ANSI.

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$root   = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'docs'
$outFile = Join-Path $outDir 'social-preview.png'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$width  = 1280
$height = 640

# Paleta do app (mesma do icone do launcher, em generate-assets.ps1).
$bgColor     = [System.Drawing.Color]::FromArgb(255, 18, 22, 34)
$accent      = [System.Drawing.Color]::FromArgb(255, 0, 229, 255)
$textColor   = [System.Drawing.Color]::FromArgb(255, 236, 240, 248)
$mutedColor  = [System.Drawing.Color]::FromArgb(255, 150, 160, 178)

$bmp = New-Object System.Drawing.Bitmap($width, $height)
$g   = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

# Fundo
$bg = New-Object System.Drawing.SolidBrush $bgColor
$g.FillRectangle($bg, 0, 0, $width, $height)

# Brilho diagonal no canto superior direito (da profundidade a imagem)
$glow = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point(0, 0)),
    (New-Object System.Drawing.Point($width, $height)),
    ([System.Drawing.Color]::FromArgb(60, 0, 229, 255)),
    ([System.Drawing.Color]::FromArgb(0, 0, 229, 255)))
$g.FillRectangle($glow, 0, 0, $width, $height)

# Moldura de marcador (mesma ideia do icone: quadrado + cantos em destaque)
$frameSize = 190
$frameX = 96
$frameY = ($height - $frameSize) / 2
$penFrame = New-Object System.Drawing.Pen(([System.Drawing.Color]::FromArgb(70, 236, 240, 248)), 3)
$g.DrawRectangle($penFrame, $frameX, $frameY, $frameSize, $frameSize)
$penCorner = New-Object System.Drawing.Pen($accent, 10)
$arm = [int]($frameSize * 0.28)
# cantos: superior esquerdo, superior direito, inferior esquerdo, inferior direito
$g.DrawLine($penCorner, $frameX, $frameY, ($frameX + $arm), $frameY)
$g.DrawLine($penCorner, $frameX, $frameY, $frameX, ($frameY + $arm))
$g.DrawLine($penCorner, ($frameX + $frameSize), $frameY, ($frameX + $frameSize - $arm), $frameY)
$g.DrawLine($penCorner, ($frameX + $frameSize), $frameY, ($frameX + $frameSize), ($frameY + $arm))
$g.DrawLine($penCorner, $frameX, ($frameY + $frameSize), ($frameX + $arm), ($frameY + $frameSize))
$g.DrawLine($penCorner, $frameX, ($frameY + $frameSize), $frameX, ($frameY + $frameSize - $arm))
$g.DrawLine($penCorner, ($frameX + $frameSize), ($frameY + $frameSize), ($frameX + $frameSize - $arm), ($frameY + $frameSize))
$g.DrawLine($penCorner, ($frameX + $frameSize), ($frameY + $frameSize), ($frameX + $frameSize), ($frameY + $frameSize - $arm))

# Cubo simples dentro da moldura (o "modelo 3D" ancorado)
$cubeColor = New-Object System.Drawing.SolidBrush(([System.Drawing.Color]::FromArgb(210, 0, 229, 255)))
$cubeSide = [int]($frameSize * 0.34)
$cubeX = $frameX + [int](($frameSize - $cubeSide) / 2)
$cubeY = $frameY + [int](($frameSize - $cubeSide) / 2)
$g.FillRectangle($cubeColor, $cubeX, $cubeY, $cubeSide, $cubeSide)
$penCube = New-Object System.Drawing.Pen($bgColor, 4)
$g.DrawRectangle($penCube, $cubeX, $cubeY, $cubeSide, $cubeSide)

# Textos
$textLeft = $frameX + $frameSize + 64
$textMaxWidth = $width - $textLeft - 64
$brushText   = New-Object System.Drawing.SolidBrush $textColor
$brushAccent = New-Object System.Drawing.SolidBrush $accent
$brushMuted  = New-Object System.Drawing.SolidBrush $mutedColor

# Desenha o texto reduzindo a fonte (de 1 em 1 pt) ate caber na largura util.
function Write-FittedText {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [single]$Size,
        [System.Drawing.FontStyle]$Style,
        [System.Drawing.Brush]$Brush,
        [single]$X,
        [single]$Y,
        [single]$MaxWidth
    )

    $font = New-Object System.Drawing.Font('Segoe UI', $Size, $Style)
    while ($Size -gt 12 -and ($Graphics.MeasureString($Text, $font)).Width -gt $MaxWidth) {
        $Size = $Size - 1
        $font.Dispose()
        $font = New-Object System.Drawing.Font('Segoe UI', $Size, $Style)
    }
    $Graphics.DrawString($Text, $font, $Brush, $X, $Y)
    $font.Dispose()
}

Write-FittedText -Graphics $g -Text 'ArkZ ARModelViewer' -Size 56 -Style ([System.Drawing.FontStyle]::Bold) -Brush $brushText -X $textLeft -Y 160 -MaxWidth $textMaxWidth
Write-FittedText -Graphics $g -Text 'Modelos 3D em Realidade Aumentada' -Size 30 -Style ([System.Drawing.FontStyle]::Regular) -Brush $brushAccent -X ($textLeft + 3) -Y 250 -MaxWidth $textMaxWidth
Write-FittedText -Graphics $g -Text 'ancorados em marcadores de imagem (QR code)' -Size 23 -Style ([System.Drawing.FontStyle]::Regular) -Brush $brushMuted -X ($textLeft + 3) -Y 296 -MaxWidth $textMaxWidth
Write-FittedText -Graphics $g -Text 'Android, Kotlin, Jetpack Compose' -Size 22 -Style ([System.Drawing.FontStyle]::Regular) -Brush $brushMuted -X ($textLeft + 3) -Y 348 -MaxWidth $textMaxWidth
Write-FittedText -Graphics $g -Text 'ARCore (Augmented Images) + SceneView/Filament' -Size 22 -Style ([System.Drawing.FontStyle]::Regular) -Brush $brushMuted -X ($textLeft + 3) -Y 382 -MaxWidth $textMaxWidth

# Rodape: repositorio e licenca (mesma largura util, comecando na margem esquerda)
$footerWidth = $width - 192
Write-FittedText -Graphics $g -Text 'github.com/em-rezende/Android-ArkZ-ARModelViewer' -Size 20 -Style ([System.Drawing.FontStyle]::Regular) -Brush $brushAccent -X 96 -Y ($height - 84) -MaxWidth $footerWidth
Write-FittedText -Graphics $g -Text 'GPL-3.0 - Ark-Z Arquitetura Ltda - desenvolvedor: Ezequiel M. Rezende' -Size 20 -Style ([System.Drawing.FontStyle]::Regular) -Brush $brushMuted -X 96 -Y ($height - 52) -MaxWidth $footerWidth

$bmp.Save($outFile, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()

Write-Host "Imagem gerada: $outFile ($width x $height)"
Write-Host "Envie em GitHub -> Settings -> Social preview -> Upload an image"
