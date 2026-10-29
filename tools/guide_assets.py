"""Package the procedural web guide for an offline, script-free Android screen."""
from pathlib import Path
import re,shutil

def package_guide(root: Path, target: Path):
    shutil.copytree(root/'app/assets',target,dirs_exist_ok=True)
    source=(root/'site/index.html').read_text(encoding='utf-8')
    match=re.search(r'<section id="manual-guide"[^>]*>[\s\S]*?</section>',source)
    if match is None or match.group(0).count('<section')!=1:
        raise RuntimeError('Manual guide section missing or nested; refusing a stale guide build')
    fragment=match.group(0)
    if re.search(r'<(?:script|iframe|form|img)\b|\bon\w+\s*=',fragment,re.I):
        raise RuntimeError('Manual guide must contain procedural content only')
    css=(root/'site/styles.css').read_text(encoding='utf-8')
    if re.search(r'@import|url\s*\(',css,re.I):raise RuntimeError('Guide CSS must not load external resources')
    html='''<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'unsafe-inline';form-action 'none';base-uri 'none'"><title>수동 설정 가이드</title><style>'''+css+'''\n.guide{border-top:0;padding-top:20px}.shell{padding:0 18px}</style></head><body><main class="shell">'''+fragment+'</main></body></html>'
    (target/'manual-guide.html').write_text(html,encoding='utf-8')
