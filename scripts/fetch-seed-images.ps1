# ============================================================================
# 演示素材下载：按关键词从 Wikimedia Commons 检索真实照片（免费授权）。
#
# 用法（在仓库根目录执行）：
#   powershell -ExecutionPolicy Bypass -File scripts/fetch-seed-images.ps1
#
# 说明：
#   - 下载结果提交入仓（frontend/public/images/），本脚本仅在素材缺失或
#     需要换图时运行；API：commons.wikimedia.org 搜索 + 640px 缩略图
#   - 校验：HTTP 200 + JPEG/PNG 魔数 + 体积 >= 10KB；失败项在结尾列出
#   - 对某张图不满意时：改 $items 里对应关键词后重跑，或直接在
#     管理端商品管理上传替换（线上能力，不依赖本脚本）
# ============================================================================

$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot | Split-Path | Split-Path   # scripts/ -> 仓库根
$destProducts = Join-Path $root 'frontend/public/images/products'
$destPickup   = Join-Path $root 'frontend/public/images/pickup'
New-Item -ItemType Directory -Force -Path $destProducts, $destPickup | Out-Null

$items = @(
    @{ file = 'p01.jpg'; term = 'strawberries fruit closeup' },
    @{ file = 'p02.jpg'; term = 'fuji apples' },
    @{ file = 'p03.jpg'; term = 'fresh spinach leaves' },
    @{ file = 'p04.jpg'; term = 'mango fruit' },
    @{ file = 'p05.jpg'; term = 'navel oranges' },
    @{ file = 'p06.jpg'; term = 'kiwifruit slices' },
    @{ file = 'p07.jpg'; term = 'cherry tomatoes' },
    @{ file = 'p08.jpg'; term = 'carrot bunch' },
    @{ file = 'p09.jpg'; term = 'garlic bulbs' },
    @{ file = 'p10.jpg'; term = 'green onions scallions' },
    @{ file = 'p11.jpg'; term = 'brown eggs' },
    @{ file = 'p12.jpg'; term = 'raw whole chicken' },
    @{ file = 'p13.jpg'; term = 'pork belly slices' },
    @{ file = 'p14.jpg'; term = 'raw shrimps' },
    @{ file = 'p15.jpg'; term = 'fresh fish fillet' },
    @{ file = 'p16.jpg'; term = 'milk bottles' },
    @{ file = 'p17.jpg'; term = 'red apple fruit' },
    @{ file = 'pickup/pickup1.jpg'; term = 'convenience store front' },
    @{ file = 'pickup/pickup2.jpg'; term = 'package pickup locker' },
    @{ file = 'pickup/pickup3.jpg'; term = 'supermarket fresh produce' }
)

$failed = @()
foreach ($it in $items) {
    $outPath = Join-Path $root ("frontend/public/images/" + $it.file)
    if (Test-Path $outPath) {
        Write-Host ("SKIP (exists) " + $it.file)
        continue
    }

    $api = 'https://commons.wikimedia.org/w/api.php?action=query&format=json' +
           '&generator=search&gsrnamespace=6&gsrlimit=5' +
           '&gsrsearch=' + [uri]::EscapeDataString($it.term + ' filetype:bitmap') +
           '&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=640'
    try {
        $resp = Invoke-RestMethod -Uri $api -TimeoutSec 20 -Headers @{ 'User-Agent' = 'FreshCommunity-seed/1.0 (thesis demo)' }
        $pages = $resp.query.pages.PSObject.Properties.Value |
            Sort-Object { $_.index }
        $downloaded = $false
        foreach ($p in $pages) {
            $info = $p.imageinfo[0]
            if ($null -eq $info) { continue }
            if ($info.mime -notin @('image/jpeg', 'image/png')) { continue }
            if ($info.width -lt 500) { continue }
            $thumb = if ($info.thumburl) { $info.thumburl } else { $info.url }
            try {
                Invoke-WebRequest -Uri $thumb -OutFile $outPath -TimeoutSec 30 `
                    -Headers @{ 'User-Agent' = 'FreshCommunity-seed/1.0 (thesis demo)' }
                $bytes = [System.IO.File]::ReadAllBytes($outPath)
                $isJpeg = $bytes.Length -gt 10240 -and $bytes[0] -eq 0xFF -and $bytes[1] -eq 0xD8
                $isPng  = $bytes.Length -gt 10240 -and $bytes[0] -eq 0x89 -and $bytes[1] -eq 0x50
                if ($isJpeg -or $isPng) {
                    Write-Host ("OK   " + $it.file + "  <- " + $p.title)
                    $downloaded = $true
                    break
                }
                Remove-Item $outPath -Force
            } catch {
                Write-Host ("     retry next candidate for " + $it.file)
            }
        }
        if (-not $downloaded) { $failed += $it.file }
    } catch {
        Write-Host ("API FAIL " + $it.file + ": " + $_.Exception.Message)
        $failed += $it.file
    }
}

if ($failed.Count -gt 0) {
    Write-Host ""
    Write-Host "FAILED items:" $failed -ForegroundColor Red
    exit 1
}
Write-Host ""
Write-Host "All seed images ready."
