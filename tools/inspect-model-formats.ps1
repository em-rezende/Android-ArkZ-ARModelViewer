# Simula o sniffing de formato do SceneView 4.38.0 (ModelLoader/ObjLoader/StlLoader)
# sobre os arquivos de uma pasta, mostrando o que a biblioteca REALMENTE detecta.
#
# Uso:  powershell -ExecutionPolicy Bypass -File tools\inspect-model-formats.ps1 "C:\caminho\dos\modelos"
#
# Por que isso importa: o SceneView identifica o formato pelo conteudo, nao pela
# extensao. Um .obj cuja primeira face esta depois dos 4 KB iniciais e tratado
# como glTF, falha ao abrir e o app mostra "nao foi possivel ler o modelo" — o
# O ArkZ ARModelViewer contorna isso convertendo o arquivo para .glb na importacao.
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Folder
)

function Test-LeadByte([int]$b) {
    if ($b -ge 9 -and $b -le 13) { return $true }
    if ($b -eq 32 -or $b -eq 35 -or $b -eq 0xef) { return $true }
    return ('vfogmuslp'.ToCharArray() -contains [char]$b)
}

function Test-ObjPrefix([byte[]]$bytes) {
    $limit = [Math]::Min($bytes.Length, 4096)
    $at = 0
    $vertex = $false
    $line = 0
    while ($at -lt $limit) {
        $start = $at
        while ($at -lt $limit -and $bytes[$at] -ne 10 -and $bytes[$at] -ne 13) { $at++ }
        $line++
        if ($at -eq $limit -and $limit -lt $bytes.Length) {
            return "nao e OBJ para a biblioteca (prefixo de 4 KB terminou no meio da linha $line)"
        }
        $text = [System.Text.Encoding]::UTF8.GetString($bytes, $start, $at - $start).TrimEnd()
        $tok = ($text -replace '\s+', ' ').Trim()
        if ($tok.Length -gt 0 -and $tok[0] -ne '#') {
            $parts = $tok -split ' '
            $cmd = $parts[0]
            if ($cmd -eq 'v') { $vertex = $true }
            elseif ($cmd -eq 'f') {
                if ($vertex -and $parts.Count -ge 4) { return "OBJ (face na linha $line)" }
                return "OBJ invalido na linha $line"
            }
            elseif (@('vn', 'vt', 'o', 'g', 'mtllib', 'usemtl', 's', 'l', 'p') -notcontains $cmd) {
                return "nao e OBJ (comando '$cmd' na linha $line)"
            }
        }
        $at++
    }
    return 'nao e OBJ (nenhuma face no prefixo)'
}

function Test-StlPayload([byte[]]$bytes) {
    $size = $bytes.Length
    if ($size -ge 84 -and (($size - 84) % 50) -eq 0) {
        $count = 0L
        for ($i = 0; $i -lt 4; $i++) { $count = $count -bor ([long]$bytes[80 + $i] -shl ($i * 8)) }
        if ($count -eq [long](($size - 84) / 50)) { return "STL binario ($count triangulos)" }
    }
    $at = 0
    while ($at -lt $size -and ($bytes[$at] -eq 32 -or ($bytes[$at] -ge 9 -and $bytes[$at] -le 13))) { $at++ }
    if ([System.Text.Encoding]::ASCII.GetString($bytes, $at, [Math]::Min(5, $size - $at)) -eq 'solid') { return 'STL ascii (suspeito)' }
    return 'nao e STL'
}

function Get-DetectedFormat([byte[]]$bytes) {
    $magic = if ($bytes.Length -ge 4) { [System.Text.Encoding]::ASCII.GetString($bytes, 0, 4) } else { '' }
    if ($bytes.Length -ge 4 -and $bytes[0] -eq 0x50 -and $bytes[1] -eq 0x4B) { return 'ZIP/3MF (testa o 3mf)' }
    if ($magic -eq 'glTF') { return 'GLB (glTF binario, lido direto pelo Filament)' }
    $stl = Test-StlPayload $bytes
    if ($stl -like 'STL binario*') { return $stl }
    $ply = [System.Text.Encoding]::ASCII.GetString($bytes, 0, [Math]::Min(4, $bytes.Length))
    if ($ply -eq 'ply' -or $ply -eq "ply`n") { return 'PLY' }
    if (Test-LeadByte $bytes[0]) {
        if ($magic -eq 'glTF') { return 'GLB' }
        $obj = Test-ObjPrefix $bytes
        if ($obj -like 'OBJ*') { return $obj }
        if ($stl -like 'STL ascii*') { return $stl }
        return "glTF JSON ou desconhecido -> $obj"
    }
    return 'desconhecido (vai para o glTF e falha)'
}

Get-ChildItem -Path $Folder -File | Where-Object { $_.Extension -match '^\.(glb|gltf|obj|stl|ply|3mf)$' } | ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    '{0,-42} {1,10} bytes  ->  {2}' -f $_.Name, $bytes.Length, (Get-DetectedFormat $bytes)
}
