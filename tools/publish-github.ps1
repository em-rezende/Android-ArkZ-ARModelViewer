# Publica o ArkZ ARModelViewer no GitHub: repositorio, branch principal e release.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File tools\publish-github.ps1
#   powershell -ExecutionPolicy Bypass -File tools\publish-github.ps1 -Check
#
# O script e idempotente (pode rodar de novo sem estragar nada) e faz:
#   1. inicializa o repositorio Git (branch main), se ainda nao existir;
#   2. cria o commit inicial e a tag do release, se ainda nao existirem;
#   3. cria o repositorio no GitHub, faz o push e publica o release usando
#      docs/release-notes/v1.0.0.md - quando o GitHub CLI (gh) esta instalado;
#   4. sem o gh, mostra o passo a passo manual equivalente.
#
# O push usa HTTPS: na primeira vez o Git Credential Manager abre o navegador
# para autenticar e guarda a credencial para as proximas execucoes.
#
# Requisito: Git instalado. O GitHub CLI e opcional.

param(
    [string]$Repo      = 'em-rezende/Android-ArkZ-ARModelViewer',
    [string]$Tag       = 'v1.0.0',
    [string]$NotesFile = 'docs/release-notes/v1.0.0.md',
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Write-Step([string]$Text) { Write-Host ''; Write-Host "== $Text" -ForegroundColor Cyan }
function Write-Ok([string]$Text)   { Write-Host "   [ok] $Text" -ForegroundColor Green }
function Write-Warn([string]$Text) { Write-Host "   [!]  $Text" -ForegroundColor Yellow }

$checkMode = $Check.IsPresent
$remoteUrl = "https://github.com/$Repo.git"

# Branch atual (antes do primeiro commit, HEAD ainda nao aponta para nada).
$branch = (git symbolic-ref --short HEAD 2>$null)
if (-not $branch) { $branch = 'main' }


# ---------------------------------------------------------------------------
# 1) Git: repositorio, branch principal, commit e tag
# ---------------------------------------------------------------------------
Write-Step 'Repositorio Git'

if (Test-Path (Join-Path $root '.git')) {
    Write-Ok 'repositorio Git ja inicializado'
} elseif ($checkMode) {
    Write-Warn 'sem repositorio Git (rode sem -Check para criar)'
} else {
    git init -b main | Out-Null
    Write-Ok 'git init -b main'
}

if (-not $checkMode) {
    Write-Ok "branch: $branch"

    $pending = git status --porcelain
    if ($pending) {
        git add -A
        git commit -m "release: ArkZ ARModelViewer $Tag" | Out-Null
        Write-Ok 'commit criado'
    } else {
        Write-Ok 'nenhuma alteracao pendente'
    }

    if (git tag --list $Tag) {
        Write-Ok "tag $Tag ja existe"
    } else {
        git tag -a $Tag -m "ArkZ ARModelViewer $Tag"
        Write-Ok "tag $Tag criada"
    }
}

# ---------------------------------------------------------------------------
# 2) GitHub: repositorio, push e release
# ---------------------------------------------------------------------------
Write-Step "Publicacao em https://github.com/$Repo"

$gh = Get-Command gh -ErrorAction SilentlyContinue
$ghReady = $false

if ($gh) {
    $status = (gh auth status 2>&1 | Out-String)
    $ghReady = ($status -match 'Logged in')
    if ($ghReady) {
        Write-Ok 'GitHub CLI instalado e autenticado'
    } else {
        Write-Warn 'GitHub CLI instalado, mas sem autenticacao (rode: gh auth login)'
    }
} else {
    Write-Warn 'GitHub CLI (gh) nao encontrado - sera mostrado o passo a passo manual'
}

if ($ghReady) {
    $exists = (gh repo view $Repo 2>&1 | Out-String)

    if ($LASTEXITCODE -ne 0) {
        if ($checkMode) {
            Write-Warn "o repositorio $Repo ainda nao existe no GitHub"
        } else {
            Write-Host '   Criando o repositorio e enviando o codigo...'
            gh repo create $Repo --public --source . --remote origin --push `
                --description 'Visualizador de modelos 3D em Realidade Aumentada (ARCore + SceneView/Filament) para Android.'
            Write-Ok "repositorio criado e codigo enviado"
            gh repo edit $Repo --add-topic augmented-reality,arcore,kotlin,android,sceneview,filament,jetpack-compose,3d | Out-Null
            Write-Ok 'topics configurados'
        }
    } else {
        Write-Ok 'repositorio ja existe no GitHub'
        if ($checkMode) {
            Write-Host '   (modo -Check: nada foi enviado)'
        } else {
            if ((git remote) -notcontains 'origin') { git remote add origin $remoteUrl }
            git push -u origin $branch --tags
            Write-Ok 'branch e tags enviadas'
        }
    }

    if (-not $checkMode) {
        gh release view $Tag --repo $Repo 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "release $Tag ja existe"
        } else {
            gh release create $Tag --repo $Repo `
                --title "ArkZ ARModelViewer $Tag" `
                --notes-file $NotesFile
            Write-Ok "release $Tag publicado"
        }
    }

    Write-Host ''
    Write-Host "   Repositorio: https://github.com/$Repo" -ForegroundColor Green
    Write-Host "   Releases:    https://github.com/$Repo/releases" -ForegroundColor Green
    exit 0
}

# ---------------------------------------------------------------------------
# 3) Sem o GitHub CLI: passo a passo manual
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host 'Passo a passo manual (tudo isso e feito pelo gh quando ele esta instalado):' -ForegroundColor White
Write-Host ''
Write-Host '  1. Crie um repositorio VAZIO (sem README, sem .gitignore, sem licenca):' -ForegroundColor Gray
Write-Host "     https://github.com/new?name=Android-ArkZ-ARModelViewer" -ForegroundColor White
Write-Host '     Visibilidade: Public' -ForegroundColor Gray
Write-Host ''
Write-Host '  2. Conecte o repositorio local e envie tudo (na pasta do projeto):' -ForegroundColor Gray
Write-Host "     git remote add origin $remoteUrl" -ForegroundColor White
Write-Host "     git push -u origin $branch --tags" -ForegroundColor White
Write-Host ''
Write-Host '  3. Publique o release colando o conteudo de:' -ForegroundColor Gray
Write-Host "     $NotesFile" -ForegroundColor White
Write-Host "     https://github.com/$Repo/releases/new?tag=$Tag" -ForegroundColor White
Write-Host ''
Write-Host '  4. Opcional - instale o GitHub CLI e automatize as proximas publicacoes:' -ForegroundColor Gray
Write-Host '     winget install --id GitHub.cli     gh auth login' -ForegroundColor White
Write-Host ''
exit 0

