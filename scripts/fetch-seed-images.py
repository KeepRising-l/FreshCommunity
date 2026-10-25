# -*- coding: utf-8 -*-
"""演示素材下载：按关键词从 Wikimedia Commons 检索真实照片（免费授权）。

用法（在仓库根目录执行）：
    python scripts/fetch-seed-images.py

说明：
  - 下载结果提交入仓（frontend/public/images/），本脚本仅在素材缺失或需要换图时运行
  - Commons 要求规范的 User-Agent 与请求间隔；脚本带 2.5s 间隔与 429 退避
  - 校验：JPEG/PNG 魔数 + 体积 >= 10KB；失败项在结尾列出
  - 对某张图不满意时：改 ITEMS 里对应关键词后删除该文件重跑，
    或直接在管理端商品管理上传替换（线上能力，不依赖本脚本）
"""
import json
import struct
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / 'frontend' / 'public' / 'images'
UA = {'User-Agent': 'FreshCommunityThesisDemo/1.0 (educational thesis demo; contact: demo@example.com)'}

# (输出文件, 检索词, 备选检索词)
ITEMS = [
    ('products/p01.jpg', 'strawberries fruit', 'strawberry closeup'),
    ('products/p02.jpg', 'fuji apples', 'red apples fruit'),
    ('products/p03.jpg', 'spinach leaves', 'fresh spinach'),
    ('products/p04.jpg', 'mango fruit', 'mangoes'),
    ('products/p05.jpg', 'navel oranges', 'oranges fruit'),
    ('products/p06.jpg', 'kiwifruit slices', 'kiwi fruit cut'),
    ('products/p07.jpg', 'cherry tomatoes', 'tomatoes on vine'),
    ('products/p08.jpg', 'carrots', 'carrot vegetable'),
    ('products/p09.jpg', 'garlic bulbs', 'garlic'),
    ('products/p10.jpg', 'green onions', 'spring onions'),
    ('products/p11.jpg', 'brown eggs', 'chicken eggs'),
    ('products/p12.jpg', 'raw whole chicken', 'raw chicken meat'),
    ('products/p13.jpg', 'pork belly raw', 'sliced pork belly'),
    ('products/p14.jpg', 'raw shrimps', 'shrimp seafood'),
    ('products/p15.jpg', 'hairtail fish', 'fresh fish fillet'),
    ('products/p16.jpg', 'milk bottle', 'glass bottle milk'),
    ('products/p17.jpg', 'red apple fruit', 'apple closeup'),
    ('products/p18.jpg', 'red apples basket', 'apple harvest'),
    ('products/p19.jpg', 'red delicious apples', 'red apple dark'),
    ('products/p20.jpg', 'green grapes bunch', 'green grapes'),
    ('products/p21.jpg', 'purple grapes', 'kyoho grapes'),
    ('products/p22.jpg', 'yellow pears fruit', 'pear fruit'),
    ('products/p23.jpg', 'pomegranate fruit', 'pomegranate seeds'),
    ('products/p24.jpg', 'bananas bunch', 'bananas'),
    ('products/p25.jpg', 'pomelo fruit', 'honey pomelo'),
    ('products/p26.jpg', 'watermelon', 'watermelon slice'),
    ('products/p27.jpg', 'cherries fruit bowl', 'sweet cherries'),
    ('products/p28.jpg', 'red tomatoes vegetable', 'tomatoes harvest'),
    ('products/p29.jpg', 'fresh cucumbers', 'cucumbers vegetable'),
    ('products/p30.jpg', 'raw potatoes', 'potatoes vegetable'),
    ('products/p31.jpg', 'lettuce vegetable', 'butterhead lettuce'),
    ('products/p32.jpg', 'broccoli', 'broccoli head'),
    ('products/p33.jpg', 'shiitake mushrooms', 'fresh mushrooms'),
    ('products/p34.jpg', 'raw chicken wings', 'chicken wings raw'),
    ('products/p35.jpg', 'raw beef chunks', 'beef raw meat'),
    ('products/p36.jpg', 'raw pork ribs', 'pork ribs raw'),
    ('products/p37.jpg', 'raw salmon fillet', 'salmon fillet'),
    ('products/p38.jpg', 'fresh sea bass fish', 'bass fish'),
    ('products/p39.jpg', 'yogurt glass', 'yogurt bowl'),
    ('products/p40.jpg', 'rice grains bowl', 'white rice'),
    ('products/p41.jpg', 'cooking oil bottle', 'peanut oil'),
    ('pickup/pickup1.jpg', 'convenience store front', 'convenience store'),
    ('pickup/pickup2.jpg', 'package pickup locker', 'parcel locker'),
    ('pickup/pickup3.jpg', 'supermarket fresh produce', 'grocery produce section'),
]


def fetch(url, binary=False, attempts=3):
    last = None
    for i in range(attempts):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = resp.read()
                return data if binary else json.loads(data.decode('utf-8'))
        except urllib.error.HTTPError as e:
            last = e
            if e.code == 429:
                wait = 15 * (i + 1)
                print(f'    429 rate limited, wait {wait}s ...')
                time.sleep(wait)
            else:
                raise
    raise last


def valid_image(data: bytes) -> bool:
    if len(data) < 10 * 1024:
        return False
    if data[:2] == b'\xff\xd8':          # JPEG
        return True
    if data[:8] == b'\x89PNG\r\n\x1a\n':  # PNG
        return True
    return False


def search_candidates(term):
    api = ('https://commons.wikimedia.org/w/api.php?action=query&format=json'
           '&generator=search&gsrnamespace=6&gsrlimit=6'
           f'&gsrsearch={urllib.request.quote(term + " filetype:bitmap")}'
           '&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=640')
    resp = fetch(api)
    pages = (resp.get('query') or {}).get('pages') or {}
    # index 字段即搜索相关度序
    for page in sorted(pages.values(), key=lambda p: p.get('index', 99)):
        infos = page.get('imageinfo') or []
        if not infos:
            continue
        info = infos[0]
        if info.get('mime') not in ('image/jpeg', 'image/png'):
            continue
        if (info.get('width') or 0) < 500:
            continue
        thumb = info.get('thumburl') or info.get('url')
        if thumb:
            yield page.get('title', ''), thumb


def main():
    failed = []
    for rel, term, fallback in ITEMS:
        out = DEST / rel
        if out.exists():
            print(f'SKIP (exists) {rel}')
            continue
        out.parent.mkdir(parents=True, exist_ok=True)

        done = False
        for t in (term, fallback):
            try:
                for title, thumb in search_candidates(t):
                    try:
                        data = fetch(thumb, binary=True)
                        if not valid_image(data):
                            continue
                        out.write_bytes(data)
                        print(f'OK   {rel:28s} <- {title}')
                        done = True
                        break
                    except Exception as e:
                        print(f'     candidate failed: {e}')
                if done:
                    break
            except Exception as e:
                print(f'     search failed for "{t}": {e}')
            time.sleep(2.5)

        if not done:
            failed.append(rel)
        time.sleep(2.5)

    print()
    if failed:
        print('FAILED items:', ', '.join(failed))
        sys.exit(1)
    print('All seed images ready.')


if __name__ == '__main__':
    main()
