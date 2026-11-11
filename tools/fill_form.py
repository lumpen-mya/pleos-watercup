#!/usr/bin/env python3
"""기획서 초안(md)을 공식 접수양식 DOCX의 02 섹션 칸(1~7번)에 채운다.

사용: python3 tools/fill_form.py
입력: docs/접수양식-원본.docx, docs/기획서-초안.md
출력: docs/접수양식-작성본.docx

- 양식의 입력 칸은 문서 순서대로 1~8번이며, md의 `## N.` 섹션을 같은 번호 칸에 넣는다.
- 8번(숏폼 링크)과 01·03·04 섹션(개인정보·동의·서명)은 건드리지 않는다.
- md의 `[확인: ...]` 표시는 작성본에 넣지 않는다.
"""
import re
import shutil
import sys
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "docs" / "접수양식-원본.docx"
MD = ROOT / "docs" / "기획서-초안.md"
OUT = ROOT / "docs" / "접수양식-작성본.docx"

P_PR = '<w:pPr><w:spacing w:after="40" w:line="300" w:lineRule="atLeast"/></w:pPr>'


def run(text, bold=False):
    b = "<w:b/><w:bCs/>" if bold else ""
    return (
        f'<w:r><w:rPr>{b}<w:lang w:eastAsia="ko-KR"/></w:rPr>'
        f'<w:t xml:space="preserve">{escape(text)}</w:t></w:r>'
    )


def paragraph(line):
    """`**굵게**` 표시를 run 단위로 나눠 문단 XML을 만든다."""
    parts = re.split(r"(\*\*[^*]+\*\*)", line)
    runs = "".join(
        run(p[2:-2], True) if p.startswith("**") else run(p) for p in parts if p
    )
    return f"<w:p>{P_PR}{runs}</w:p>"


def clean(line):
    line = re.sub(r"\s*`\[확인[^\]]*\]`", "", line)
    return line.replace("`", "").rstrip()


def md_sections(text):
    """`## N. 제목` 단위로 본문 줄 목록을 돌려준다. 표는 한 줄 요약으로 바꾼다."""
    sections = {}
    current = None
    for raw in text.splitlines():
        m = re.match(r"^## (\d+)\. ", raw)
        if m:
            current = int(m.group(1))
            sections[current] = []
            continue
        if current is None or raw.strip() == "---":
            continue
        line = clean(raw)
        if not line.strip():
            continue
        if line.lstrip().startswith("|"):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if all(re.fullmatch(r":?-+:?", c) for c in cells):
                continue  # 구분선
            if cells[0] in ("SDK", "단계"):
                continue  # 머리글
            head = " · ".join(c for c in cells[:-1] if c)
            line = f"- {head} — {cells[-1]}"
        sections[current].append(line)
    return sections


def fill(xml, sections, app_name):
    # 02 섹션의 "앱명 / 프로젝트명: ____" 줄 (문서 내 두 번째 등장)
    idxs = [m.start() for m in re.finditer("프로젝트명", xml)]
    if len(idxs) >= 2:
        start = idxs[1]
        end = xml.find("</w:p>", start)
        seg = xml[start:end]
        seg = re.sub(r"프로젝트명: _+", f"프로젝트명: {escape(app_name)}", seg, count=1)
        seg = re.sub(r"<w:t>_+</w:t>", "<w:t></w:t>", seg)
        xml = xml[:start] + seg + xml[end:]

    # 입력 칸(sdt)을 뒤에서부터 바꿔 앞쪽 위치가 밀리지 않게 한다
    spots = [m.start() for m in re.finditer(r"<w:sdt>", xml)]
    for number in sorted(sections, reverse=True):
        if number > len(spots) or not sections[number]:
            continue
        sdt = spots[number - 1]
        p_start = xml.rfind("<w:p ", 0, sdt)
        p_end = xml.find("</w:p>", xml.find("</w:sdt>", sdt)) + len("</w:p>")
        body = "".join(paragraph(line) for line in sections[number])
        xml = xml[:p_start] + body + xml[p_end:]
        # 긴 칸이 페이지를 넘길 수 있게 해당 행의 '행 나눔 금지'를 푼다
        tr_start = xml.rfind("<w:trPr>", 0, p_start)
        tr_end = xml.find("</w:trPr>", tr_start)
        trpr = xml[tr_start:tr_end].replace("<w:cantSplit/>", "")
        xml = xml[:tr_start] + trpr + xml[tr_end:]
    return xml


def main():
    md = MD.read_text(encoding="utf-8")
    name = re.search(r"앱명 / 프로젝트명: \*\*(.+?)\*\*", md)
    sections = {n: v for n, v in md_sections(md).items() if n <= 7}
    shutil.copyfile(SRC, OUT)
    with zipfile.ZipFile(SRC) as zin, zipfile.ZipFile(
        OUT, "w", zipfile.ZIP_DEFLATED
    ) as zout:
        for item in zin.infolist():
            if item.is_dir():
                continue
            data = zin.read(item.filename)
            if item.filename == "word/document.xml":
                xml = fill(data.decode("utf-8"), sections, name.group(1) if name else "")
                data = xml.encode("utf-8")
            zout.writestr(item, data)
    print(f"작성본 저장: {OUT.relative_to(ROOT)} (채운 칸: {sorted(sections)})")


if __name__ == "__main__":
    sys.exit(main())
